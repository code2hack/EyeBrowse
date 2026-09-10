#!/usr/bin/env python3
"""Test-only HTTP/HTTPS fixture server for the EyeBrowse Phone browser slice.

Default mode is loopback-only (`--bind 127.0.0.1`) and unchanged from the original fixture: pages
carry a changing per-load marker, ``/api/observations`` exposes load/submission metadata for test
assertions, and one HTTPS listener exists so the app's refusal of an untrusted certificate can be
exercised.

C2 amendment (private-LAN fixture access):

* ``--bind`` accepts a numeric loopback or RFC1918 IPv4 address only. Wildcard, public, multicast,
  link-local and hostname values are rejected, and the configured address is validated against the
  host's own interfaces before startup. Nothing else about the product network is changed.
* Every advertised URL (``READY``, ``{{BASE_URL}}``, ``{{SECURE_URL}}``, the generated certificate
  SAN) derives from that validated configuration, never from the request ``Host`` header.
* Fixture-specific safety guards apply before any LAN exposure: an approved fixture-page allowlist
  with symlink/escape refusal, bounded request bodies/targets/fields and connection concurrency,
  bounded state maps, guarded reflection into HTML/JS/headers, bounded logging that never records
  typed values, and cookies restricted to the two named fixture cookies.

It remains a test fixture: no directory listing, upload, proxy, execution or file-write endpoint, and
no privileged or personal data. It must be stopped when its assigned test use is over.
"""

from __future__ import annotations

import argparse
import html
import ipaddress
import json
import os
import re
import socket
import ssl
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PAGES_DIR = REPO_ROOT / "test-fixtures" / "browser"
DEFAULT_HTTP_PORT = 25341
DEFAULT_HTTPS_PORT = 25342

# --- fixture allowlists -------------------------------------------------------------------------
# URL path -> (fixture basename, counted key used by /api/observations). Only these routes are served.
PAGE_ROUTES: dict[str, tuple[str, str]] = {
    "/": ("index.html", "/"),
    "/basic.html": ("basic.html", "/basic.html"),
    "/target-blank.html": ("target-blank.html", "/target-blank.html"),
    "/popup.html": ("popup.html", "/popup.html"),
    "/opened.html": ("opened.html", "/opened.html"),
    "/history-one.html": ("history-one.html", "/history-one.html"),
    "/history-two.html": ("history-two.html", "/history-two.html"),
    "/scroll.html": ("scroll.html", "/scroll.html"),
    "/form.html": ("form.html", "/form.html"),
    "/cookies.html": ("cookies.html", "/cookies.html"),
    "/storage.html": ("storage.html", "/storage.html"),
    "/destinations.html": ("destinations.html", "/destinations.html"),
    "/submitted.html": ("submitted.html", "/submitted.html"),
    "/secure-ok.html": ("secure-ok.html", "/secure-ok.html"),
    "/fail": ("fail.html", "/fail"),
    "/submit": ("form.html", "/submit"),  # GET on the POST endpoint stays a readable page
}
ALLOWED_PAGE_FILES = frozenset(basename for basename, _ in PAGE_ROUTES.values())
COUNT_KEYS = frozenset(key for _, key in PAGE_ROUTES.values())
KNOWN_TEST_IDS = frozenset({"fixture-post-1"})
KNOWN_FIELD_NAMES = frozenset({"test_id", "message", "secret", "notes"})
KNOWN_NOTE_NAMES = frozenset({"dest-mailto", "dest-content", "dest-file", "dest-intent", "dest-data"})
KNOWN_NOTE_VALUES = frozenset({"app-refused", "engine-no-op"})
KNOWN_API_ROUTES = frozenset({
    "/healthz", "/api/observations", "/api/cookie", "/api/cookies", "/api/note", "/favicon.ico",
    "/abort",
})
FIXTURE_COOKIES = ("fixture_session", "fixture_persist")

# --- operating bounds ---------------------------------------------------------------------------
MAX_TARGET_BYTES = 2 * 1024
MAX_BODY_BYTES = 16 * 1024
MAX_FORM_FIELDS = 16
MAX_HISTORY = 200
MAX_TOKEN_LEN = 64
MAX_ACTIVE_CONNECTIONS = 8
IDLE_TIMEOUT_S = 5.0
REQUEST_DEADLINE_S = 10.0
READY_POLL_S = 0.25

SAFE_TOKEN = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
PRIVATE_NETS = (
    ipaddress.IPv4Network("10.0.0.0/8"),
    ipaddress.IPv4Network("172.16.0.0/12"),
    ipaddress.IPv4Network("192.168.0.0/16"),
)


class ConfigError(ValueError):
    """Raised for invalid CLI/configuration values."""


def validate_bind(value: str) -> str:
    """Validate a numeric loopback/RFC1918 IPv4 bind address; reject everything else."""
    try:
        address = ipaddress.IPv4Address(value)
    except (ipaddress.AddressValueError, TypeError) as exc:
        raise ConfigError(
            f"--bind must be a numeric IPv4 address (loopback or RFC1918): {value!r}") from exc
    if address.is_loopback:
        return str(address)
    if any(address in network for network in PRIVATE_NETS):
        return str(address)
    raise ConfigError(
        f"--bind must be loopback or RFC1918 unicast (not wildcard/public/multicast): {value!r}")


def local_ipv4_addresses() -> set[str]:
    """Return the host's current IPv4 addresses without any network mutation."""
    addresses: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addresses.add(info[4][0])
    except OSError:
        pass
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            probe.connect(("192.168.0.1", 9))
            addresses.add(probe.getsockname()[0])
        finally:
            probe.close()
    except OSError:
        pass
    return addresses


def validate_port(value: int, flag: str) -> int:
    if not 1 <= int(value) <= 65535:
        raise ConfigError(f"{flag} must be between 1 and 65535")
    return int(value)


def advertised_base_url(scheme: str, bind: str, port: int) -> str:
    return f"{scheme}://{bind}:{port}"


def strip_control(value: str) -> str:
    return "".join(ch if 32 <= ord(ch) < 127 else "?" for ch in value)


def bounded_token(value: str, fallback: str = "") -> str:
    candidate = (value or "").strip()
    if len(candidate) > MAX_TOKEN_LEN or not SAFE_TOKEN.match(candidate):
        return fallback
    return candidate


def html_safe(value: str) -> str:
    return html.escape(strip_control(value), quote=True)


def js_string(value: str) -> str:
    # A JSON string literal is a safe JS string in this context; escape '<' so '</script>' cannot
    # terminate the surrounding inline script block.
    return json.dumps(value).replace("<", "\\u003c").replace(">", "\\u003e")


def js_inner(value: str) -> str:
    """Escapes a value for insertion inside an existing double-quoted JS string (no added quotes)."""
    return json.dumps(value)[1:-1].replace("<", "\\u003c").replace(">", "\\u003e")


class Observations:
    """Thread-safe, bounded and optionally persisted counters and request metadata."""

    def __init__(self, state_dir: Path) -> None:
        self.state_file = state_dir / "observations.json"
        self.started_at = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        self.load_seq = 0
        self.loads: dict[str, int] = {}
        self.notes: dict[str, str] = {}
        self.requests: list[dict[str, object]] = []
        if self.state_file.exists():
            try:
                data = json.loads(self.state_file.read_text())
                self.started_at = data.get("startedAt", self.started_at)
                self.load_seq = int(data.get("loadSeq", 0))
                self.loads = {
                    str(k): int(v) for k, v in data.get("loads", {}).items() if k in COUNT_KEYS
                }
                self.notes = {
                    str(k): str(v) for k, v in data.get("notes", {}).items()
                    if k in KNOWN_NOTE_NAMES and v in KNOWN_NOTE_VALUES
                }
                self.requests = list(data.get("requests", []))[-MAX_HISTORY:]
            except (OSError, ValueError) as exc:  # a corrupt scratch file must not hide readiness
                print(f"WARN could not read {self.state_file}: {exc}", flush=True)

    def next_load(self, count_key: str) -> str:
        """Increments only an allowlisted count key and returns the per-load marker."""
        with STATE_LOCK:
            self.load_seq += 1
            self.loads[count_key] = self.loads.get(count_key, 0) + 1
            marker = f"L{self.load_seq}"
            self._persist_locked()
            return marker

    def record_request(self, method: str, path: str, scheme: str) -> None:
        bounded_path = path if path in KNOWN_API_ROUTES or path in COUNT_KEYS else "(other)"
        with STATE_LOCK:
            self.requests.append({
                "method": method if method in ("GET", "POST") else "(other)",
                "path": bounded_path,
                "scheme": "https" if scheme == "https" else "http",
            })
            del self.requests[:-MAX_HISTORY]
            self._persist_locked()

    def record_submission(self, test_id: str, field_names: list[str]) -> None:
        allowed = sorted(name for name in field_names if name in KNOWN_FIELD_NAMES)
        with STATE_LOCK:
            self.requests.append({
                "method": "POST",
                "path": "/submit",
                "scheme": "http",
                "testId": test_id if test_id in KNOWN_TEST_IDS else None,
                "fields": allowed,
            })
            del self.requests[:-MAX_HISTORY]
            self._persist_locked()

    def record_note(self, name: str, value: str) -> bool:
        """Records an allowlisted test-case outcome; returns whether it was recorded."""
        if name not in KNOWN_NOTE_NAMES or value not in KNOWN_NOTE_VALUES:
            return False
        with STATE_LOCK:
            self.notes[name] = value
            self._persist_locked()
            return True

    def snapshot(self) -> dict[str, object]:
        with STATE_LOCK:
            return {
                "startedAt": self.started_at,
                "loadSeq": self.load_seq,
                "loads": dict(self.loads),
                "notes": dict(self.notes),
                "requests": list(self.requests),
            }

    @staticmethod
    def _count_keys() -> set[str]:
        return set(COUNT_KEYS)

    def _persist_locked(self) -> None:
        tmp = self.state_file.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(self.snapshot(), indent=2))
        tmp.replace(self.state_file)


STATE_LOCK = threading.RLock()


class FixtureHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    observations: Observations
    pages_dir: Path = DEFAULT_PAGES_DIR
    http_port: int = DEFAULT_HTTP_PORT
    https_port: int = DEFAULT_HTTPS_PORT
    http_base: str = advertised_base_url("http", "127.0.0.1", DEFAULT_HTTP_PORT)
    secure_base: str = advertised_base_url("https", "127.0.0.1", DEFAULT_HTTPS_PORT)
    idle_timeout: float = IDLE_TIMEOUT_S
    request_deadline: float = REQUEST_DEADLINE_S

    # ------------------------------------------------------------------ http verbs

    def do_GET(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler API)
        if not self._target_within_bounds():
            return
        parsed = urlparse(self.path)
        path = parsed.path
        scheme = "https" if getattr(self.server, "secure", False) else "http"
        self.observations.record_request("GET", path, scheme)

        if path == "/healthz":
            self._send_bytes(200, b"ok\n", "text/plain; charset=utf-8")
            return
        if path == "/api/observations":
            self._send_bytes(200, json.dumps(self.observations.snapshot(), indent=2).encode(),
                             "application/json")
            return
        if path == "/api/cookie":
            self._handle_cookie(parse_qs(parsed.query))
            return
        if path == "/api/cookies":
            self._handle_cookies()
            return
        if path == "/api/note":
            self._handle_note(parse_qs(parsed.query))
            return
        if path == "/favicon.ico":
            self._send_bytes(204, b"", "image/x-icon")
            return
        if path == "/abort":
            # Controlled transport failure: no response at all.
            self.close_connection = True
            self.connection.close()
            return
        if path in PAGE_ROUTES:
            page, count_key = PAGE_ROUTES[path]
            status = 500 if path == "/fail" else 200
            self._send_page(status, page, count_key)
            return
        self._send_rejected()

    def do_POST(self) -> None:  # noqa: N802
        if not self._target_within_bounds():
            return
        parsed = urlparse(self.path)
        if parsed.path != "/submit":
            self._send_bytes(404, b"no such endpoint\n", "text/plain; charset=utf-8")
            return
        fields = self._read_form_body()
        if fields is None:
            return  # a bounded error was already sent
        test_id = (fields.get("test_id") or [""])[0]
        # Only the synthetic identifier and field names are recorded; values are discarded.
        self.observations.record_submission(test_id, sorted(fields.keys()))
        shown = test_id if test_id in KNOWN_TEST_IDS else "(unrecognized)"
        self._send_page(200, "submitted.html", "/submit", extra={"{{TEST_ID}}": html_safe(shown)})

    # ------------------------------------------------------------------- helpers

    def _target_within_bounds(self) -> bool:
        if len(self.path.encode("utf-8", "replace")) > MAX_TARGET_BYTES:
            self._send_bytes(414, b"target too long\n", "text/plain; charset=utf-8")
            return False
        return True

    def _read_form_body(self) -> dict[str, list[str]] | None:
        if self.headers.get("Transfer-Encoding"):
            self._send_bytes(501, b"transfer encoding not supported\n", "text/plain; charset=utf-8")
            return None
        lengths = self.headers.get_all("Content-Length") or []
        if len(lengths) != 1 or not lengths[0].strip().isdigit():
            self._send_bytes(411, b"single numeric content length required\n",
                             "text/plain; charset=utf-8")
            return None
        length = int(lengths[0])
        if length > MAX_BODY_BYTES:
            self._send_bytes(413, b"body too large\n", "text/plain; charset=utf-8")
            return None
        raw = self.rfile.read(length).decode("utf-8", "replace")
        fields = parse_qs(raw, keep_blank_values=True)
        if len(fields) > MAX_FORM_FIELDS:
            self._send_bytes(400, b"too many fields\n", "text/plain; charset=utf-8")
            return None
        return fields

    def _handle_cookie(self, query: dict[str, list[str]]) -> None:
        mode = (query.get("mode") or ["persistent"])[0]
        if mode not in ("persistent", "session"):
            mode = "persistent"
        name = "fixture_persist" if mode == "persistent" else "fixture_session"
        value = bounded_token((query.get("value") or [""])[0], fallback=f"v{int(time.time())}")
        header = f"{name}={value}; Path=/"
        if mode == "persistent":
            header += "; Max-Age=86400"
        body = json.dumps({"mode": mode, "name": name, "value": value}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Set-Cookie", header)
        self._send_common_headers(len(body))
        self.end_headers()
        self.wfile.write(body)

    def _handle_cookies(self) -> None:
        """Exposes only the two named fixture cookies, never the whole Cookie header."""
        found: dict[str, str] = {}
        for part in (self.headers.get("Cookie") or "").split(";"):
            name, _, value = part.strip().partition("=")
            if name in FIXTURE_COOKIES:
                found[name] = bounded_token(value, fallback="(invalid)")
        body = json.dumps({"fixtureCookies": found}).encode()
        self._send_bytes(200, body, "application/json")

    def _handle_note(self, query: dict[str, list[str]]) -> None:
        name = (query.get("name") or [""])[0]
        value = (query.get("value") or [""])[0]
        recorded = self.observations.record_note(name, value)
        body = json.dumps({"recorded": recorded}).encode()
        self._send_bytes(200, body, "application/json")

    def _send_page(self, status: int, page: str, count_key: str,
                   extra: dict[str, str] | None = None) -> None:
        safe_count_key = count_key if count_key in Observations._count_keys() else "/"
        resolved = self._resolve_page(page)
        if resolved is None:
            self._send_rejected()
            return
        template = resolved.read_text(encoding="utf-8")
        marker = self.observations.next_load(safe_count_key)
        replacements = {
            "{{LOAD_ID}}": html_safe(marker),
            "{{PATH}}": js_inner(safe_count_key),
            "{{BASE_URL}}": html_safe(self.http_base),
            "{{SECURE_URL}}": html_safe(self.secure_base),
        }
        if extra:
            replacements.update(extra)
        for key, value in replacements.items():
            template = template.replace(key, value)
        self._send_bytes(status, template.encode("utf-8"), "text/html; charset=utf-8")

    def _resolve_page(self, page: str) -> Path | None:
        """Confinement: exact allowlisted basename under the fixture dir, no symlinks or escapes."""
        if page not in ALLOWED_PAGE_FILES:
            return None
        candidate = self.pages_dir / page
        if candidate.is_symlink():
            return None
        try:
            real_root = self.pages_dir.resolve(strict=True)
            real_candidate = candidate.resolve(strict=True)
        except OSError:
            return None
        if real_candidate.parent != real_root or not real_candidate.is_file():
            return None
        return real_candidate

    def _send_rejected(self) -> None:
        self._send_bytes(404, b"fixture: not found\n", "text/plain; charset=utf-8")

    def _send_common_headers(self, length: int) -> None:
        self.send_header("Content-Length", str(length))
        self.send_header("Connection", "close")
        self.close_connection = True

    def _send_bytes(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self._send_common_headers(len(body))
        self.end_headers()
        self.wfile.write(body)

    # ------------------------------------------------------------- logging/bounds

    def log_request(self, code="-", size="-") -> None:
        """Logs bounded route/status metadata only; never the raw target or any value."""
        route = urlparse(self.path or "").path
        if route not in PAGE_ROUTES and route not in KNOWN_API_ROUTES and route != "/abort":
            route = "(other)"
        sys.stderr.write(f'fixture {self.address_string()} "{self.command} {route}" {code}\n')

    def log_message(self, fmt: str, *args: object) -> None:
        sys.stderr.write("fixture " + strip_control(fmt % args)[:200] + "\n")

    def setup(self) -> None:
        self._accepted_at = time.monotonic()
        super().setup()

    def handle_one_request(self) -> None:
        if time.monotonic() - self._accepted_at > self.request_deadline:
            self.close_connection = True
            return
        super().handle_one_request()


class BoundedThreadingHTTPServer(ThreadingHTTPServer):
    """Threaded server with a shared connection budget, bounded I/O and optional TLS."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address: tuple[str, int], handler: type[FixtureHandler], *,
                 slots: threading.BoundedSemaphore, tls_context: ssl.SSLContext | None = None,
                 secure: bool = False, idle_timeout: float = IDLE_TIMEOUT_S) -> None:
        self._slots = slots
        self.tls_context = tls_context
        self.secure = secure
        self.idle_timeout = idle_timeout
        super().__init__(address, handler)

    def get_request(self):  # type: ignore[no-untyped-def]
        request, client_address = super().get_request()
        request.settimeout(self.idle_timeout)
        if self.tls_context is not None:
            try:
                request = self.tls_context.wrap_socket(request, server_side=True)
            except (ssl.SSLError, OSError):
                request.close()
                raise
            request.settimeout(self.idle_timeout)
        return request, client_address

    def process_request(self, request, client_address) -> None:  # type: ignore[no-untyped-def]
        if not self._slots.acquire(blocking=False):
            self._refuse_busy(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self._slots.release()
            raise

    def process_request_thread(self, request, client_address) -> None:  # type: ignore[no-untyped-def]
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._slots.release()

    def handle_error(self, request, client_address) -> None:  # type: ignore[no-untyped-def]
        """Bounded failure output: no traceback dump for a malformed or stalled client."""
        sys.stderr.write("fixture connection error (closed safely)\n")

    @staticmethod
    def _refuse_busy(request) -> None:  # type: ignore[no-untyped-def]
        body = b"fixture busy\n"
        try:
            request.settimeout(1.0)
            request.sendall(b"HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n"
                            b"Content-Length: %d\r\n\r\n" % len(body) + body)
        except OSError:
            pass
        finally:
            try:
                request.close()
            except OSError:
                pass


class FixtureConfig:
    """Validated fixture configuration shared by both listeners."""

    def __init__(self, *, bind: str, http_port: int, https_port: int, state_dir: Path,
                 pages_dir: Path = DEFAULT_PAGES_DIR, idle_timeout: float = IDLE_TIMEOUT_S,
                 max_connections: int = MAX_ACTIVE_CONNECTIONS) -> None:
        self.bind = validate_bind(bind)
        self.http_port = validate_port(http_port, "--http-port")
        self.https_port = validate_port(https_port, "--https-port")
        if self.http_port == self.https_port:
            raise ConfigError("--http-port and --https-port must differ")
        self.state_dir = state_dir
        self.pages_dir = pages_dir
        self.idle_timeout = idle_timeout
        self.max_connections = max_connections
        self.http_base = advertised_base_url("http", self.bind, self.http_port)
        self.secure_base = advertised_base_url("https", self.bind, self.https_port)


def ensure_certificate(cert_dir: Path, bind: str) -> tuple[Path, Path]:
    """Create/reuse a disposable self-signed certificate whose SAN matches the configured bind."""
    cert_dir.mkdir(parents=True, exist_ok=True)
    cert = cert_dir / "fixture-cert.pem"
    key = cert_dir / "fixture-key.pem"
    stamp = cert_dir / "bind-ip"
    if cert.is_file() and key.is_file() and stamp.is_file() and stamp.read_text().strip() == bind:
        return cert, key
    subprocess.run(
        [
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "7",
            "-keyout", str(key), "-out", str(cert),
            "-subj", f"/CN={bind}",
            "-addext", f"subjectAltName=IP:{bind}",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    key.chmod(0o600)
    stamp.write_text(bind + "\n")
    print(f"INFO generated disposable fixture certificate for {bind} in {cert_dir}", flush=True)
    return cert, key


def create_servers(config: FixtureConfig) -> tuple[BoundedThreadingHTTPServer, BoundedThreadingHTTPServer, Observations]:
    """Builds both listeners without starting them (tests use this directly)."""
    config.state_dir.mkdir(parents=True, exist_ok=True)
    observations = Observations(config.state_dir)
    handler = type("ConfiguredFixtureHandler", (FixtureHandler,), {
        "observations": observations,
        "pages_dir": config.pages_dir,
        "http_port": config.http_port,
        "https_port": config.https_port,
        "http_base": config.http_base,
        "secure_base": config.secure_base,
        "idle_timeout": config.idle_timeout,
    })
    slots = threading.BoundedSemaphore(config.max_connections)

    http_server = BoundedThreadingHTTPServer((config.bind, config.http_port), handler,
                                             slots=slots, secure=False,
                                             idle_timeout=config.idle_timeout)
    cert, key = ensure_certificate(config.state_dir / "tls", config.bind)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certfile=cert, keyfile=key)
    https_server = BoundedThreadingHTTPServer((config.bind, config.https_port), handler,
                                              slots=slots, tls_context=context, secure=True,
                                              idle_timeout=config.idle_timeout)
    return http_server, https_server, observations


def guard_ready_file(state_dir: Path) -> None:
    """Refuses a second server on one observation directory (no competing writers)."""
    ready = state_dir / "ready"
    if not ready.exists():
        return
    try:
        pid = int(ready.read_text().split()[0])
    except (OSError, ValueError):
        raise ConfigError(f"stale ready file present, remove or reuse another --state-dir: {ready}")
    try:
        os.kill(pid, 0)
    except OSError:
        return
    raise ConfigError(f"another fixture server (pid {pid}) uses this --state-dir")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", required=True,
                        help="Directory for observations.json and the disposable TLS key pair")
    parser.add_argument("--http-port", type=int, default=DEFAULT_HTTP_PORT)
    parser.add_argument("--https-port", type=int, default=DEFAULT_HTTPS_PORT)
    parser.add_argument("--bind", default="127.0.0.1",
                        help="Numeric loopback or RFC1918 IPv4 address (default 127.0.0.1)")
    args = parser.parse_args()

    try:
        config = FixtureConfig(bind=args.bind, http_port=args.http_port,
                               https_port=args.https_port, state_dir=Path(args.state_dir))
    except ConfigError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2

    if not ipaddress.IPv4Address(config.bind).is_loopback:
        assigned = local_ipv4_addresses()
        if config.bind not in assigned:
            print(f"ERROR --bind {config.bind} is not assigned to this host ({sorted(assigned)})",
                  file=sys.stderr)
            return 2

    try:
        guard_ready_file(config.state_dir)
        http_server, https_server, _ = create_servers(config)
    except (ConfigError, OSError) as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2

    threading.Thread(target=http_server.serve_forever, daemon=True).start()
    threading.Thread(target=https_server.serve_forever, daemon=True).start()

    ready = config.state_dir / "ready"
    ready.write_text(f"{os.getpid()} {config.http_base} {config.secure_base}\n")
    print(f"READY http={config.http_base} https={config.secure_base} "
          f"state={config.state_dir} bind={config.bind}", flush=True)
    try:
        while True:
            time.sleep(READY_POLL_S)
    except KeyboardInterrupt:
        print("stopping fixture server", flush=True)
    finally:
        http_server.shutdown()
        https_server.shutdown()
        if ready.exists():
            ready.unlink()
    return 0


if __name__ == "__main__":
    sys.exit(main())
