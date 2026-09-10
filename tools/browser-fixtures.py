#!/usr/bin/env python3
"""Test-only HTTP/HTTPS fixture server for the EyeBrowse Phone browser slice.

The server is deliberately small and dependency-free (Python standard library):

* it binds only to 127.0.0.1 on the two reserved ports (HTTP 25341, HTTPS 25342);
* every page load gets a changing in-memory marker so tests can prove a document did (or did
  not) reload;
* ``/api/observations`` exposes load/submission counters for assertions in test code;
* the POST endpoint records only a synthetic ``test_id`` and the submitted field *names*, never
  typed values, passwords or page content;
* the HTTPS listener uses a disposable self-signed certificate and exists so that the app's
  refusal of an untrusted certificate can be exercised. It is not a trusted endpoint.

It is a test fixture, not a product service: no remote control, no persistent homepage, and no
provisioning interface.
"""

from __future__ import annotations

import argparse
import json
import ssl
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

REPO_ROOT = Path(__file__).resolve().parent.parent
PAGES_DIR = REPO_ROOT / "test-fixtures" / "browser"
DEFAULT_HTTP_PORT = 25341
DEFAULT_HTTPS_PORT = 25342

# Re-entrant: helpers may take the lock while a caller already holds it.
STATE_LOCK = threading.RLock()


class Observations:
    """Thread-safe, optionally persisted counters and request metadata."""

    def __init__(self, state_dir: Path) -> None:
        self.state_file = state_dir / "observations.json"
        self.started_at = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        self.load_seq = 0
        self.loads: dict[str, int] = {}
        self.requests: list[dict[str, object]] = []
        if self.state_file.exists():
            try:
                data = json.loads(self.state_file.read_text())
                self.started_at = data.get("startedAt", self.started_at)
                self.load_seq = int(data.get("loadSeq", 0))
                self.loads = {str(k): int(v) for k, v in data.get("loads", {}).items()}
                self.requests = list(data.get("requests", []))
            except (OSError, ValueError) as exc:  # a corrupt scratch file must not hide readiness
                print(f"WARN could not read {self.state_file}: {exc}", flush=True)

    def next_load(self, path: str) -> str:
        with STATE_LOCK:
            self.load_seq += 1
            self.loads[path] = self.loads.get(path, 0) + 1
            marker = f"L{self.load_seq}"
            self._persist_locked()
            return marker

    def record_request(self, method: str, path: str, scheme: str) -> None:
        with STATE_LOCK:
            self.requests.append({"method": method, "path": path, "scheme": scheme})
            del self.requests[:-200]
            self._persist_locked()

    def record_submission(self, test_id: str, field_names: list[str]) -> None:
        with STATE_LOCK:
            self.requests.append({
                "method": "POST",
                "path": "/submit",
                "scheme": "http",
                "testId": test_id or None,
                "fields": field_names,
            })
            del self.requests[:-200]
            self._persist_locked()

    def snapshot(self) -> dict[str, object]:
        with STATE_LOCK:
            return {
                "startedAt": self.started_at,
                "loadSeq": self.load_seq,
                "loads": dict(self.loads),
                "requests": list(self.requests),
            }

    def _persist_locked(self) -> None:
        tmp = self.state_file.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(self.snapshot(), indent=2))
        tmp.replace(self.state_file)


class FixtureHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    observations: Observations
    http_port: int = DEFAULT_HTTP_PORT
    https_port: int = DEFAULT_HTTPS_PORT

    # ------------------------------------------------------------------ http verbs

    def do_GET(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler API)
        parsed = urlparse(self.path)
        path = parsed.path
        scheme = "https" if getattr(self.server, "secure", False) else "http"
        self.observations.record_request("GET", path, scheme)

        if path == "/healthz":
            self._send_bytes(200, b"ok\n", "text/plain; charset=utf-8")
            return
        if path == "/api/observations":
            body = json.dumps(self.observations.snapshot(), indent=2).encode()
            self._send_bytes(200, body, "application/json")
            return
        if path == "/api/cookie":
            self._handle_cookie(parse_qs(parsed.query))
            return
        if path == "/api/cookies":
            cookies = self.headers.get("Cookie", "")
            self._send_bytes(200, json.dumps({"cookieHeader": cookies}).encode(),
                             "application/json")
            return
        if path == "/favicon.ico":
            self._send_bytes(204, b"", "image/x-icon")
            return
        if path == "/fail":
            self._send_page(500, "fail.html", path=path)
            return
        if path == "/abort":
            # Controlled transport failure: no response at all.
            self.close_connection = True
            self.connection.close()
            return
        if path == "/submit":
            # GET on the POST endpoint is a readable page, not a submission.
            self._send_page(200, "form.html", path=path)
            return

        page = "index.html" if path == "/" else path.lstrip("/")
        if "/" in page or page.startswith(".") or not page.endswith(".html"):
            self._send_page(404, "notfound.html", path=path)
            return
        if not (PAGES_DIR / page).is_file():
            self._send_page(404, "notfound.html", path=path)
            return
        self._send_page(200, page, path=path)

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path != "/submit":
            self._send_bytes(404, b"no such endpoint\n", "text/plain; charset=utf-8")
            return
        length = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(length).decode("utf-8", "replace")
        fields = parse_qs(raw, keep_blank_values=True)
        test_id = (fields.get("test_id") or [""])[0]
        # Only the synthetic identifier and field names are recorded; values are discarded.
        field_names = sorted(fields.keys())
        self.observations.record_submission(test_id, field_names)
        self._send_page(
            200,
            "submitted.html",
            path=parsed.path,
            extra={"{{TEST_ID}}": test_id or "(missing test_id)"},
        )

    # ------------------------------------------------------------------- helpers

    def _handle_cookie(self, query: dict[str, list[str]]) -> None:
        mode = (query.get("mode") or ["persistent"])[0]
        value = (query.get("value") or [f"v{int(time.time())}"])[0]
        if mode == "session":
            header = f"fixture_session={value}; Path=/"
        else:
            header = f"fixture_persist={value}; Path=/; Max-Age=86400"
        body = json.dumps({"mode": mode, "set": header}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Set-Cookie", header)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_page(self, status: int, page: str, path: str,
                   extra: dict[str, str] | None = None) -> None:
        template = (PAGES_DIR / page).read_text(encoding="utf-8")
        marker = self.observations.next_load(path)
        replacements = {
            "{{LOAD_ID}}": marker,
            "{{PATH}}": path,
            "{{BASE_URL}}": f"http://127.0.0.1:{self.http_port}",
            "{{SECURE_URL}}": f"https://127.0.0.1:{self.https_port}",
        }
        if extra:
            replacements.update(extra)
        for key, value in replacements.items():
            template = template.replace(key, value)
        self._send_bytes(status, template.encode("utf-8"), "text/html; charset=utf-8")

    def _send_bytes(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: object) -> None:  # keep the console readable
        sys.stderr.write("fixture %s - %s\n" % (self.address_string(), fmt % args))


def ensure_certificate(cert_dir: Path) -> tuple[Path, Path]:
    """Create a disposable self-signed certificate outside source control when absent."""
    cert_dir.mkdir(parents=True, exist_ok=True)
    cert = cert_dir / "fixture-cert.pem"
    key = cert_dir / "fixture-key.pem"
    if cert.is_file() and key.is_file():
        return cert, key
    subprocess.run(
        [
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "7",
            "-keyout", str(key), "-out", str(cert),
            "-subj", "/CN=127.0.0.1",
            "-addext", "subjectAltName=IP:127.0.0.1",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    print(f"INFO generated disposable fixture certificate in {cert_dir}", flush=True)
    return cert, key


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", required=True,
                        help="Directory for observations.json and the disposable TLS key pair")
    parser.add_argument("--http-port", type=int, default=DEFAULT_HTTP_PORT)
    parser.add_argument("--https-port", type=int, default=DEFAULT_HTTPS_PORT)
    args = parser.parse_args()

    state_dir = Path(args.state_dir)
    state_dir.mkdir(parents=True, exist_ok=True)
    health = state_dir / "ready"
    if health.exists():
        health.unlink()

    observations = Observations(state_dir)
    FixtureHandler.observations = observations
    FixtureHandler.http_port = args.http_port
    FixtureHandler.https_port = args.https_port

    http_server = ThreadingHTTPServer(("127.0.0.1", args.http_port), FixtureHandler)
    http_server.daemon_threads = True
    http_server.secure = False

    cert, key = ensure_certificate(state_dir / "tls")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certfile=cert, keyfile=key)
    https_server = ThreadingHTTPServer(("127.0.0.1", args.https_port), FixtureHandler)
    https_server.daemon_threads = True
    https_server.secure = True
    https_server.socket = context.wrap_socket(https_server.socket, server_side=True)

    threading.Thread(target=http_server.serve_forever, daemon=True).start()
    threading.Thread(target=https_server.serve_forever, daemon=True).start()

    health.write_text("ready\n")
    print(f"READY http=http://127.0.0.1:{args.http_port} "
          f"https=https://127.0.0.1:{args.https_port} state={state_dir}", flush=True)
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("stopping fixture server", flush=True)
    finally:
        http_server.shutdown()
        https_server.shutdown()
        if health.exists():
            health.unlink()
    return 0


if __name__ == "__main__":
    sys.exit(main())
