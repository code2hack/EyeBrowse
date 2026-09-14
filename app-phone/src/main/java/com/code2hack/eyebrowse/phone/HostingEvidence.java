package com.code2hack.eyebrowse.phone;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * App-scoped milestone sink for hosting evidence: state transitions, clock, resource and timing
 * milestones are persisted while produced so rotating shared logcat buffers cannot discard them
 * mid-run. The file lives in the application's private storage, carries no page content, and is
 * truncated at each hosting start to stay bounded. Instrumentation reads it back in-process.
 */
final class HostingEvidence {

    private static final String FILE_NAME = "hosting-evidence.log";
    private static final long MAX_BYTES = 256 * 1024;

    private static volatile File file;

    private HostingEvidence() {
    }

    /** Binds the sink to the application's private storage and truncates prior evidence. */
    static synchronized void startNew(Context appContext) {
        file = new File(appContext.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write("start " + System.currentTimeMillis() + "\n");
        } catch (IOException ignored) {
            file = null; // Evidence is best-effort; hosting never depends on it.
        }
    }

    static synchronized void log(String line) {
        File target = file;
        if (target == null) {
            return;
        }
        if (target.length() > MAX_BYTES) {
            return; // Bounded: further milestones are dropped rather than growing unbounded.
        }
        try (FileWriter writer = new FileWriter(target, true)) {
            writer.write(SystemClock.elapsedRealtime() + " " + line + "\n");
        } catch (IOException ignored) {
            // Best-effort evidence only.
        }
    }
}
