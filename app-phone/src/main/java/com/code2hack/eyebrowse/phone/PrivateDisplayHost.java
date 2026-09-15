package com.code2hack.eyebrowse.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * The single private hosting display: a {@link VirtualDisplay} fed by an {@link ImageReader}
 * surface, showing the existing live WebView through a {@link android.app.Presentation}.
 *
 * <p>This is own-content-only private output on a virtual display, never physical-screen capture.
 *
 * <p><b>Ownership model (correction round 2):</b>
 * <ul>
 * <li><b>Bound delivery.</b> Each lease hands the capture pipeline an immutable bound sink
 * (lease token + hosting generation + consumer, created by the controller). Delivery is admitted
 * once per frame through the controller's lock-free {@link FrameGate} against that token.
 * Revocation prevents new admissions; an admitted frame may finish delivery to its own consumer
 * (in-flight borrowed use). The capture path never takes the controller monitor.</li>
 * <li><b>Native serialization.</b> Acquired Images are acquired, copied, hashed and closed inside
 * one {@code nativeLock}-serialized section on the capture path; consumers receive only the copied
 * borrowed bitmap, never a native image, and the image is closed before delivery. Reader
 * creation, swap and close share the same lock, so no close can invalidate a buffer mid-use, and
 * a superseded reader's stale callback is recognized and dropped. Main-thread rebuilds briefly
 * take {@code nativeLock}; the controller monitor is never held while {@code nativeLock} is
 * held, and delivery runs outside both.</li>
 * <li><b>Observable teardown.</b> Native capture resources are released on the capture path (a
 * posted teardown task, or inline when no capture thread exists) and the host object remains
 * reachable for introspection until that completion marker is set. Nothing joins while holding
 * the controller monitor.</li>
 * </ul>
 *
 * <p>Entry points other than the capture callback run on the main thread.
 */
final class PrivateDisplayHost {

    private static final String TAG = "EyeBrowseHosting";
    private static final String DISPLAY_NAME = "EyeBrowseHosting";

    /** Test seam: creates the platform resources so failure injection can exercise rollback. */
    interface Factory {
        VirtualDisplay createVirtualDisplay(DisplayManager manager, String name, int width,
                int height, int densityDpi, Object surface) throws RuntimeException;

        ImageReader createImageReader(int width, int height) throws RuntimeException;

        PresentationHost createPresentation(Context context, Display display)
                throws RuntimeException;
    }

    /** The shown presentation holding the container the WebView is attached to. */
    interface PresentationHost {
        void show();

        void dismiss();

        FrameLayout container();
    }

    /** Immutable per-lease delivery sink; the capture pipeline invokes exactly this object. */
    interface FrameSink {
        void onFrame(HostingFrame frame);
    }

    private final Factory factory;
    private final Object nativeLock = new Object();

    /** One capture owner at a time: ACTIVE → RETIRING → QUIESCENT is real and observable (R2). */
    private final CaptureOwnerPhase ownerPhase = new CaptureOwnerPhase();

    /**
     * One-shot callback fired when a requested retirement completes (on the completing thread;
     * the controller wraps it to hop to main). This is the safe-replacement signal: reacquisition
     * and restart wait for it instead of racing the outstanding teardown.
     */
    private Runnable onQuiesced;

    private VirtualDisplay virtualDisplay;      // main-thread only
    private PresentationHost presentation;      // main-thread only
    private ImageReader imageReader;            // nativeLock-protected
    private HandlerThread captureThread;        // main-thread lifecycle
    private Handler captureHandler;             // main-thread lifecycle
    private Thread retainedCaptureThread;       // latest capture thread, for isAlive() introspection
    private FrameSink frameSink;                // volatile: swapped from main, read on capture path
    private int width;
    private int height;
    private int densityDpi;

    // Capture-path state.
    private long frameSequence;
    private long lastDeliveryElapsedMs;
    private boolean deliveredAny;
    private Bitmap frameBitmap;                 // nativeLock-protected
    private volatile boolean captureActive;
    private volatile boolean captureReleased;   // release requested; no further admissions/copying
    private volatile boolean teardownComplete = true;
    private volatile int captureGeneration;     // hosting generation stamped into produced frames

    /**
     * The live output epoch (B): full live identity of the output cycle with its supported
     * visual-state -> window-submission stages. Superseded (REPLACED, not reset) on any relevant
     * output-state change; while the step-5 reader-correspondence link is unresolved the
     * consumer admission gate stays CLOSED and live-epoch images are consumed (acquire+close)
     * on the owning path — an explicitly incomplete, non-delivering state.
     */
    private volatile OutputEpoch currentEpoch;

    /** S5: guards the single bounded last-update continuation (no FIFO). */
    private boolean lastUpdateContinuationPending;

    /**
     * Production consumer admission gate (B step 5): CLOSED while no supported
     * window-submission -> reader-buffer correspondence rule exists. Opening it is a future,
     * separately reviewed change; it is not toggled by this checkpoint.
     */
    private volatile boolean consumerAdmissionOpen = false;

    /** One-shot visual-state request ids for {@code WebView.postVisualStateCallback}. */
    private long visualStateRequestCounter;

    PrivateDisplayHost(Factory factory, Runnable onQuiesced) {
        this.factory = factory;
        this.onQuiesced = onQuiesced;
    }

    /**
     * Begins (REPLACES) the live output epoch and starts the supported S2 chain on the main
     * thread. The previous epoch is invalidated as a whole by replacement — its stages never
     * complete retroactively (S3).
     *
     * <p>Chain: (1) {@code WebView.postVisualStateCallback} with full live-tuple revalidation on
     * completion — official guarantee: the NEXT draw reflects DOM state through the request point
     * (visibility/attachment conditions apply; video excluded); a document-readiness signal, not
     * rendered output. (2) A guarded OnPreDraw observer (registered legally; never removed inside
     * its own callback; inert after its single registration) registers
     * {@code ViewTreeObserver.registerFrameCommitCallback} for the upcoming traversal and the
     * view is invalidated to drive it. (3) The commit callback — hardware rendering rendered a
     * frame and SUBMITTED it to the window swap chain (API 29+; the frame need not be visible) —
     * revalidates the full live tuple and records WINDOW_SUBMITTED. Window submission is NOT
     * output-ready: the consumer gate remains closed (step 5 unresolved).
     */
    void beginOutputEpoch(OutputEpoch epoch, PhoneBrowserSession session) {
        currentEpoch = epoch;
        android.webkit.WebView view = session.view();
        if (view == null || view != epoch.viewRef()) {
            return; // No delivered view to link; the epoch stays unchained (nothing is admitted).
        }
        final OutputEpoch observedEpoch = epoch;
        visualStateRequestCounter++;
        final long requestId = visualStateRequestCounter;
        view.postVisualStateCallback(requestId, new android.webkit.WebView.VisualStateCallback() {
            @Override
            public void onComplete(long id) {
                if (id != requestId || currentEpoch != observedEpoch) {
                    return; // Superseded while pending: stages never complete retroactively (S3).
                }
                if (!observedEpoch.matchesLive(session.view(), currentPresentationRef(),
                        currentReaderRef(), observedEpoch.width(), observedEpoch.height(),
                        currentDensityDpi(), observedEpoch.hostingGeneration(),
                        session.outputStateVersion())) {
                    return; // Full live identity changed: this epoch is superseded (S3).
                }
                observedEpoch.markVisualStateCompleted(android.os.SystemClock.elapsedRealtime());
                Log.i(TAG, "visual state completed gen=" + observedEpoch.hostingGeneration()
                        + " eligible=" + observedEpoch.eligibleElapsedMs());
                registerWindowCommitObservation(observedEpoch, session);
            }
        });
    }

    /**
     * Registers the window-commit observation for the upcoming traversal (S1-legal lifetime): a
     * guarded OnPreDraw observer registers {@code registerFrameCommitCallback} for THAT pass and
     * then stays attached as an inert pass-through (it is never removed inside its own callback;
     * replacement happens at the next epoch begin). The traversal is driven by an ordinary
     * invalidate.
     */
    private void registerWindowCommitObservation(OutputEpoch epoch, PhoneBrowserSession session) {
        android.webkit.WebView view = session.view();
        if (view == null || view != epoch.viewRef() || currentEpoch != epoch) {
            return;
        }
        final OutputEpoch observedEpoch = epoch;
        final boolean[] commitRegistered = {false};
        android.view.ViewTreeObserver.OnPreDrawListener preDraw =
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (currentEpoch != observedEpoch || commitRegistered[0]) {
                            return true; // Inert pass-through after its single registration (S1).
                        }
                        if (!observedEpoch.isVisualStateCompleted()) {
                            return true; // Stage 1 must precede stage 2 registration.
                        }
                        commitRegistered[0] = true;
                        android.view.ViewTreeObserver observer = view.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return true;
                        }
                        observer.registerFrameCommitCallback(() -> {
                            // Hardware rendering submitted the frame to the window swap chain.
                            if (currentEpoch != observedEpoch
                                    || !observedEpoch.matchesLive(session.view(),
                                    currentPresentationRef(), currentReaderRef(),
                                    observedEpoch.width(), observedEpoch.height(),
                                    currentDensityDpi(), observedEpoch.hostingGeneration(),
                                    session.outputStateVersion())) {
                                return; // Superseded/full identity changed (S3).
                            }
                            observedEpoch.markWindowSubmitted(
                                    android.os.SystemClock.elapsedRealtime());
                            Log.i(TAG, "window frame submitted gen="
                                    + observedEpoch.hostingGeneration() + " eligible="
                                    + observedEpoch.eligibleElapsedMs()
                                    + " (WINDOW_SUBMITTED; consumer gate closed, step5 open)");
                        });
                        return true;
                    }
                };
        view.getViewTreeObserver().addOnPreDrawListener(preDraw);
        view.invalidate(); // Drive the traversal (ordinary; no content change, no reload).
    }

    /** Ends the live epoch: pending chain stages never complete and its callbacks no-op. */
    void supersedeOutputEpoch() {
        currentEpoch = null;
    }

    OutputEpoch currentEpoch() {
        return currentEpoch;
    }

    /** Live presenting-container identity for epoch binding (main thread). */
    Object currentPresentationRef() {
        return presentation;
    }

    /** Live reader identity for epoch binding. */
    Object currentReaderRef() {
        synchronized (nativeLock) {
            return imageReader;
        }
    }

    private int currentDensityDpi() {
        return densityDpi;
    }

    /** Sparse diagnostic facts for test-owned milestones (no content, no wire format). */
    String outputEpochFacts() {
        OutputEpoch epoch = currentEpoch;
        if (epoch == null) {
            return "epoch=none consumerGate=" + (consumerAdmissionOpen ? "open" : "closed");
        }
        return "epoch=" + System.identityHashCode(epoch)
                + " gen=" + epoch.hostingGeneration()
                + " visualState=" + epoch.isVisualStateCompleted()
                + " windowSubmitted=" + epoch.isWindowSubmitted()
                + " eligible=" + epoch.eligibleElapsedMs()
                + " consumerGate=" + (consumerAdmissionOpen ? "open" : "closed")
                + " " + epoch.width() + "x" + epoch.height();
    }

    /**
     * Creates the display, presentation and reader for the measured viewport. Recoverable
     * platform failures are converted to {@link HostingException} after rolling back the partial
     * allocations; the caller records the failure. Throws {@link HostingException} otherwise.
     */
    void create(Context serviceContext, int measuredWidth, int measuredHeight, int measuredDensityDpi)
            throws HostingException {
        String sizeError = HostingPolicy.viewportError(measuredWidth, measuredHeight);
        if (sizeError != null) {
            throw new HostingException(sizeError);
        }
        this.width = measuredWidth;
        this.height = measuredHeight;
        this.densityDpi = measuredDensityDpi;
        try {
            imageReader = factory.createImageReader(width, height);
            if (imageReader == null) {
                throw new HostingException("image reader creation failed");
            }
            DisplayManager displayManager =
                    (DisplayManager) serviceContext.getSystemService(Context.DISPLAY_SERVICE);
            virtualDisplay = factory.createVirtualDisplay(displayManager, DISPLAY_NAME, width,
                    height, measuredDensityDpi, imageReader.getSurface());
            if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
                throw new HostingException("virtual display creation failed");
            }
            presentation = factory.createPresentation(serviceContext, virtualDisplay.getDisplay());
            presentation.show();
        } catch (HostingException error) {
            rollbackDisplayAllocation();
            throw error;
        } catch (RuntimeException error) {
            rollbackDisplayAllocation();
            throw new HostingException("platform allocation failed: " + error.getMessage());
        }
    }

    /** Rolls back whatever subset of display resources was already allocated. */
    private void rollbackDisplayAllocation() {
        synchronized (nativeLock) {
            if (imageReader != null) {
                try {
                    imageReader.close();
                } catch (RuntimeException ignored) {
                    // Rollback best effort; no capture thread exists yet.
                }
                imageReader = null;
            }
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (RuntimeException ignored) {
                // Rollback best effort.
            }
            presentation = null;
        }
    }

    /** Attaches the live session WebView into the presentation container. */
    void attachSessionView(PhoneBrowserSession session) {
        if (presentation == null || session.view() == null) {
            return;
        }
        session.attachExternal(presentation.container(), presentation.container().getContext());
    }

    /** Moves the session WebView out of the presentation container (stays alive, parentless). */
    void detachSessionView(PhoneBrowserSession session) {
        if (presentation != null) {
            session.detachExternal(presentation.container());
        }
    }

    /**
     * Ensures a capture surface for {@code desiredWidth}×{@code desiredHeight}×
     * {@code desiredDensityDpi}: a matching live reader is kept, a missing reader is created on
     * the surviving display, and any differing geometry (including density) triggers a full
     * display/presentation rebuild without navigation. A previously private view is restored to
     * the new container before return; a Phone-owned view is not stolen. The caller rearms its
     * live lease and explicitly attaches when transitioning from Phone UI. Throws on failure.
     */
    void ensureCaptureSurface(Context serviceContext, int desiredWidth, int desiredHeight,
            int desiredDensityDpi, PhoneBrowserSession session) throws HostingException {
        String sizeError = HostingPolicy.viewportError(desiredWidth, desiredHeight);
        if (sizeError != null) {
            throw new HostingException(sizeError);
        }
        synchronized (nativeLock) {
            if (imageReader != null && width == desiredWidth && height == desiredHeight
                    && densityDpi == desiredDensityDpi) {
                return;
            }
        }
        if (imageReader == null && width == desiredWidth && height == desiredHeight
                && densityDpi == desiredDensityDpi) {
            // Same geometry, surface recreated after an idle release — no presentation change.
            // Same recoverable allocation path as initial creation (R7).
            ImageReader reader;
            try {
                reader = factory.createImageReader(width, height);
            } catch (RuntimeException error) {
                throw new HostingException("reader recreation failed: " + error.getMessage());
            }
            if (reader == null) {
                throw new HostingException("image reader creation failed");
            }
            synchronized (nativeLock) {
                try {
                    virtualDisplay.setSurface(reader.getSurface());
                } catch (RuntimeException error) {
                    reader.close();
                    throw new HostingException("surface reattach failed: " + error.getMessage());
                }
                imageReader = reader;
            }
            return;
        }
        // Differing geometry or density: reconcile by rebuilding the private
        // display/presentation/reader, without navigation (R6).
        rebuildAtSize(serviceContext, desiredWidth, desiredHeight, desiredDensityDpi, session);
    }

    /** Full rebuild of display, presentation and reader at a new measured geometry. */
    private void rebuildAtSize(Context serviceContext, int newWidth, int newHeight,
            int newDensityDpi, PhoneBrowserSession session) throws HostingException {
        boolean restorePrivateAttachment = presentation != null && session.view() != null
                && session.view().getParent() == presentation.container();
        detachSessionView(session); // The live view leaves the old container; the document stays.
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (RuntimeException ignored) {
                // Teardown continues.
            }
            presentation = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        synchronized (nativeLock) {
            if (imageReader != null) {
                try {
                    imageReader.close();
                } catch (RuntimeException ignored) {
                    // Serialized with any in-flight capture-path use; safe to close here.
                }
                imageReader = null;
            }
        }
        create(serviceContext, newWidth, newHeight, newDensityDpi);
        if (restorePrivateAttachment) {
            // First demand may rebuild after a demand-free window change. Capture must see the
            // same live view in the NEW presentation, not merely a correctly sized blank reader.
            attachSessionView(session);
        }
    }

    /**
     * Rearms frame production for the SAME live capture owner on the current reader (R6): legal
     * only while this owner is ACTIVE with an open reader and a live capture path. Used after a
     * geometry rebuild replaced the reader underneath an existing lease. Returns {@code false}
     * when the owner is not actively capturable — the caller must surface that, never display a
     * healthy capturing state over an unarmed reader.
     */
    boolean rearmCapture(int hostingGeneration, FrameSink boundSink, OutputEpoch epoch) {
        if (!ownerPhase.isActive() || imageReader == null || boundSink == null
                || captureThread == null || captureHandler == null) {
            return false;
        }
        if (epoch == null || imageReader != epoch.readerRef()) {
            return false; // The epoch must bind the exact reader being rearmed (B).
        }
        frameSink = boundSink;
        frameSequence = 0;
        deliveredAny = false;
        captureGeneration = hostingGeneration;
        captureActive = true;
        captureReleased = false;
        currentEpoch = epoch;
        // Listener identity bound at REGISTRATION to this epoch+sink (B): a stale callback can
        // never select a successor's epoch/sink, whatever executes later.
        imageReader.setOnImageAvailableListener(
                (reader) -> onImageAvailableForEpoch(epoch, boundSink, reader), captureHandler);
        scheduleDrain(epoch);
        Log.i(TAG, "rearmCapture gen=" + hostingGeneration + " on rebuilt reader");
        return true;
    }

    /**
     * Starts (or restarts) frame production for a NEW capture owner through the caller's bound
     * sink; latest-only and throttled. The sink is retained as-is: delivery identity lives in the
     * sink, not in a reassigned callback.
     *
     * <p>Returns {@code false} when no capture surface exists or a previous capture owner is
     * still retiring (R2): reacquisition waits for quiescence instead of racing the outstanding
     * teardown. The caller retries explicitly after quiescence — a null acquisition has no
     * hidden side effects.
     */
    boolean startCapture(int hostingGeneration, FrameSink boundSink, OutputEpoch epoch) {
        if (imageReader == null || boundSink == null) {
            return false;
        }
        if (!ownerPhase.beginActive()) {
            Log.i(TAG, "startCapture deferred: owner phase=" + ownerPhase.phase());
            return false;
        }
        if (epoch == null || imageReader != epoch.readerRef()) {
            return false; // The epoch must bind the exact reader being started (B).
        }
        frameSink = boundSink;
        frameSequence = 0;
        deliveredAny = false;
        captureGeneration = hostingGeneration;
        captureActive = true;
        captureReleased = false;
        // NOTE: currentEpoch is bound by beginOutputEpoch (the S2 chain start), not here (S3).
        Log.i(TAG, "startCapture gen=" + hostingGeneration + " reader=" + (imageReader != null)
                + " threadAlive=" + (captureThread != null));
        // Listener identity bound at REGISTRATION to this epoch+sink (B).
        java.util.function.BiConsumer<OutputEpoch, ImageReader> boundListener =
                (boundEpoch, reader) -> onImageAvailableForEpoch(boundEpoch, boundSink, reader);
        if (captureThread != null) {
            // Reacquisition after lease loss: the capture thread survived; rearm the listener.
            imageReader.setOnImageAvailableListener(
                    (reader) -> onImageAvailableForEpoch(epoch, boundSink, reader), captureHandler);
            scheduleDrain(epoch);
            return true;
        }
        captureThread = new HandlerThread("EyeBrowseHostingCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        retainedCaptureThread = captureThread;
        imageReader.setOnImageAvailableListener(
                (reader) -> onImageAvailableForEpoch(epoch, boundSink, reader), captureHandler);
        scheduleDrain(epoch);
        return true;
    }

    /**
     * Images queued before the listener was armed do not reliably fire the callback; acquire and
     * close them so the producer cannot stay blocked on a full (maxImages=2) queue and stale
     * frames are dropped, latest-only. Runs on the capture handler, serialized with callback
     * acquisitions, and is bounded.
     */
    /** Posts an epoch-BOUND drain: the task validates its originating epoch, not entry-time state. */
    private void scheduleDrain(OutputEpoch epoch) {
        if (captureHandler != null && epoch != null) {
            captureHandler.post(() -> drainPendingImages(epoch));
        }
    }

    /** Drains queued initialization images for the ORIGINATING epoch only (bounded). */
    private void drainPendingImages(OutputEpoch boundEpoch) {
        final int maxDrain = 8; // maxImages=2 plus settling headroom; bounded by construction.
        int drained = 0;
        while (drained < maxDrain) {
            Image stale;
            synchronized (nativeLock) {
                if (imageReader == null || boundEpoch == null
                        || imageReader != boundEpoch.readerRef()
                        || currentEpoch != boundEpoch) {
                    break; // Superseded: never select/drain a successor's reader (B).
                }
                stale = imageReader.acquireLatestImage();
                if (stale == null) {
                    break;
                }
            }
            try {
                drained++;
            } finally {
                stale.close(); // Initialization/preparation output: discarded, never delivered.
            }
        }
        if (drained > 0) {
            Log.i(TAG, "drained " + drained + " pre-readiness capture image(s)");
        }
    }

    /**
     * Stops frame delivery for revocation; the capture owner (thread/reader) stays retained for
     * same-owner reacquisition via {@link #rearmCapture(int, FrameSink)}. Native resources leave
     * only through the owning retirement path.
     */
    void stopCapture() {
        supersedeOutputEpoch(); // Revocation ends the epoch: pending readiness never completes.
        captureActive = false;
        frameSink = null;
    }

    boolean isCapturing() {
        return captureActive && hasReader();
    }

    /**
     * Releases the capture reader/surface/thread as one coherent ownership transition (R2): the
     * owner enters {@code RETIRING} synchronously (no replacement capture can start), the
     * retiring reader/bitmap are snapshotted and the current fields released, and the retirement
     * closes only those snapshots on the capture path. With a live capture thread the native
     * close executes on that thread after all pending callbacks (serialized by nativeLock and the
     * handler queue); without one it executes inline. Nothing joins under a controller monitor;
     * quiescence is observable via {@link #isQuiescent()} and the quiescence callback.
     */
    void releaseCaptureResources() {
        supersedeOutputEpoch(); // Pending readiness of this owner never completes (B).
        if (ownerPhase.isRetiring()) {
            return; // Retirement already requested and outstanding; do not re-post.
        }
        if (ownerPhase.isActive() && !ownerPhase.beginRetiring()) {
            return; // Concurrent transition; treat as already retiring.
        }
        // ACTIVE enters RETIRING above; IDLE/QUIESCENT hosts only residual resources (e.g. the
        // never-leased reader) and releases them inline without a phase transition.
        captureActive = false;
        frameSink = null;
        captureReleased = true;
        teardownComplete = false;
        final ImageReader retiringReader;
        final Bitmap retiringBitmap;
        synchronized (nativeLock) {
            retiringReader = imageReader;
            retiringBitmap = frameBitmap;
            // Current fields release now: introspection sees no live reader and any later
            // allocation creates fresh resources the old teardown will never touch.
            imageReader = null;
            frameBitmap = null;
        }
        if (captureThread != null && captureHandler != null) {
            final HandlerThread retiringThread = captureThread;
            final Handler retiringHandler = captureHandler;
            retiringHandler.post(() -> finishRetirement(retiringReader, retiringBitmap,
                    retiringThread));
            retiringThread.quitSafely(); // Pending callbacks and the teardown task run first.
            captureThread = null;
            captureHandler = null;
        } else {
            finishRetirement(retiringReader, retiringBitmap, null); // No capture path; inline.
        }
    }

    /**
     * The owning teardown close: retires only the snapshot references of the retiring owner
     * (never the current mutable fields a replacement may already use) and marks the teardown
     * task complete. Phase QUIESCENT is NOT set here: with a live capture thread this executes
     * on that thread BEFORE it exits, so quiescence is declared only later, on the main thread,
     * once the thread has actually terminated ({@link #evaluateRetirementCompletion()}).
     */
    private void finishRetirement(ImageReader retiringReader, Bitmap retiringBitmap,
            HandlerThread retiringThread) {
        if (retiringReader != null) {
            try {
                retiringReader.close();
            } catch (RuntimeException ignored) {
                // Serialized with capture-path use; a platform refusal must not block teardown.
            }
        }
        // The retiring borrowed bitmap is dropped without recycle (a completed delivery may
        // still hold it); reclamation stays with GC, which is safe for borrowed bitmaps.
        teardownComplete = true;
        Runnable callback = onQuiesced;
        if (callback != null) {
            callback.run(); // Signals the controller to evaluate completion on the main thread.
        }
    }

    /**
     * Main-thread retirement completion check: declares the owner QUIESCENT only when the
     * teardown task has completed AND the retiring capture thread has actually exited — a
     * still-running thread is never quiescent (R2). Fires the quiescence callback once on the
     * transition. Returns true when this call reached quiescence.
     */
    boolean evaluateRetirementCompletion() {
        if (ownerPhase.phase() != CaptureOwnerPhase.Phase.RETIRING || !teardownComplete) {
            return false;
        }
        Thread thread = retainedCaptureThread;
        if (thread != null && thread.isAlive()) {
            return false; // The retiring owner's thread is still terminating.
        }
        if (ownerPhase.completeRetirement()) {
            Log.i(TAG, "capture retirement quiescent");
            Runnable callback = onQuiesced;
            if (callback != null) {
                callback.run();
            }
            return true;
        }
        return false;
    }

    /** Full teardown for Stop: detaches the session view, dismisses, releases the display. */
    void release(PhoneBrowserSession session) {
        stopCapture();
        if (session != null) {
            detachSessionView(session);
        }
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (RuntimeException ignored) {
                // A dismissed presentation must not block teardown.
            }
            presentation = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        releaseCaptureResources();
    }

    // Resource introspection used by tests and cleanup evidence.

    boolean hasDisplay() {
        return virtualDisplay != null;
    }

    boolean hasPresentation() {
        return presentation != null;
    }

    boolean hasReader() {
        synchronized (nativeLock) {
            return imageReader != null;
        }
    }

    /**
     * True while any live capture resource remains: an open reader, a still-running capture
     * thread, or teardown that has been requested but has not yet completed. Never reports a
     * phantom resource for capture that never started (thread liveness is observed, not inferred
     * from flags).
     */
    boolean hasLiveCaptureResources() {
        if (hasReader()) {
            return true;
        }
        Thread thread = retainedCaptureThread;
        if (thread != null && thread.isAlive()) {
            return true;
        }
        return !teardownComplete;
    }

    /** Completion marker for the requested capture teardown. */
    boolean isTeardownComplete() {
        return teardownComplete;
    }

    /** True when a previous capture owner is still retiring (R2). */
    boolean isRetiring() {
        return ownerPhase.isRetiring();
    }

    /** True when replacement capture ownership is safe (no outstanding teardown). */
    boolean isQuiescent() {
        return ownerPhase.isQuiescent() && teardownComplete;
    }

    /** True while a capture owner is live (frame production possible). */
    boolean isOwnerActive() {
        return ownerPhase.isActive();
    }

    /**
     * Epoch-bound capture callback (B): the epoch and sink were bound at listener REGISTRATION;
     * admission goes through the pure {@link EpochAdmission} decision against the host's live
     * state, and the epoch is rechecked immediately before delivery.
     */
    private void onImageAvailableForEpoch(OutputEpoch boundEpoch, FrameSink boundSink,
            ImageReader reader) {
        final FrameSink sink = boundSink; // Registration identity, not entry-time mutable state.
        final OutputEpoch epoch = boundEpoch;
        OutputEpoch liveEpoch;
        Object liveReader;
        boolean active;
        boolean released;
        synchronized (nativeLock) {
            liveEpoch = currentEpoch;
            liveReader = imageReader;
            active = captureActive;
            released = captureReleased;
        }
        EpochAdmission.Decision decision = EpochAdmission.evaluate(active, released, liveReader,
                liveEpoch, epoch, reader, consumerAdmissionOpen, deliveredAny,
                lastDeliveryElapsedMs, SystemClock.elapsedRealtime());
        if (decision == EpochAdmission.Decision.DISCARD_STALE) {
            return; // Orphaned output of a retired cycle: the reader may belong to a successor.
        }
        // CONSUME_UNREADY / RECORD_CANDIDATE / DELIVER all acquire the newest image on the
        // owning path: bounded queue progress (the producer is never left to fill maxImages).
        HostingFrame frame = null;
        boolean consumeOnly = decision == EpochAdmission.Decision.CONSUME_UNREADY;
        synchronized (nativeLock) {
            if (captureReleased || reader != imageReader || reader != epoch.readerRef()
                    || currentEpoch != epoch) {
                return; // Recheck under the lock: state moved between decision and acquire.
            }
            Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            long nowElapsed = SystemClock.elapsedRealtime();
            try {
                if (consumeOnly) {
                    // S2 chain incomplete: preparation output stays internal (acquire+close).
                    return;
                }
                deliveredAny = true;
                lastDeliveryElapsedMs = nowElapsed;
                frame = copyFrame(image, nowElapsed);
            } catch (RuntimeException error) {
                Log.w(TAG, "hosting frame capture failed", error);
            } finally {
                image.close(); // The native image never escapes the copy scope.
            }
        }
        if (frame == null || currentEpoch != epoch) {
            return; // Superseded between copy and delivery: never retag onto a successor (B).
        }
        if (decision == EpochAdmission.Decision.RECORD_CANDIDATE) {
            scheduleLastUpdateContinuation(epoch, sink, frame, frame.captureElapsedMs);
            return; // Throttled: the candidate is delivered by the bounded continuation (S5).
        }
        // DELIVER: admission and consumer invocation happen outside nativeLock; the sink's bound
        // identity fences superseded leases without taking any monitor.
        sink.onFrame(frame);
    }

    /**
     * S5: one bounded scheduled continuation that delivers the LAST copied candidate when the
     * throttle window expires — the final update is eventually delivered without a FIFO. The
     * continuation revalidates epoch currency, capture liveness and the consumer gate, and is
     * skipped when a newer frame was already delivered after the candidate was copied.
     */
    private void scheduleLastUpdateContinuation(OutputEpoch epoch, FrameSink sink,
            HostingFrame candidate, long candidateElapsedMs) {
        if (lastUpdateContinuationPending) {
            return; // One bounded continuation at a time (no FIFO).
        }
        long now = SystemClock.elapsedRealtime();
        long delay = Math.max(1, HostingPolicy.MIN_FRAME_INTERVAL_MS
                - (now - lastDeliveryElapsedMs));
        lastUpdateContinuationPending = true;
        if (captureHandler != null) {
            captureHandler.postDelayed(() -> {
                lastUpdateContinuationPending = false;
                FrameSink currentSink;
                boolean active;
                synchronized (nativeLock) {
                    currentSink = frameSink;
                    active = captureActive;
                }
                if (!active || currentSink != sink || currentEpoch != epoch
                        || !consumerAdmissionOpen
                        || lastDeliveryElapsedMs > candidateElapsedMs) {
                    return; // Superseded / newer frame already delivered: the candidate is stale.
                }
                long stamp = SystemClock.elapsedRealtime();
                deliveredAny = true;
                lastDeliveryElapsedMs = stamp;
                sink.onFrame(candidate); // Eventual delivery of the last update (S5).
            }, delay);
        } else {
            lastUpdateContinuationPending = false;
        }
    }

    /** Copies the acquired image into the reused borrowed bitmap (nativeLock held by caller). */
    private HostingFrame copyFrame(Image image, long captureElapsedMs) {
        Image.Plane plane = image.getPlanes()[0];
        int rowStride = plane.getRowStride();
        int rowBytes = width * plane.getPixelStride();
        ByteBuffer packed = rowStride == rowBytes
                ? plane.getBuffer()
                : packedRowCopy(plane, height, rowStride, rowBytes);
        if (packed == null) {
            return null;
        }
        if (frameBitmap == null || frameBitmap.getWidth() != width
                || frameBitmap.getHeight() != height) {
            if (frameBitmap != null) {
                frameBitmap.recycle();
            }
            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        packed.rewind();
        frameBitmap.copyPixelsFromBuffer(packed);
        return new HostingFrame(frameBitmap, width, height, captureGeneration, ++frameSequence,
                captureElapsedMs, contentHash(plane));
    }

    /** Packs a stride-padded image plane into tight rows for {@code copyPixelsFromBuffer}. */
    private static ByteBuffer packedRowCopy(Image.Plane plane, int rows, int rowStride,
            int rowBytes) {
        ByteBuffer source = plane.getBuffer().duplicate();
        return packRows(source, source.limit(), rows, rowStride, rowBytes);
    }

    /**
     * Pure row-packing core (JVM-testable): copies {@code rows} tight {@code rowBytes} rows from a
     * stride-padded buffer of declared {@code sourceLimit} into a fresh direct buffer. The limit is
     * captured by the caller because per-row slicing mutates the working buffer's limit.
     */
    static ByteBuffer packRows(ByteBuffer source, int sourceLimit, int rows, int rowStride,
            int rowBytes) {
        ByteBuffer packed = ByteBuffer.allocateDirect(rowBytes * rows);
        for (int y = 0; y < rows; y++) {
            int rowStart = y * rowStride;
            if (rowStart + rowBytes > sourceLimit) {
                break; // Buffer holds a short tail row; packed output stays smaller than requested.
            }
            source.limit(rowStart + rowBytes).position(rowStart);
            packed.put(source);
        }
        packed.flip();
        return packed;
    }

    /** CRC32 over every valid pixel byte, excluding row padding, so stale frames are detectable. */
    private long contentHash(Image.Plane plane) {
        CRC32 crc = new CRC32();
        ByteBuffer source = plane.getBuffer().duplicate();
        int rowStride = plane.getRowStride();
        int rowBytes = width * plane.getPixelStride();
        for (int y = 0; y < height; y++) {
            int rowStart = y * rowStride;
            if (rowStart + rowBytes > source.limit()) {
                break;
            }
            ByteBuffer row = source.duplicate();
            row.position(rowStart);
            row.limit(rowStart + rowBytes);
            crc.update(row);
        }
        return crc.getValue();
    }

    /** Real platform resources; test failure injection substitutes this factory. */
    static final class PlatformFactory implements Factory {

        @Override
        public VirtualDisplay createVirtualDisplay(DisplayManager manager, String name, int width,
                int height, int densityDpi, Object surface) {
            return manager.createVirtualDisplay(name, width, height, densityDpi,
                    (android.view.Surface) surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
        }

        @Override
        public ImageReader createImageReader(int width, int height) {
            return ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2);
        }

        @Override
        public PresentationHost createPresentation(Context context, Display display) {
            return new HostingPresentation(context, display);
        }
    }

    private static final class HostingPresentation extends android.app.Presentation
            implements PresentationHost {

        private final FrameLayout container;

        HostingPresentation(Context context, Display display) {
            super(context, display);
            container = new FrameLayout(context);
            container.setBackgroundColor(Color.WHITE);
        }

        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override
        public FrameLayout container() {
            return container;
        }
    }
}
