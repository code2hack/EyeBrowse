package com.code2hack.eyebrowse.core.browser;

import java.net.IDN;
import java.util.Locale;

/**
 * Strict address grammar for the Phone browser slice.
 *
 * <p>The Phone address control accepts only explicit HTTP(S) destinations. Input is normalised to
 * {@code http://} or {@code https://} (scheme-less input becomes {@code https://}); there is no
 * search fallback and no silent repair, so a rejected address never navigates.
 *
 * <p>Documented consequences of the strict grammar: trailing-dot hosts, bare single words (no
 * search fallback), userinfo ({@code user@host}), whitespace or control characters anywhere,
 * non-numeric or out-of-range ports, malformed percent escapes and unsafe delimiters are rejected
 * rather than rewritten. A single-label LAN hostname is accepted only when written with an explicit
 * {@code http://} or {@code https://} scheme; {@code localhost} is also accepted bare.
 */
public final class AddressPolicy {

    /** Reason an address was rejected; each carries a short message for the status line. */
    public enum RejectReason {
        EMPTY("Enter an address"),
        WHITESPACE("Addresses cannot contain spaces"),
        UNSUPPORTED_SCHEME("Only http:// and https:// addresses are supported"),
        MALFORMED("That address is not valid"),
        NO_HOST("Enter a full address such as example.com or http://printer"),
        INVALID_PORT("Port must be between 1 and 65535"),
        USERINFO("Addresses cannot include user names");

        private final String message;

        RejectReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** Outcome of {@link #resolve(String)}: either an accepted normalized URL or a rejection. */
    public static final class Result {
        private final String url;
        private final RejectReason reason;

        private Result(String url, RejectReason reason) {
            this.url = url;
            this.reason = reason;
        }

        public boolean accepted() {
            return url != null;
        }

        /** Normalized absolute HTTP(S) URL; {@code null} when rejected. */
        public String url() {
            return url;
        }

        /** Rejection reason; {@code null} when accepted. */
        public RejectReason reason() {
            return reason;
        }

        /** Short status message; {@code null} when accepted. */
        public String message() {
            return reason == null ? null : reason.message();
        }

        @Override
        public String toString() {
            return accepted() ? "Result.accepted(" + url + ")" : "Result.rejected(" + reason + ")";
        }
    }

    private AddressPolicy() {
    }

    /** Known non-HTTP(S) schemes that must never be treated as a host. */
    private static final String[] FORBIDDEN_SCHEMES = {
        "javascript", "data", "file", "content", "blob", "about", "intent", "mailto", "tel",
        "sms", "vbscript", "chrome", "android", "ftp", "ftps", "ws", "wss", "market", "geo",
        "magnet", "whatsapp", "tg", "view-source", "resource", "jar"
    };

    private static final String ALLOWED_PATH_CHARS = "!$&'()*+,;=:@/?#[]-._~";

    /**
     * Resolves typed address-bar input.
     *
     * @return accepted normalized URL, or a rejection carrying a user-facing reason
     */
    public static Result resolve(String input) {
        if (input == null) {
            return rejected(RejectReason.EMPTY);
        }
        String trimmed = input.strip();
        if (trimmed.isEmpty()) {
            return rejected(RejectReason.EMPTY);
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c <= 0x20 || c == 0x7F) {
                // Covers interior spaces, tabs, newlines and control characters.
                return rejected(RejectReason.WHITESPACE);
            }
            if (c == '\\' || c == '"' || c == '<' || c == '>' || c == '^' || c == '`'
                    || c == '{' || c == '|' || c == '}') {
                return rejected(RejectReason.MALFORMED);
            }
        }
        if (!hasValidPercentEscapes(trimmed)) {
            return rejected(RejectReason.MALFORMED);
        }

        String scheme;
        String remainder;
        boolean explicitScheme = false;
        int firstColon = trimmed.indexOf(':');
        String prefix = firstColon > 0 ? trimmed.substring(0, firstColon) : null;
        boolean prefixIsScheme = prefix != null && prefix.indexOf('.') < 0
                && isValidSchemeToken(prefix);
        if (prefixIsScheme) {
            String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
            String after = trimmed.substring(firstColon + 1);
            if (after.startsWith("//")) {
                if (!lowerPrefix.equals("http") && !lowerPrefix.equals("https")) {
                    return rejected(RejectReason.UNSUPPORTED_SCHEME);
                }
                scheme = lowerPrefix;
                remainder = after.substring(2);
                explicitScheme = true;
            } else if (isForbiddenScheme(lowerPrefix)) {
                return rejected(RejectReason.UNSUPPORTED_SCHEME);
            } else if (lowerPrefix.equals("http") || lowerPrefix.equals("https")) {
                // "http:example.com" is never repaired into a loadable address.
                return rejected(RejectReason.MALFORMED);
            } else {
                // Scheme-less host:port form such as "localhost:8080".
                scheme = "https";
                remainder = trimmed;
            }
        } else {
            scheme = "https";
            remainder = trimmed;
        }
        if (remainder.isEmpty()) {
            return rejected(RejectReason.NO_HOST);
        }

        String authority = remainder;
        String suffix = "";
        int authorityEnd = indexOfAny(remainder, "/?#");
        if (authorityEnd >= 0) {
            authority = remainder.substring(0, authorityEnd);
            suffix = remainder.substring(authorityEnd);
        }
        if (authority.isEmpty()) {
            return rejected(RejectReason.NO_HOST);
        }
        if (authority.indexOf('@') >= 0) {
            return rejected(RejectReason.USERINFO);
        }

        String host;
        String portText = null;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                return rejected(RejectReason.MALFORMED);
            }
            host = authority.substring(1, close);
            String after = authority.substring(close + 1);
            if (!after.isEmpty()) {
                if (after.charAt(0) != ':') {
                    return rejected(RejectReason.MALFORMED);
                }
                portText = after.substring(1);
            }
            if (!isIpv6Literal(host)) {
                return rejected(RejectReason.MALFORMED);
            }
            host = host.toLowerCase(Locale.ROOT);
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) {
                    // Multiple colons without brackets: ambiguous, never repaired into an IPv6 host.
                    return rejected(RejectReason.MALFORMED);
                }
                host = authority.substring(0, colon);
                portText = authority.substring(colon + 1);
            } else {
                host = authority;
            }
            if (host.isEmpty()) {
                return rejected(RejectReason.NO_HOST);
            }
            String ascii;
            try {
                ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            } catch (IllegalArgumentException e) {
                return rejected(RejectReason.MALFORMED);
            }
            ascii = ascii.toLowerCase(Locale.ROOT);
            if (ascii.endsWith(".")) {
                return rejected(RejectReason.MALFORMED);
            }
            if (ascii.equals("localhost")) {
                host = "localhost";
            } else if (isDottedNumeric(ascii)) {
                if (!isIpv4Literal(ascii)) {
                    return rejected(RejectReason.MALFORMED);
                }
                host = ascii;
            } else {
                if (ascii.indexOf('.') < 0) {
                    // A bare single word is never guessed into a host, but an explicit scheme makes
                    // a LAN hostname such as http://printer/ an unambiguous address.
                    if (!explicitScheme) {
                        return rejected(RejectReason.NO_HOST);
                    }
                    if (!isValidSingleLabel(ascii)) {
                        return rejected(RejectReason.MALFORMED);
                    }
                    host = ascii;
                } else if (!isValidDnsName(ascii)) {
                    return rejected(RejectReason.MALFORMED);
                } else {
                    host = ascii;
                }
            }
        }

        int port = -1;
        if (portText != null) {
            if (!isAsciiDigits(portText) || portText.isEmpty() || portText.length() > 5) {
                return rejected(RejectReason.INVALID_PORT);
            }
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                return rejected(RejectReason.INVALID_PORT);
            }
        }

        String normalized = scheme + "://" + (host.indexOf(':') >= 0 ? "[" + host + "]" : host);
        if (port > 0) {
            normalized += ":" + port;
        }
        normalized += suffix;
        return new Result(normalized, null);
    }

    private static Result rejected(RejectReason reason) {
        return new Result(null, reason);
    }

    private static int indexOfAny(String text, String chars) {
        for (int i = 0; i < text.length(); i++) {
            if (chars.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** True when {@code lowerPrefix} names a known non-HTTP(S) scheme. */
    private static boolean isForbiddenScheme(String lowerPrefix) {
        for (String forbidden : FORBIDDEN_SCHEMES) {
            if (lowerPrefix.equals(forbidden)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidSchemeToken(String scheme) {
        if (scheme.isEmpty() || !Character.isLetter(scheme.charAt(0))) {
            return false;
        }
        for (int i = 1; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidPercentEscapes(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '%') {
                continue;
            }
            if (i + 2 >= text.length()) {
                return false;
            }
            if (!isHexDigit(text.charAt(i + 1)) || !isHexDigit(text.charAt(i + 2))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAsciiDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isDottedNumeric(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || !isAsciiDigits(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv4Literal(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length != 4) {
            return false;
        }
        for (String label : labels) {
            if (label.length() > 3) {
                return false;
            }
            int value = Integer.parseInt(label);
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidSingleLabel(String label) {
        if (label.isEmpty() || label.length() > 63) {
            return false;
        }
        if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidDnsName(String host) {
        if (host.length() > 253) {
            return false;
        }
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String host) {
        if (host.isEmpty() || host.indexOf(':') < 0) {
            return false;
        }
        if (host.contains(":::")) {
            return false;
        }
        if (host.indexOf('.') >= 0) {
            // Embedded IPv4 form is out of scope for the slice.
            return false;
        }
        int groups = 0;
        int i = 0;
        while (i < host.length()) {
            int groupStart = i;
            while (i < host.length() && host.charAt(i) != ':') {
                char c = Character.toLowerCase(host.charAt(i));
                boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
                if (!hex) {
                    return false;
                }
                i++;
            }
            int length = i - groupStart;
            if (length > 4) {
                return false;
            }
            if (length > 0) {
                groups++;
            } else if (groupStart == i) {
                // Empty group only legal as part of "::"; checked via the double-colon count below.
            }
            if (i < host.length()) {
                i++;
            }
        }
        boolean compressed = host.contains("::");
        if (compressed) {
            return groups <= 7;
        }
        return groups == 8;
    }
}
