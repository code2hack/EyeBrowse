package com.code2hack.eyebrowse.core.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Table-driven checks of the strict address grammar and its normalization. */
public class AddressPolicyTest {

    @Test
    public void normalizesAcceptedForms() {
        assertAccepted("example.com", "https://example.com");
        assertAccepted("example.com/path?x=1#frag", "https://example.com/path?x=1#frag");
        assertAccepted("http://example.com", "http://example.com");
        assertAccepted("HTTP://Example.COM/Path", "http://example.com/Path");
        assertAccepted("https://example.com:8443/x", "https://example.com:8443/x");
        assertAccepted("localhost:8080", "https://localhost:8080");
        assertAccepted("http://localhost", "http://localhost");
        assertAccepted("http://printer/", "http://printer/");
        assertAccepted("HTTP://PRINTER/", "http://printer/");
        assertAccepted("https://nas", "https://nas");
        assertAccepted("http://printer:631/status", "http://printer:631/status");
        assertAccepted("https://nas:8443/admin?x=1", "https://nas:8443/admin?x=1");
        assertAccepted("127.0.0.1:25341/", "https://127.0.0.1:25341/");
        assertAccepted("[::1]:25342", "https://[::1]:25342");
        assertAccepted("http://[2001:db8::1]/a", "http://[2001:db8::1]/a");
        assertAccepted("  example.com  ", "https://example.com");
        assertAccepted("example.com/a%20b?q=%2F", "https://example.com/a%20b?q=%2F");
        assertAccepted("example.com:080", "https://example.com:80");
        assertAccepted("sub.domain.example.co.uk", "https://sub.domain.example.co.uk");
    }

    @Test
    public void convertsUnicodeHostsToAscii() {
        AddressPolicy.Result result = AddressPolicy.resolve("example.com");
        assertTrue(result.toString(), result.accepted());
        result = AddressPolicy.resolve("\u4f8b\u5b50.\u6d4b\u8bd5");
        assertTrue(result.toString(), result.accepted());
        assertEquals("https://xn--fsqu00a.xn--0zwm56d", result.url());
    }

    @Test
    public void rejectsEmptyAndWhitespace() {
        assertRejected("", AddressPolicy.RejectReason.EMPTY);
        assertRejected("   ", AddressPolicy.RejectReason.EMPTY);
        assertRejected("exa mple.com", AddressPolicy.RejectReason.WHITESPACE);
        assertRejected("example.com/\u0001", AddressPolicy.RejectReason.WHITESPACE);
        assertRejected("example.com\nhttp://evil.example", AddressPolicy.RejectReason.WHITESPACE);
    }

    @Test
    public void rejectsNonHttpSchemes() {
        assertRejected("javascript:alert(1)", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("data:text/html,hello", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("file:///etc/passwd", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("content://media/external/images", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("blob:https://example.com/uuid", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("about:blank", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("intent://scan/#Intent;scheme=zxing", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("mailto:someone@example.com", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("ftp://example.com", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
        assertRejected("ws://example.com/socket", AddressPolicy.RejectReason.UNSUPPORTED_SCHEME);
    }

    @Test
    public void rejectsUserinfo() {
        assertRejected("user@example.com", AddressPolicy.RejectReason.USERINFO);
        assertRejected("http://user:pass@example.com", AddressPolicy.RejectReason.USERINFO);
    }

    @Test
    public void rejectsBadPorts() {
        assertRejected("example.com:0", AddressPolicy.RejectReason.INVALID_PORT);
        assertRejected("example.com:65536", AddressPolicy.RejectReason.INVALID_PORT);
        assertRejected("example.com:abc", AddressPolicy.RejectReason.INVALID_PORT);
        assertRejected("example.com:", AddressPolicy.RejectReason.INVALID_PORT);
        assertRejected("example.com:123456", AddressPolicy.RejectReason.INVALID_PORT);
    }

    @Test
    public void rejectsMalformedAuthorities() {
        assertRejected("example.com:8080:9090", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("[::1", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("[::1]x", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("[zzz]", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("example.com\\path", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("example.com/a%zz", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("example.com/%2", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("http://", AddressPolicy.RejectReason.NO_HOST);
        assertRejected("https://", AddressPolicy.RejectReason.NO_HOST);
        assertRejected(":8080", AddressPolicy.RejectReason.NO_HOST);
    }

    @Test
    public void rejectsUnsupportedHostForms() {
        assertRejected("singlelabel", AddressPolicy.RejectReason.NO_HOST);
        assertRejected("printer", AddressPolicy.RejectReason.NO_HOST);
        assertRejected("printer:631", AddressPolicy.RejectReason.NO_HOST);
        assertRejected("example.com.", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("999.1.1.1", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("256.1.1.1", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("exa_mple.com", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("-bad.example.com", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("bad-.example.com", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("http://-printer/", AddressPolicy.RejectReason.MALFORMED);
        assertRejected("https://bad_name/", AddressPolicy.RejectReason.MALFORMED);
    }

    @Test
    public void messagesExistForEveryRejection() {
        for (AddressPolicy.RejectReason reason : AddressPolicy.RejectReason.values()) {
            assertFalse(reason.name(), reason.message().isEmpty());
        }
    }

    private static void assertAccepted(String input, String expectedUrl) {
        AddressPolicy.Result result = AddressPolicy.resolve(input);
        assertTrue(input + " -> " + result, result.accepted());
        assertEquals(input, expectedUrl, result.url());
        assertNull(input, result.reason());
        assertNull(input, result.message());
    }

    private static void assertRejected(String input, AddressPolicy.RejectReason expected) {
        AddressPolicy.Result result = AddressPolicy.resolve(input);
        assertFalse(input + " unexpectedly accepted as " + result.url(), result.accepted());
        assertEquals(input, expected, result.reason());
        assertNull(input, result.url());
        assertEquals(input, expected.message(), result.message());
    }
}
