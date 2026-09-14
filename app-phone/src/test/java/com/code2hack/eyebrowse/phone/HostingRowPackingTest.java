package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Regression for the hosting row-packing core: a stride-padded plane must pack EVERY row's valid
 * pixels (the original defect compared row bounds against a per-row-mutated buffer limit and
 * packed only the first row, failing Bitmap.copyPixelsFromBuffer with "Buffer not large enough").
 */
public class HostingRowPackingTest {

    private static ByteBuffer plane(int rowStride, int rows, int fill) {
        ByteBuffer plane = ByteBuffer.allocateDirect(rowStride * rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < rowStride; x++) {
                plane.put((byte) (fill + y + x));
            }
        }
        plane.flip();
        return plane;
    }

    @Test
    public void packsEveryRowOfPaddedMultiRowPlane() {
        int rows = 7;
        int rowStride = 64; // Padded: rowBytes < rowStride.
        int rowBytes = 50;
        ByteBuffer source = plane(rowStride, rows, 1);
        ByteBuffer packed = PrivateDisplayHost.packRows(source, source.limit(), rows, rowStride,
                rowBytes);
        assertNotNull(packed);
        assertEquals("every row's valid pixels are packed", rowBytes * rows, packed.remaining());

        // Spot-check content: each packed row equals the source row's valid prefix.
        ByteBuffer sourceView = plane(rowStride, rows, 1);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < rowBytes; x++) {
                assertEquals("packed[" + y + "][" + x + "]", sourceView.get(y * rowStride + x),
                        packed.get(y * rowBytes + x));
            }
        }
    }

    @Test
    public void packsExactStridePlanesWithoutPadding() {
        int rows = 3;
        int rowStride = 16;
        ByteBuffer source = plane(rowStride, rows, 9);
        ByteBuffer packed = PrivateDisplayHost.packRows(source, source.limit(), rows, rowStride,
                rowStride);
        assertEquals(rowStride * rows, packed.remaining());
    }

    @Test
    public void shortTailRowBoundsThePackedOutput() {
        // A plane whose declared limit cannot hold the last row's full width yields a shorter
        // packed buffer instead of over-reading; the caller treats a size mismatch as a failure.
        int rows = 2;
        int rowStride = 32;
        int rowBytes = 30;
        ByteBuffer source = plane(rowStride, rows, 3);
        int shortLimit = rowStride + 10; // Second row holds only 10 of 30 valid bytes.
        ByteBuffer packed = PrivateDisplayHost.packRows(source, shortLimit, rows, rowStride,
                rowBytes);
        assertEquals(rowBytes, packed.remaining()); // First row only.
    }

    @Test
    public void capturesLimitBeforePerRowMutation() {
        // The caller captures the limit BEFORE the loop; even a pre-sliced working buffer whose
        // limit was mutated must not shrink the packing (the original defect's shape).
        int rows = 4;
        int rowStride = 48;
        int rowBytes = 40;
        ByteBuffer source = plane(rowStride, rows, 5);
        int originalLimit = source.limit();
        source.limit(rowBytes).position(0); // Simulated prior mutation of the working buffer.
        ByteBuffer packed = PrivateDisplayHost.packRows(source, originalLimit, rows, rowStride,
                rowBytes);
        assertEquals(rowBytes * rows, packed.remaining());
    }
}
