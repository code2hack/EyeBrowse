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
import select
import socket
import ssl
import sys
import tempfile
import threading
import time
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from urllib.parse import urlencode
from unittest.mock import patch

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
    request_deadline = 10.0

    def setUp(self) -> None:
        # Each case owns a fresh budget/state; a previous closing TLS handler cannot consume its slots.
        self._tmp = tempfile.TemporaryDirectory(prefix="eyebrowse-fixture-test-")
        self.addCleanup(self._tmp.cleanup)
        self.state_dir = Path(self._tmp.name)
        self.config = fx.FixtureConfig(
            bind="127.0.0.1",
            http_port=free_port(),
            https_port=free_port(),
            state_dir=self.state_dir,
            idle_timeout=self.idle_timeout,
            max_connections=self.max_connections,
            request_deadline=self.request_deadline,
        )
        self.http_server, self.https_server, self.observations = fx.create_servers(self.config)
        self.addCleanup(self._stop)
        threading.Thread(target=self.http_server.serve_forever, daemon=True).start()
        threading.Thread(target=self.https_server.serve_forever, daemon=True).start()
        self.http_port = self.http_server.server_address[1]
        self.https_port = self.https_server.server_address[1]

    def _stop(self) -> None:
        self.http_server.shutdown()
        self.https_server.shutdown()
        self.http_server.server_close()
        self.https_server.server_close()

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

    def test_partial_startup_closes_http_listener(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            port = free_port()
            config = fx.FixtureConfig(bind="127.0.0.1", http_port=port,
                                      https_port=free_port(), state_dir=Path(tmp))
            with patch.object(fx, "ensure_certificate", side_effect=OSError("synthetic TLS setup failure")):
                with self.assertRaises(OSError):
                    fx.create_servers(config)
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))

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
            "GET", "/api/case?case=dest-content&phase=baseline&marker=L7&location="
                   + base.replace(":", "%3A").replace("/", "%2F") + "%2Fdestinations.html"
                   + "&outcome=prepared&t=4242&sampleStart=4200&sampleEnd=4230")
        self.assertEqual(200, status)
        self.assertTrue(json.loads(body)["recorded"])
        entry = self.observations.snapshot()["cases"][-1]
        self.assertEqual("dest-content", entry["case"])
        self.assertEqual("baseline", entry["phase"])
        self.assertEqual("L7", entry["marker"])
        self.assertEqual("prepared", entry["outcome"])
        self.assertEqual(4242, entry["uptimeMs"])
        self.assertEqual(4200, entry["sampleStartMs"])
        self.assertEqual(4230, entry["sampleEndMs"])
        self.assertEqual(-1, entry["actionStartMs"])
        self.assertEqual("not-attempted", entry["dispatchStage"])
        self.assertEqual(2, entry["traceVersion"])
        self.assertEqual("/destinations.html", entry["location"])
        self.assertIsInstance(entry["loadSeq"], int)

    def test_case_channel_rejects_unknown_values_and_sanitizes(self) -> None:
        for query in ("case=evil&phase=baseline&marker=L1&location=x&outcome=prepared&t=1",
                      "case=dest-content&phase=middle&marker=L1&location=x&outcome=prepared&t=1",
                      "case=dest-content&phase=baseline&marker=L1&location=x&outcome=passed&t=1",
                      "case=dest-content&phase=baseline&marker=L1&location=x&outcome=returned&t=1",
                      "case=dest-content&phase=start&marker=L1&location=x&outcome=dispatched&t=1"):
            status, body, _ = self.request("GET", "/api/case?" + query)
            self.assertEqual(200, status)
            self.assertFalse(json.loads(body)["recorded"], query)
        status, body, _ = self.request(
            "GET", "/api/case?case=dest-intent&phase=observation&marker=NOT-A-MARKER"
                   "&location=http%3A%2F%2Fevil.example%2Fx&outcome=refused&t=4242")
        self.assertEqual(200, status)
        self.assertTrue(json.loads(body)["recorded"])
        entry = self.observations.snapshot()["cases"][-1]
        self.assertEqual("(other)", entry["marker"])
        self.assertEqual("(other)", entry["location"])
        self.assertEqual(4242, entry["uptimeMs"])

    def test_case_location_parses_exact_origin_and_records_only_fixture_paths(self) -> None:
        base = self.config.http_base
        samples = [
            (base + "/basic.html?SYNTHETIC_QUERY=value#SYNTHETIC_FRAGMENT", "/basic.html"),
            (self.config.secure_base + "/secure-ok.html", "/secure-ok.html"),
            (base + "@evil.invalid/basic.html", "(other)"),
            (base + "0/basic.html", "(other)"),
            (base.replace("http://", "http://user:SYNTHETIC_PASSWORD@") + "/basic.html", "(other)"),
            (base + "/SYNTHETIC_IDENTIFIER.html", "(other)"),
            (base + "/%62asic.html", "(other)"),
            ("file:///basic.html", "(other)"),
        ]
        stream = io.StringIO()
        with redirect_stderr(stream):
            for location, expected in samples:
                with self.subTest(location=location):
                    query = urlencode({"case": "native-0", "phase": "observation", "marker": "L2",
                                       "location": location, "outcome": "refused", "t": "12"})
                    status, body, _ = self.request("GET", "/api/case?" + query)
                    self.assertEqual(200, status)
                    self.assertTrue(json.loads(body)["recorded"])
                    self.assertEqual(expected, self.observations.snapshot()["cases"][-1]["location"])
        for value in ("SYNTHETIC_QUERY", "SYNTHETIC_FRAGMENT", "SYNTHETIC_PASSWORD", "SYNTHETIC_IDENTIFIER"):
            self.assertNotIn(value, self.state_text())
            self.assertNotIn(value, stream.getvalue())

    def test_case_failure_preserves_unknown_dispatch_and_missing_observation(self) -> None:
        self.assertTrue(self.observations.record_case(
            "dest-content", "failure", "", "(other)", "dispatch-unknown", "300",
            "-1", "-1", "200", "250", "up-attempted"))
        entry = self.observations.snapshot()["cases"][-1]
        self.assertEqual("dispatch-unknown", entry["outcome"])
        self.assertEqual("up-attempted", entry["dispatchStage"])
        self.assertEqual(200, entry["actionStartMs"])
        self.assertEqual(250, entry["actionEndMs"])
        self.assertEqual(-1, entry["sampleEndMs"])
        for bad in ("abc", "-2", str(1 << 63)):
            self.assertFalse(self.observations.record_case(
                "dest-content", "baseline", "L1", "(other)", "prepared", bad))

    def test_malformed_request_does_not_log_its_query(self) -> None:
        stream = io.StringIO()
        with redirect_stderr(stream):
            self.raw_request(b"GET /basic.html?SYNTHETIC_PRIVATE_QUERY invalid-version\r\n\r\n")
        self.assertNotIn("SYNTHETIC_PRIVATE_QUERY", stream.getvalue())

    def test_case_channel_is_bounded(self) -> None:
        for index in range(fx.MAX_CASE_REPORTS + 5):
            self.assertTrue(self.observations.record_case(
                "dest-file", "observation", "L1", self.config.http_base + "/x", "no-op", str(index)))
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

    def test_short_body_is_not_recorded_as_a_submission(self) -> None:
        before = list(self.observations.snapshot()["requests"])
        with socket.create_connection(("127.0.0.1", self.http_port), timeout=2) as sock:
            sock.sendall(b"POST /submit HTTP/1.1\r\nHost: fixture\r\nContent-Length: 100\r\n\r\nx=1")
            sock.shutdown(socket.SHUT_WR)
            self.assertIn(b"400", sock.recv(4096))
        self.assertEqual(before, self.observations.snapshot()["requests"])

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

    def test_tls_handshakes_share_the_connection_budget(self) -> None:
        held = []
        try:
            for _ in range(self.max_connections):
                incoming, outgoing = ssl.MemoryBIO(), ssl.MemoryBIO()
                client = ssl.create_default_context().wrap_bio(
                    incoming, outgoing, server_side=False, server_hostname="127.0.0.1")
                with self.assertRaises(ssl.SSLWantReadError):
                    client.do_handshake()
                sock = socket.create_connection(("127.0.0.1", self.https_port), timeout=2)
                held.append(sock)
                sock.sendall(outgoing.read())
                # A TLS handshake response proves admission. Stop before ClientFinished; no trust bypass.
                self.assertEqual(b"\x16", sock.recv(4096)[:1])
            status, _, _ = self.request("GET", "/healthz")
            self.assertEqual(503, status)
        finally:
            for sock in held:
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


class AbsoluteDeadlineTests(FixtureServerCase):
    idle_timeout = 3.0  # Exceeds the 1.3 s assertion bound, including SSL's cumulative handshake timeout.
    request_deadline = 0.45

    def assert_drip_is_closed(self, prefix: bytes, *, port: int | None = None,
                              drip: bytes = b"x" * 64) -> None:
        with socket.create_connection(("127.0.0.1", port or self.http_port), timeout=2) as sock:
            sock.sendall(prefix)
            start = time.monotonic()
            closed = False
            index = 0
            while time.monotonic() - start < 1.3:
                try:
                    sock.sendall(drip[index:index + 1])
                    index += 1
                except OSError:
                    closed = True
                    break
                readable, _, _ = select.select([sock], [], [], 0.08)
                if readable:
                    closed = sock.recv(4096) == b""
                    if closed:
                        break
            self.assertTrue(closed, "drip traffic extended the absolute connection deadline")
            self.assertLess(time.monotonic() - start, 1.3)

    def test_partial_tls_handshake_cannot_extend_deadline(self) -> None:
        incoming, outgoing = ssl.MemoryBIO(), ssl.MemoryBIO()
        client = ssl.create_default_context().wrap_bio(
            incoming, outgoing, server_side=False, server_hostname="127.0.0.1")
        with self.assertRaises(ssl.SSLWantReadError):
            client.do_handshake()
        hello = outgoing.read()
        self.assert_drip_is_closed(hello[:5], port=self.https_port, drip=hello[5:])

    def test_partial_headers_cannot_extend_deadline(self) -> None:
        self.assert_drip_is_closed(b"GET /healthz HTTP/1.1\r\nX-Drip: ")

    def test_partial_body_cannot_extend_deadline(self) -> None:
        self.assert_drip_is_closed(b"POST /submit HTTP/1.1\r\nHost: fixture\r\n"
                                  b"Content-Length: 1024\r\n\r\n")


if __name__ == "__main__":
    unittest.main()
