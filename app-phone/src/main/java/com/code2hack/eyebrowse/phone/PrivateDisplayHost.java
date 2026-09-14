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

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * The single private hosting display: a {@link VirtualDisplay} fed by an {@link ImageReader}
 * surface, showing the existing live WebView through a {@link android.app.Presentation}.
 *
 * <p>This is own-content-only private output on a virtual display, never physical-screen capture.
 * The host owns the capture thread and the latest-only frame production (ImageReader maxImages=2,
 * acquired images closed immediately, delivery throttled to the policy's frame interval). Idle
 * release drops the reader surface and thread while the display/presentation attachment survives;
 * a new surface can be attached later on the same display without any navigation.
 *
 * <p>All entry points run on the main thread; frame callbacks arrive on the capture thread.
 */
final class PrivateDisplayHost {

    private static final String TAG = "EyeBrowseHosting";
    private static final String DISPLAY_NAME = "EyeBrowseHosting";

    /** Test seam: creates the platform resources so failure injection can exercise rollback. */
    interface Factory {
        VirtualDisplay createVirtualDisplay(DisplayManager manager, String name, int width,
                int height, int densityDpi, Object surface);

        ImageReader createImageReader(int width, int height);

        PresentationHost createPresentation(Context context, Display display);
    }

    /** The shown presentation holding the container the WebView is attached to. */
    interface PresentationHost {
        void show();

        void dismiss();

        FrameLayout container();
    }

    /** Receives frames on the capture thread while the lease is live. */
    interface FrameSink {
        void onFrame(HostingFrame frame);
    }

    private final Factory factory;

    private VirtualDisplay virtualDisplay;
    private PresentationHost presentation;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private FrameSink frameSink;
    private int width;
    private int height;

    // Capture-side state, touched on the capture thread.
    private long frameSequence;
    private long lastDeliveryElapsedMs;
    private boolean deliveredAny;
    private volatile boolean capturing;
    private volatile int generation;
    private Bitmap frameBitmap;

    PrivateDisplayHost(Factory factory) {
        this.factory = factory;
    }

    /**
     * Creates the display, presentation and reader for the measured viewport. Throws {@link
     * HostingException} with the failure reason; the caller rolls back partial resources.
     */
    void create(Context serviceContext, int measuredWidth, int measuredHeight, int measuredDensityDpi)
            throws HostingException {
        String sizeError = HostingPolicy.viewportError(measuredWidth, measuredHeight);
        if (sizeError != null) {
            throw new HostingException(sizeError);
        }
        this.width = measuredWidth;
        this.height = measuredHeight;

        imageReader = factory.createImageReader(width, height);
        if (imageReader == null) {
            throw new HostingException("image reader creation failed");
        }

        DisplayManager displayManager =
                (DisplayManager) serviceContext.getSystemService(Context.DISPLAY_SERVICE);
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
        virtualDisplay = factory.createVirtualDisplay(displayManager, DISPLAY_NAME, width, height,
                measuredDensityDpi, imageReader.getSurface());
        if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
            throw new HostingException("virtual display creation failed");
        }

        try {
            presentation = factory.createPresentation(serviceContext, virtualDisplay.getDisplay());
            presentation.show();
        } catch (RuntimeException error) {
            throw new HostingException("presentation: " + error.getMessage());
        }
    }

    /** Attaches the live session WebView into the presentation container. */
    void attachSessionView(PhoneBrowserSession session) {
        if (presentation == null || session.view() == null) {
            return;
        }
        session.attachExternal(presentation.container(), presentationContainerContext());
    }

    private Context presentationContainerContext() {
        return presentation.container().getContext();
    }

    /** Moves the session WebView out of the presentation container (stays alive, parentless). */
    void detachSessionView(PhoneBrowserSession session) {
        if (presentation != null) {
            session.detachExternal(presentation.container());
        }
    }

    boolean isHostingSessionView(PhoneBrowserSession session) {
        return presentation != null && session.isAttachedExternal(presentation.container());
    }

    /** Starts (or restarts) frame production for a live consumer; latest-only and throttled. */
    void startCapture(int hostingGeneration, FrameSink sink) {
        if (imageReader == null) {
            return;
        }
        generation = hostingGeneration;
        frameSink = sink;
        frameSequence = 0;
        deliveredAny = false;
        Log.i(TAG, "startCapture gen=" + hostingGeneration + " reader=" + (imageReader != null)
                + " threadAlive=" + (captureThread != null));
        if (captureThread != null) {
            // Reacquisition after lease loss: the capture thread survived; rearm the listener.
            capturing = true;
            imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
            drainPendingImages();
            return;
        }
        capturing = true;
        captureThread = new HandlerThread("EyeBrowseHostingCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
        drainPendingImages();
    }

    /**
     * Images queued before the listener was armed do not reliably fire the callback; acquire and
     * close them so the producer cannot stay blocked on a full (maxImages=2) queue and stale
     * frames are dropped, latest-only.
     */
    private void drainPendingImages() {
        int drained = 0;
        while (imageReader != null) {
            Image stale = imageReader.acquireLatestImage();
            if (stale == null) {
                break;
            }
            stale.close();
            drained++;
        }
        if (drained > 0) {
            Log.i(TAG, "drained " + drained + " stale capture image(s)");
        }
    }

    /**
     * Recreates the capture reader/surface on the surviving display after an idle release, without
     * any navigation or attachment change. Returns {@code false} when the platform refuses.
     */
    boolean recreateCaptureSurface(int recreateWidth, int recreateHeight) {
        if (virtualDisplay == null || imageReader != null || captureThread != null) {
            return imageReader != null;
        }
        if (recreateWidth != width || recreateHeight != height) {
            return false; // The display geometry is fixed at creation; report, never resize.
        }
        ImageReader reader = factory.createImageReader(width, height);
        if (reader == null) {
            return false;
        }
        try {
            virtualDisplay.setSurface(reader.getSurface());
        } catch (RuntimeException error) {
            Log.w(TAG, "surface reattach failed", error);
            reader.close();
            return false;
        }
        imageReader = reader;
        return true;
    }

    /** Stops frame delivery; the reader stays until idle release or teardown. */
    void stopCapture() {
        capturing = false;
        frameSink = null;
    }

    boolean isCapturing() {
        return capturing && imageReader != null;
    }

    /**
     * Idle release: closes the reader and its surface, quits the capture thread and drops cached
     * frame buffers. The virtual display and presentation (the browser attachment) remain; a later
     * {@link #startCapture} recreates the surface on the same display without navigation.
     */
    void releaseCaptureResources() {
        capturing = false;
        frameSink = null;
        frameBitmap = null;
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    /** Full teardown for Stop: detaches the session view, dismisses, releases everything. */
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
        releaseCaptureResources();
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    // Resource introspection used by tests and cleanup evidence.
    boolean hasDisplay() {
        return virtualDisplay != null;
    }

    boolean hasPresentation() {
        return presentation != null;
    }

    boolean hasReader() {
        return imageReader != null;
    }

    boolean hasCaptureThread() {
        return captureThread != null;
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            long nowElapsed = SystemClock.elapsedRealtime();
            FrameSink sink = frameSink;
            if (!capturing || sink == null) {
                return; // Latest-only: stale buffers are dropped by closing the image.
            }
            if (deliveredAny && HostingPolicy.frameThrottled(nowElapsed, lastDeliveryElapsedMs)) {
                return;
            }
            deliveredAny = true;
            lastDeliveryElapsedMs = nowElapsed;
            deliverFrame(image, sink, nowElapsed);
        } catch (RuntimeException error) {
            Log.w(TAG, "hosting frame capture failed", error);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /** Copies the acquired image into the reused frame bitmap and hands it to the sink. */
    private void deliverFrame(Image image, FrameSink sink, long captureElapsedMs) {
        Image.Plane plane = image.getPlanes()[0];
        int rowStride = plane.getRowStride();
        int rowBytes = width * plane.getPixelStride();
        ByteBuffer packed = rowStride == rowBytes
                ? plane.getBuffer()
                : packedRowCopy(plane, height, rowStride, rowBytes);
        if (packed == null) {
            return;
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
        sink.onFrame(new HostingFrame(frameBitmap, width, height, generation, ++frameSequence,
                captureElapsedMs, contentHash(plane)));
    }

    /** Packs a stride-padded image buffer into tight rows for {@code copyPixelsFromBuffer}. */
    private static ByteBuffer packedRowCopy(Image.Plane plane, int rows, int rowStride,
            int rowBytes) {
        ByteBuffer source = plane.getBuffer().duplicate();
        final int planeLimit = source.limit(); // Captured before per-row limit mutation.
        ByteBuffer packed = ByteBuffer.allocateDirect(rowBytes * rows);
        for (int y = 0; y < rows; y++) {
            int rowStart = y * rowStride;
            if (rowStart + rowBytes > planeLimit) {
                break;
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
        int rows = height;
        for (int y = 0; y < rows; y++) {
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
