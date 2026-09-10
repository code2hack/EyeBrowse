#!/usr/bin/env python3
"""Regression tests for the EyeBrowse test fixture server (C2 safety guards).

Run from the repository root:

    python3 -m unittest discover -s tools/tests -p 'test_browser_fixtures*.py'

The tests start isolated loopback listeners on ephemeral ports with temporary state directories, so
they never touch the reserved run ports or real mission state. Actual LAN reachability is a separate
check and is not claimed here.
"""

from __future__ import annotations

import http.client
import importlib.util
import io
import json
import socket
import sys
import tempfile
import threading
import time
import unittest
from contextlib import redirect_stderr
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_TOOLS_DIR))

# The fixture lives in a hyphenated script file (tools/browser-fixtures.py), so load it by path.
_spec = importlib.util.spec_from_file_location("browser_fixtures", _TOOLS_DIR / "browser-fixtures.py")
assert _spec and _spec.loader
fx = importlib.util.module_from_spec(_spec)
sys.modules["browser_fixtures"] = fx
_spec.loader.exec_module(fx)


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def close_servers(*servers) -> None:
    """Closes listeners created by create_servers (unstarted servers need server_close only)."""
    for server in servers:
        server.server_close()


class FixtureServerCase(unittest.TestCase):
    """Base case: one loopback HTTP listener and one HTTPS listener with a temp state dir."""

    idle_timeout = 1.0
    max_connections = 8

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory(prefix="eyebrowse-fixture-test-")
        cls.addClassCleanup(cls._tmp.cleanup)
        cls.state_dir = Path(cls._tmp.name)
        cls.config = fx.FixtureConfig(
            bind="127.0.0.1",
            http_port=free_port(),
            https_port=free_port(),
            state_dir=cls.state_dir,
            idle_timeout=cls.idle_timeout,
            max_connections=cls.max_connections,
        )
        cls.http_server, cls.https_server, cls.observations = fx.create_servers(cls.config)
        cls.addClassCleanup(cls._stop)
        threading.Thread(target=cls.http_server.serve_forever, daemon=True).start()
        threading.Thread(target=cls.https_server.serve_forever, daemon=True).start()
        cls.http_port = cls.http_server.server_address[1]
        cls.https_port = cls.https_server.server_address[1]

    @classmethod
    def _stop(cls) -> None:
        cls.http_server.shutdown()
        cls.https_server.shutdown()
        cls.http_server.server_close()
        cls.https_server.server_close()

    # ------------------------------------------------------------------ helpers

    def request(self, method: str, path: str, *, body: bytes | None = None,
                headers: dict[str, str] | None = None, timeout: float = 5.0):
        connection = http.client.HTTPConnection("127.0.0.1", self.http_port, timeout=timeout)
        try:
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            return response.status, response.read().decode("utf-8", "replace"), dict(response.getheaders())
        finally:
            connection.close()

    def raw_request(self, payload: bytes, *, timeout: float = 5.0) -> bytes:
        with socket.create_connection(("127.0.0.1", self.http_port), timeout=timeout) as sock:
            sock.sendall(payload)
            chunks = []
            sock.settimeout(timeout)
            while True:
                try:
                    data = sock.recv(65536)
                except socket.timeout:
                    break
                if not data:
                    break
                chunks.append(data)
            return b"".join(chunks)

    def state_text(self) -> str:
        state = self.state_dir / "observations.json"
        return state.read_text() if state.exists() else ""


class ConfigurationTests(unittest.TestCase):
    def test_validates_loopback_and_rfc1918_binds(self) -> None:
        for value in ("127.0.0.1", "192.168.0.52", "10.1.2.3", "172.16.5.5"):
            self.assertEqual(value, fx.validate_bind(value))

    def test_rejects_wildcard_public_multicast_linklocal_and_hostnames(self) -> None:
        for value in ("0.0.0.0", "8.8.8.8", "224.0.0.1", "169.254.1.1", "::", "::1",
                      "localhost", "phone.local", "", "192.168.0.52 "):
            with self.assertRaises(fx.ConfigError, msg=value):
                fx.validate_bind(value)

    def test_default_configuration_advertises_loopback(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = fx.FixtureConfig(bind="127.0.0.1", http_port=25341, https_port=25342,
                                      state_dir=Path(tmp))
        self.assertEqual("http://127.0.0.1:25341", config.http_base)
        self.assertEqual("https://127.0.0.1:25342", config.secure_base)

    def test_lan_configuration_advertises_configured_host(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = fx.FixtureConfig(bind="192.168.0.52", http_port=25341, https_port=25342,
                                      state_dir=Path(tmp))
        self.assertEqual("http://192.168.0.52:25341", config.http_base)
        self.assertEqual("https://192.168.0.52:25342", config.secure_base)

    def test_rejects_equal_ports_and_port_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(fx.ConfigError):
                fx.FixtureConfig(bind="127.0.0.1", http_port=25341, https_port=25341,
                                 state_dir=Path(tmp))
            with self.assertRaises(fx.ConfigError):
                fx.FixtureConfig(bind="127.0.0.1", http_port=0, https_port=25342,
                                 state_dir=Path(tmp))

    def test_state_and_key_directories_are_private(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp) / "state"
            config = fx.FixtureConfig(bind="127.0.0.1", http_port=free_port(),
                                      https_port=free_port(), state_dir=state)
            http_server, https_server, _ = fx.create_servers(config)
            try:
                self.assertEqual(0o700, state.stat().st_mode & 0o777)
                self.assertEqual(0o700, (state / "tls").stat().st_mode & 0o777)
                self.assertEqual(0o600, (state / "tls" / "fixture-key.pem").stat().st_mode & 0o777)
            finally:
                close_servers(http_server, https_server)

    def test_ready_file_guard_refuses_second_server(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            (state / "ready").write_text(f"{__import__('os').getpid()} http://x https://y\n")
            with self.assertRaises(fx.ConfigError):
                fx.guard_ready_file(state)


class PageAndObservationTests(FixtureServerCase):
    def test_default_page_flow_and_marker(self) -> None:
        status, body, _ = self.request("GET", "/form.html")
        self.assertEqual(200, status)
        self.assertIn('id="load-marker"', body)
        self.assertIn('path: "/form.html"', body)
        self.assertEqual(1, self.observations.snapshot()["loads"].get("/form.html"))

        status, index, _ = self.request("GET", "/")
        self.assertEqual(200, status)
        self.assertIn(self.config.http_base, index)
        self.assertIn(self.config.secure_base, index)

    def test_advertised_url_ignores_request_host_header(self) -> None:
        status, body, _ = self.request("GET", "/", headers={"Host": "evil.example"})
        self.assertEqual(200, status)
        self.assertIn(self.config.http_base, body)
        self.assertNotIn("evil.example", body)

    def test_unknown_path_is_not_counted_or_served(self) -> None:
        before = dict(self.observations.snapshot()["loads"])
        status, body, _ = self.request("GET", "/not-a-fixture.html?anything=1")
        self.assertEqual(404, status)
        self.assertNotIn("evil", body)
        self.assertEqual(before, self.observations.snapshot()["loads"])

    def test_form_post_records_names_and_synthetic_id_only(self) -> None:
        body = ("test_id=fixture-post-1&message=DUMMYPERSONAL&secret=DUMMYSECRET&notes=x"
                .encode())
        status, page, _ = self.request("POST", "/submit", body=body, headers={
            "Content-Type": "application/x-www-form-urlencoded"})
        self.assertEqual(200, status)
        self.assertIn("Submission recorded", page)
        last = self.observations.snapshot()["requests"][-1]
        self.assertEqual("POST", last["method"])
        self.assertEqual("fixture-post-1", last["testId"])
        self.assertEqual(["message", "notes", "secret", "test_id"], last["fields"])

    def test_dummy_values_never_reach_state_or_logs(self) -> None:
        body = b"test_id=fixture-post-1&message=DUMMYPERSONALTOKEN&secret=DUMMYSECRETTOKEN"
        stream = io.StringIO()
        with redirect_stderr(stream):
            self.request("POST", "/submit", body=body, headers={
                "Content-Type": "application/x-www-form-urlencoded"})
            self.request("GET", "/basic.html?DUMMYQUERYTOKEN=1")
        self.assertNotIn("DUMMYPERSONALTOKEN", self.state_text())
        self.assertNotIn("DUMMYSECRETTOKEN", self.state_text())
        self.assertNotIn("DUMMYQUERYTOKEN", self.state_text())
        self.assertNotIn("DUMMYQUERYTOKEN", stream.getvalue())


class ConfinementTests(FixtureServerCase):
    def test_traversal_encoded_and_nested_paths_are_refused(self) -> None:
        for path in ("/../tools/browser-fixtures.py", "/..%2f..%2fetc/passwd", "/%2e%2e/AGENTS.md",
                     "/sub/dir.html", "/.hidden.html", "/basic.html%00.png", "/tools/tests/x.py"):
            with self.subTest(path=path):
                status, _, _ = self.request("GET", path)
                self.assertEqual(404, status)

    def test_symlink_in_pages_dir_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            pages = Path(tmp) / "pages"
            pages.mkdir()
            outside = Path(tmp) / "outside.html"
            outside.write_text("<html>outside</html>")
            (pages / "basic.html").symlink_to(outside)
            config = fx.FixtureConfig(bind="127.0.0.1", http_port=free_port(),
                                      https_port=free_port(), state_dir=Path(tmp) / "state",
                                      pages_dir=pages, idle_timeout=self.idle_timeout)
            http_server, https_server, _ = fx.create_servers(config)
            threading.Thread(target=http_server.serve_forever, daemon=True).start()
            try:
                connection = http.client.HTTPConnection("127.0.0.1", http_server.server_address[1],
                                                        timeout=5)
                connection.request("GET", "/basic.html")
                response = connection.getresponse()
                self.assertEqual(404, response.status)
                connection.close()
            finally:
                http_server.shutdown()
                close_servers(http_server, https_server)


class ReflectionAndCookieTests(FixtureServerCase):
    def test_submitted_page_escapes_and_does_not_record_unknown_ids(self) -> None:
        body = b"test_id=<script>alert(1)</script>&message=x"
        status, page, _ = self.request("POST", "/submit", body=body, headers={
            "Content-Type": "application/x-www-form-urlencoded"})
        self.assertEqual(200, status)
        self.assertNotIn("<script>alert(1)</script>", page)
        self.assertIn("(unrecognized)", page)
        self.assertIsNone(self.observations.snapshot()["requests"][-1]["testId"])

    def test_cookie_value_control_characters_are_sanitized(self) -> None:
        status, _, headers = self.request(
            "GET", "/api/cookie?mode=persistent&value=bad%0d%0aX-Injected:%201")
        self.assertEqual(200, status)
        cookie = headers.get("Set-Cookie", "")
        self.assertNotIn("\r", cookie)
        self.assertNotIn("\n", cookie)
        self.assertNotIn("X-Injected", cookie)

    def test_cookies_endpoint_exposes_only_fixture_cookies(self) -> None:
        status, body, _ = self.request("GET", "/api/cookies", headers={
            "Cookie": "fixture_persist=abc123; sessionid=DUMMYSESSION; OTHER=x"})
        self.assertEqual(200, status)
        payload = json.loads(body)
        self.assertEqual({"fixture_persist": "abc123"}, payload["fixtureCookies"])
        self.assertNotIn("DUMMYSESSION", body)

    def test_note_allowlist_bounds_state(self) -> None:
        self.assertTrue(self.observations.record_note("dest-file", "engine-no-op"))
        self.assertFalse(self.observations.record_note("../../etc/passwd", "engine-no-op"))
        self.assertFalse(self.observations.record_note("dest-file", "DUMMYVALUE"))
        self.assertFalse(self.observations.record_note("x" * 200, "engine-no-op"))
        self.assertEqual({"dest-file": "engine-no-op"}, self.observations.snapshot()["notes"])

    def test_case_channel_records_bounded_correlated_evidence(self) -> None:
        base = self.config.http_base
        status, body, _ = self.request(
            "GET", "/api/case?case=dest-content&phase=start&marker=L7&location="
                   + base.replace(":", "%3A").replace("/", "%2F") + "%2Fdestinations.html"
                   + "&outcome=dispatched&t=4242")
        self.assertEqual(200, status)
        self.assertTrue(json.loads(body)["recorded"])
        entry = self.observations.snapshot()["cases"][-1]
        self.assertEqual("dest-content", entry["case"])
        self.assertEqual("start", entry["phase"])
        self.assertEqual("L7", entry["marker"])
        self.assertEqual("dispatched", entry["outcome"])
        self.assertEqual(4242, entry["uptimeMs"])
        self.assertIn("destinations.html", entry["location"])
        self.assertIsInstance(entry["loadSeq"], int)

    def test_case_channel_rejects_unknown_values_and_sanitizes(self) -> None:
        for query in ("case=evil&phase=start&marker=L1&location=x&outcome=dispatched",
                      "case=dest-content&phase=middle&marker=L1&location=x&outcome=dispatched",
                      "case=dest-content&phase=start&marker=L1&location=x&outcome=passed"):
            status, body, _ = self.request("GET", "/api/case?" + query)
            self.assertEqual(200, status)
            self.assertFalse(json.loads(body)["recorded"], query)
        status, body, _ = self.request(
            "GET", "/api/case?case=dest-intent&phase=end&marker=NOT-A-MARKER"
                   "&location=http%3A%2F%2Fevil.example%2Fx&outcome=refused&t=abc")
        self.assertEqual(200, status)
        self.assertTrue(json.loads(body)["recorded"])
        entry = self.observations.snapshot()["cases"][-1]
        self.assertEqual("(other)", entry["marker"])
        self.assertEqual("(other)", entry["location"])
        self.assertEqual(-1, entry["uptimeMs"])

    def test_case_channel_is_bounded(self) -> None:
        for index in range(fx.MAX_CASE_REPORTS + 5):
            self.assertTrue(self.observations.record_case(
                "dest-file", "end", "L1", self.config.http_base + "/x", "no-op", str(index)))
        self.assertEqual(fx.MAX_CASE_REPORTS, len(self.observations.snapshot()["cases"]))

    def test_unknown_note_route_reports_not_recorded(self) -> None:
        status, body, _ = self.request("GET", "/api/note?name=evil&value=DUMMYVALUE")
        self.assertEqual(200, status)
        self.assertFalse(json.loads(body)["recorded"])


class RequestBoundTests(FixtureServerCase):
    def test_target_length_bound(self) -> None:
        status, _, _ = self.request("GET", "/basic.html?" + "a" * 3000)
        self.assertEqual(414, status)

    def test_transfer_encoding_is_refused(self) -> None:
        status, _, _ = self.request("POST", "/submit", body=b"a=b", headers={
            "Transfer-Encoding": "chunked"})
        self.assertEqual(501, status)

    def test_missing_or_conflicting_content_length_is_refused(self) -> None:
        raw = self.raw_request(b"POST /submit HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
        self.assertIn(b"411", raw)
        raw = self.raw_request(
            b"POST /submit HTTP/1.1\r\nHost: x\r\nContent-Length: 3\r\nContent-Length: 4\r\n"
            b"Connection: close\r\n\r\na=b")
        self.assertIn(b"411", raw)

    def test_oversize_body_is_refused(self) -> None:
        status, _, _ = self.request("POST", "/submit", body=b"a" * (fx.MAX_BODY_BYTES + 1),
                                    headers={"Content-Type": "application/x-www-form-urlencoded"})
        self.assertEqual(413, status)

    def test_too_many_fields_is_refused(self) -> None:
        body = "&".join(f"f{i}=1" for i in range(fx.MAX_FORM_FIELDS + 1)).encode()
        status, _, _ = self.request("POST", "/submit", body=body, headers={
            "Content-Type": "application/x-www-form-urlencoded"})
        self.assertEqual(400, status)


class ConnectionBoundTests(FixtureServerCase):
    max_connections = 2
    idle_timeout = 1.0

    def test_extra_connection_is_refused_with_bounded_busy_response(self) -> None:
        held = [socket.create_connection(("127.0.0.1", self.http_port), timeout=5)
                for _ in range(self.max_connections)]
        try:
            status, _, _ = self.request("GET", "/healthz", timeout=5)
            self.assertEqual(503, status)
        finally:
            for sock in held:
                sock.close()
        time.sleep(0.3)
        status, _, _ = self.request("GET", "/healthz")
        self.assertEqual(200, status)

    def test_stalled_connection_is_closed_within_idle_bound(self) -> None:
        sock = socket.create_connection(("127.0.0.1", self.http_port), timeout=5)
        try:
            start = time.monotonic()
            sock.settimeout(self.idle_timeout + 3)
            data = sock.recv(1)
            elapsed = time.monotonic() - start
            self.assertEqual(b"", data)
            self.assertLess(elapsed, self.idle_timeout + 2.5)
        finally:
            sock.close()

    def test_tls_stall_does_not_block_http(self) -> None:
        stalled = socket.create_connection(("127.0.0.1", self.https_port), timeout=5)
        try:
            status, _, _ = self.request("GET", "/healthz")
            self.assertEqual(200, status)
            stalled.settimeout(self.idle_timeout + 3)
            start = time.monotonic()
            self.assertEqual(b"", stalled.recv(1))
            self.assertLess(time.monotonic() - start, self.idle_timeout + 2.5)
        finally:
            stalled.close()


if __name__ == "__main__":
    unittest.main()
