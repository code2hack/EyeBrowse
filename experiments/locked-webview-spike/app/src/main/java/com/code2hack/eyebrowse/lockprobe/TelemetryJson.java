package com.code2hack.eyebrowse.lockprobe;

import org.json.JSONObject;

/**
 * JSON string encoding for telemetry records.
 *
 * <p>Telemetry is newline-delimited JSON that independent readers parse strictly, so every string
 * value must be a valid JSON string. {@link JSONObject#quote(String)} escapes the full required
 * U+0000-U+001F control range (including TAB, backspace, and form feed), quotes, and backslashes;
 * the previous hand-rolled encoder missed most control characters and produced invalid JSONL.
 */
final class TelemetryJson {

    private TelemetryJson() {
    }

    /**
     * Returns a JSON string literal, or the bare token {@code null} for a {@code null} input.
     */
    static String quote(String value) {
        return value == null ? "null" : JSONObject.quote(value);
    }
}
