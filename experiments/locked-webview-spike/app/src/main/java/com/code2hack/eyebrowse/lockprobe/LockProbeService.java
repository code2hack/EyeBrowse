package com.code2hack.eyebrowse.lockprobe;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Presentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.zip.CRC32;

public final class LockProbeService extends Service {
    private static final String TAG = "EyeBrowseLockProbe";
    private static final String CHANNEL_ID = "locked_webview_spike";
    private static final int NOTIFICATION_ID = 41001;

    public static final String ACTION_START = "com.code2hack.eyebrowse.lockprobe.START";
    public static final String ACTION_STOP = "com.code2hack.eyebrowse.lockprobe.STOP";

    // RG-like physical render target. 160 dpi is intentionally a validation constant, not an RG claim.
    private static final int WIDTH = 480;
    private static final int HEIGHT = 640;
    private static final int DENSITY_DPI = 160;

    private static volatile LockProbeService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler captureHandler;

    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private ProbePresentation presentation;
    private WebView webView;
    private PowerManager.WakeLock wakeLock;

    private File evidenceDir;
    private File telemetryFile;
    private long frameSequence = 0L;
    private long lastFrameHash = Long.MIN_VALUE;
    private long lastFrameEvidenceMs = 0L;
    private long lastPngMs = 0L;
    private boolean started;

    private final Runnable pageStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!started || webView == null) {
                return;
            }
            String js = "(function(){return JSON.stringify({" +
                    "counter:(document.getElementById('counter')||{}).textContent||''," +
                    "status:(document.getElementById('status')||{}).textContent||''," +
                    "text:(document.getElementById('textInput')||{}).value||''," +
                    "scrollY:Math.round(window.scrollY)," +
                    "href:location.href" +
                    "});})()";
            webView.evaluateJavascript(js, value ->
                    appendTelemetry("page", "\"state\":" + jsonString(value)));
            mainHandler.postDelayed(this, 1000L);
        }
    };

    public static LockProbeService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        evidenceDir = new File(getFilesDir(), "lockprobe");
        if (!evidenceDir.exists() && !evidenceDir.mkdirs()) {
            Log.w(TAG, "Could not create evidence directory " + evidenceDir);
        }
        telemetryFile = new File(evidenceDir, "telemetry.jsonl");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            appendTelemetry("lifecycle", "\"event\":\"stop-requested\"");
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground();
        if (!started) {
            startProbe();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "EyeBrowse locked-WebView spike",
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("EyeBrowse lock probe running")
                .setContentText("Offscreen 480×640 WebView validation session")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startProbe() {
        started = true;
        appendTelemetry("lifecycle", "\"event\":\"start\"");

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EyeBrowse:LockedWebViewSpike");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(6 * 60 * 60 * 1000L);

        captureThread = new HandlerThread("EyeBrowseLockProbeCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

        DisplayManager displayManager = getSystemService(DisplayManager.class);
        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
        virtualDisplay = displayManager.createVirtualDisplay(
                "EyeBrowseLockedWebView",
                WIDTH,
                HEIGHT,
                DENSITY_DPI,
                imageReader.getSurface(),
                displayFlags);

        if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
            appendTelemetry("error", "\"message\":\"virtual-display-create-failed\"");
            Log.e(TAG, "VirtualDisplay creation failed");
            return;
        }

        try {
            presentation = new ProbePresentation(this, virtualDisplay.getDisplay());
            presentation.show();
            webView = presentation.getWebView();
            appendTelemetry("lifecycle", "\"event\":\"presentation-shown\"");
            mainHandler.post(pageStateRunnable);
        } catch (RuntimeException error) {
            appendTelemetry("error", "\"message\":" + jsonString("presentation: " + error));
            Log.e(TAG, "Could not show Presentation on virtual display", error);
        }
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            frameSequence++;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            long hash = frameHash(buffer, plane.getRowStride(), plane.getPixelStride());
            boolean changed = hash != lastFrameHash;
            lastFrameHash = hash;

            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameEvidenceMs >= 1000L) {
                lastFrameEvidenceMs = now;
                appendTelemetry(
                        "frame",
                        "\"seq\":" + frameSequence +
                                ",\"hash\":" + hash +
                                ",\"changed\":" + changed +
                                ",\"rowStride\":" + plane.getRowStride() +
                                ",\"pixelStride\":" + plane.getPixelStride());
            }

            if (now - lastPngMs >= 2000L) {
                lastPngMs = now;
                writeLatestPng(buffer, plane.getRowStride(), plane.getPixelStride());
            }
        } catch (RuntimeException error) {
            appendTelemetry("error", "\"message\":" + jsonString("image: " + error));
            Log.e(TAG, "Image capture error", error);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /**
     * CRC32 over every valid pixel byte of the frame, excluding row padding.
     *
     * <p>An earlier version sampled only every 24th pixel, which stayed constant while the
     * small on-page counter changed and therefore could not prove frame freshness. A full-frame
     * hash makes stale/repeated surfaces detectable from telemetry alone.
     */
    private long frameHash(ByteBuffer source, int rowStride, int pixelStride) {
        CRC32 crc = new CRC32();
        ByteBuffer buffer = source.duplicate();
        int rowBytes = WIDTH * pixelStride;
        int limit = buffer.limit();
        for (int y = 0; y < HEIGHT; y++) {
            int rowStart = y * rowStride;
            if (rowStart < 0 || rowStart + rowBytes > limit) {
                break;
            }
            ByteBuffer row = buffer.duplicate();
            row.position(rowStart);
            row.limit(rowStart + rowBytes);
            crc.update(row);
        }
        return crc.getValue();
    }

    private void writeLatestPng(ByteBuffer source, int rowStride, int pixelStride) {
        if (pixelStride <= 0 || rowStride <= 0) {
            return;
        }
        int paddedWidth = rowStride / pixelStride;
        if (paddedWidth < WIDTH) {
            return;
        }

        Bitmap padded = null;
        Bitmap cropped = null;
        try {
            padded = Bitmap.createBitmap(paddedWidth, HEIGHT, Bitmap.Config.ARGB_8888);
            ByteBuffer copy = source.duplicate();
            copy.rewind();
            padded.copyPixelsFromBuffer(copy);
            cropped = Bitmap.createBitmap(padded, 0, 0, WIDTH, HEIGHT);
            File output = new File(evidenceDir, "latest.png");
            try (FileOutputStream stream = new FileOutputStream(output, false)) {
                cropped.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
        } catch (IOException | RuntimeException error) {
            appendTelemetry("error", "\"message\":" + jsonString("png: " + error));
        } finally {
            if (cropped != null) {
                cropped.recycle();
            }
            if (padded != null) {
                padded.recycle();
            }
        }
    }

    public void handleCommand(String command, String value) {
        mainHandler.post(() -> executeCommand(command, value));
    }

    private void executeCommand(String command, String value) {
        String normalized = command == null ? "" : command;
        if ("stop".equals(normalized)) {
            // ADB cannot stop a non-exported service with `am stopservice`, so the debug
            // command surface exposes an explicit post-lock stop that releases resources.
            appendTelemetry("lifecycle", "\"event\":\"stop-requested\"");
            stopSelf();
            return;
        }
        if (!started || webView == null) {
            appendTelemetry("command", "\"command\":" + jsonString(normalized) + ",\"result\":\"no-webview\"");
            return;
        }
        appendTelemetry(
                "command",
                "\"command\":" + jsonString(normalized) +
                        (value == null ? "" : ",\"value\":" + jsonString(value)));

        switch (normalized) {
            case "scrollDown":
                webView.evaluateJavascript("window.scrollBy(0,180);", null);
                break;
            case "scrollUp":
                webView.evaluateJavascript("window.scrollBy(0,-180);", null);
                break;
            case "click":
                webView.evaluateJavascript("document.getElementById('actionButton').click();", null);
                break;
            case "type":
                String text = value == null ? "" : value;
                String js = "(function(v){var e=document.getElementById('textInput');" +
                        "var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                        "s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "e.dispatchEvent(new Event('change',{bubbles:true}));e.focus();})(" + jsString(text) + ");";
                webView.evaluateJavascript(js, null);
                break;
            case "reload":
                webView.reload();
                break;
            case "reset":
                webView.loadDataWithBaseURL(
                        "https://lockprobe.invalid/",
                        TEST_HTML,
                        "text/html",
                        "UTF-8",
                        null);
                break;
            case "navigate":
                if (value != null && (value.startsWith("https://") || value.startsWith("http://"))) {
                    webView.loadUrl(value);
                }
                break;
            case "dump":
                mainHandler.removeCallbacks(pageStateRunnable);
                mainHandler.post(pageStateRunnable);
                break;
            default:
                appendTelemetry("command", "\"command\":" + jsonString(normalized) + ",\"result\":\"unknown\"");
                break;
        }
    }

    private synchronized void appendTelemetry(String type, String fields) {
        if (telemetryFile == null) {
            return;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        String line = String.format(
                Locale.US,
                "{\"elapsedMs\":%d,\"wallMs\":%d,\"type\":%s,\"interactive\":%s,\"deviceLocked\":%s,%s%s%s}%n",
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
                jsonString(type),
                pm.isInteractive(),
                km.isDeviceLocked(),
                batteryFields(),
                fields == null || fields.isEmpty() ? "" : ",",
                fields == null ? "" : fields);
        try (FileWriter writer = new FileWriter(telemetryFile, true)) {
            writer.write(line);
        } catch (IOException error) {
            Log.e(TAG, "Could not append telemetry", error);
        }
        Log.d(TAG, line.trim());
    }

    private String batteryFields() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = -1;
        int status = -1;
        int plugged = -1;
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                percent = Math.round(100f * level / scale);
            }
            status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        }
        return "\"batteryPct\":" + percent + ",\"batteryStatus\":" + status + ",\"plugged\":" + plugged;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    private static String jsString(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "'";
    }

    @Override
    public void onDestroy() {
        started = false;
        mainHandler.removeCallbacks(pageStateRunnable);
        appendTelemetry("lifecycle", "\"event\":\"destroy\"");

        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (RuntimeException ignored) {
            }
            presentation = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        instance = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private final class ProbePresentation extends Presentation {
        private WebView localWebView;

        ProbePresentation(Context outerContext, Display display) {
            super(outerContext, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            localWebView = new WebView(getContext());
            localWebView.setBackgroundColor(Color.WHITE);
            WebSettings settings = localWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setOffscreenPreRaster(true);
            localWebView.setWebViewClient(new WebViewClient());
            setContentView(localWebView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            localWebView.loadDataWithBaseURL(
                    "https://lockprobe.invalid/",
                    TEST_HTML,
                    "text/html",
                    "UTF-8",
                    null);
        }

        WebView getWebView() {
            return localWebView;
        }
    }

    private static final String TEST_HTML = "<!doctype html>\n" +
            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>\n" +
            "<style>body{font-family:sans-serif;margin:0;background:#f6f3ea;color:#171717;}" +
            ".bar{position:sticky;top:0;background:#dcebdc;padding:14px;border-bottom:1px solid #8b9b8b;}" +
            ".card{margin:16px;padding:16px;background:white;border-radius:18px;}" +
            "button,input{font-size:18px;padding:12px;margin:6px 0;width:100%;box-sizing:border-box;}" +
            ".block{height:240px;margin:16px;background:linear-gradient(135deg,#ececec,#d8e5f0);border-radius:18px;padding:18px;box-sizing:border-box;}" +
            "</style></head><body>\n" +
            "<div class='bar'><b>LOCK PROBE</b><div>counter: <span id='counter'>0</span></div>" +
            "<div>status: <span id='status'>ready</span></div></div>\n" +
            "<div class='card'><button id='actionButton' onclick=\"document.getElementById('status').textContent='clicked@'+Date.now()\">Change page state</button>" +
            "<input id='textInput' placeholder='remote text target'><p>Scroll position and input value are sampled into telemetry once per second.</p></div>\n" +
            "<div class='block'>Block 1</div><div class='block'>Block 2</div><div class='block'>Block 3</div>" +
            "<div class='block'>Block 4</div><div class='block'>Block 5</div><div class='block'>Block 6</div>\n" +
            "<script>let n=0;setInterval(()=>{n++;document.getElementById('counter').textContent=n+' @ '+Date.now();},250);</script>\n" +
            "</body></html>";
}
