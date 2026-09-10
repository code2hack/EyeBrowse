package com.code2hack.eyebrowse.lockprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Regression tests for telemetry string encoding.
 *
 * <p>The original encoder escaped quotes, backslashes, CR, and LF only, so a tab or other
 * U+0000-U+001F character produced invalid JSONL. These tests round-trip the encoded literal
 * through a strict JSON parser and reject any raw control character in the output.
 */
public final class TelemetryJsonTest {

    private static String roundTrip(String input) throws Exception {
        String encoded = TelemetryJson.quote(input);
        return new JSONObject("{\"v\":" + encoded + "}").getString("v");
    }

    private static void assertRoundTrip(String input) throws Exception {
        String encoded = TelemetryJson.quote(input);
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            assertFalse(
                    "raw control character 0x" + Integer.toHexString(c) + " in " + encoded,
                    c < 0x20);
        }
        assertEquals(input, roundTrip(input));
    }

    @Test
    public void quotesBackslashesAndMultiline() throws Exception {
        assertRoundTrip("say \"hi\" \\ done\nsecond line\r\nthird\tline");
    }

    @Test
    public void tabBackspaceFormFeed() throws Exception {
        assertRoundTrip("left\tright\b\f");
    }

    @Test
    public void fullControlRange() throws Exception {
        for (char c = 0; c <= 0x1f; c++) {
            assertRoundTrip("a" + c + "b");
        }
    }

    @Test
    public void unicodeAndLineSeparators() throws Exception {
        assertRoundTrip("emoji \uD83D\uDE00 and separators \u2028\u2029");
    }

    @Test
    public void emptyAndNull() throws Exception {
        assertRoundTrip("");
        assertEquals("null", TelemetryJson.quote(null));
    }

    @Test
    public void commandRecordStaysValidJsonl() throws Exception {
        String value = "x\ty\nz\"q\\";
        String line = "{\"type\":\"command\",\"value\":" + TelemetryJson.quote(value) + "}";
        assertEquals(value, new JSONObject(line).getString("value"));
    }
}
