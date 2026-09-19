package com.code2hack.eyebrowse.phone;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-owned milestone sink (correction round 2): per-execution retention with explicit
 * capture-failure reporting. Lives in the androidTest sources only — production keeps plain
 * {@code Log.i} metadata and no evidence subsystem. One sink instance covers one instrumentation
 * execution: milestones are kept in memory (never lost to logcat rotation), mirrored to a
 * per-execution file under the instrumentation's own storage, and flushed to the results stream
 * at test end. Write failures are counted and reported, never silent.
 */
final class MilestoneSink {

    private final List<String> milestones = new ArrayList<>();
    private final File file;
    private final String executionId;
    private int writeFailures;

    MilestoneSink(Context instrumentationContext, long startedWallMs) {
        executionId = "run-" + startedWallMs;
        File dir = new File(instrumentationContext.getFilesDir(), "hosting-milestones");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = instrumentationContext.getFilesDir();
        }
        file = new File(dir, executionId + ".log");
        record("milestone-sink opened " + executionId);
    }

    /** Records a milestone in memory and mirrors it to the per-execution file. */
    synchronized void record(String line) {
        String stamped = SystemClock.elapsedRealtime() + " " + line;
        milestones.add(stamped);
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(stamped + "\n");
        } catch (IOException error) {
            writeFailures++;
            System.out.println("MILESTONE_WRITE_FAILURE count=" + writeFailures + " " + error);
        }
    }

    /** Failure observers use memory-only retention: no file/network wait inside their deadline. */
    synchronized void recordDeferred(String line) {
        milestones.add(SystemClock.elapsedRealtime() + " deferred " + line);
    }

    /** Flushes every retained milestone into the instrumentation results stream. */
    synchronized void flushToStream(String label) {
        System.out.println("MILESTONE_SINK_BEGIN " + label + " entries=" + milestones.size()
                + " writeFailures=" + writeFailures + " file=" + file.getAbsolutePath());
        for (String line : milestones) {
            System.out.println("MILESTONE " + line);
        }
        System.out.println("MILESTONE_SINK_END");
    }

    synchronized int failureCount() {
        return writeFailures;
    }

    synchronized String filePath() {
        return file.getAbsolutePath();
    }
}
