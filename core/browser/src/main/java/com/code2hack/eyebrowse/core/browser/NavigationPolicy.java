package com.code2hack.eyebrowse.core.browser;

import java.util.Locale;

/**
 * Destination policy shared by the address control and in-page navigation handling.
 *
 * <p>Only {@code http} and {@code https} remain inside the browser session. Every other scheme
 * (including {@code javascript:}, {@code data:}, {@code file:}, {@code content:}, {@code blob:},
 * {@code intent:} and {@code about:}) is refused instead of being loaded or forwarded.
 *
 * <p>Scope: this policy decides about a main-frame navigation that the WebView asks the host
 * application to handle. It is not a blanket ban on page-owned JavaScript: a {@code javascript:}
 * link that the page itself activates runs inside that page's ordinary WebView sandbox (with no
 * EyeBrowse script bridge or native capability), and a destination the engine refuses on its own may
 * simply be a no-op. Typing any of these values into EyeBrowse's address bar is a different origin
 * and is rejected by {@link AddressPolicy} before any navigation.
 */
public final class NavigationPolicy {

    private NavigationPolicy() {
    }

    /** True when {@code url} is a non-empty http/https destination that the browser may load. */
    public static boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int schemeEnd = trimmed.indexOf(':');
        if (schemeEnd <= 0) {
            return false;
        }
        for (int i = 0; i < schemeEnd; i++) {
            char c = trimmed.charAt(i);
            boolean schemeChar = Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
            if (!schemeChar) {
                return false;
            }
        }
        String scheme = trimmed.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        String rest = trimmed.substring(schemeEnd + 1);
        return rest.startsWith("//") && rest.length() > 2;
    }

    /** Human-readable refusal notice used by the status line for blocked destinations. */
    public static String refusalMessage(String url) {
        return "Blocked unsupported address";
    }
}
