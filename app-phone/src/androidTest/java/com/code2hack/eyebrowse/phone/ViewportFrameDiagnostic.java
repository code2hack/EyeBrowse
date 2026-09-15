package com.code2hack.eyebrowse.phone;

import android.app.Presentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** One-run, fixture-only diagnostic observer. No production listener/buffer is replaced/acquired. */
final class ViewportFrameDiagnostic {
    private static final int MAX_FRAMES = 60;
    private static final int EXPECTED = 0xfff6f3ea;
    private static final long MAX_IMAGES = 16L * 1024 * 1024;
    private final HostingController hosting;
    private final PhoneBrowserSession session;
    private final String fixtureUrl;
    private final File directory;
    private final File events;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<ViewTreeObserver> observers = new ArrayList<>();
    private volatile int treeDraws;
    private volatile long lastTreeDraw;
    private final ViewTreeObserver.OnDrawListener drawListener = () -> {
        treeDraws++;
        lastTreeDraw = SystemClock.elapsedRealtime();
    };
    private volatile String error;
    private volatile boolean closed;
    private int frameCount;
    private long firstDelivery;
    private long imageBytes;
    private byte[] bitmapBytes;
    private final boolean[] laterFull = new boolean[3];

    ViewportFrameDiagnostic(File directory, HostingController hosting,
            PhoneBrowserSession session, String fixtureUrl) {
        this.directory = directory;
        this.events = new File(directory, "events.jsonl");
        this.hosting = hosting;
        this.session = session;
        this.fixtureUrl = fixtureUrl;
        if (directory.exists() || !directory.mkdirs()) {
            throw new IllegalStateException("diagnostic directory must be new");
        }
        emit(json("type", "diagnostic-start", "elapsedMs", SystemClock.elapsedRealtime(),
                "wallMs", System.currentTimeMillis(), "expectedArgb", hex(EXPECTED),
                "unchangedTolerance", 8, "maxFrames", MAX_FRAMES,
                "maxImageBytes", MAX_IMAGES, "acceptanceRun", false));
    }

    synchronized void emit(JSONObject value) {
        try {
            if (events.length() > 512 * 1024) throw new IOException("metadata cap exceeded");
            try (FileWriter writer = new FileWriter(events, true)) {
                writer.write(value.toString());
                writer.write('\n');
            }
        } catch (IOException failure) {
            error = "diagnostic write failed: " + failure.getMessage();
        }
    }

    /** Called inside the original borrowed-frame callback, never retaining the borrowed bitmap. */
    void onFrame(HostingFrame frame, long deliveryElapsedMs) {
        if (closed) return;
        int index = ++frameCount;
        if (index > MAX_FRAMES) {
            error = "frame diagnostic cap exceeded";
            return;
        }
        if (index == 1) firstDelivery = deliveryElapsedMs;
        long began = SystemClock.elapsedRealtime();
        try {
            Bitmap bitmap = frame.bitmap;
            int rowBytes = bitmap.getRowBytes();
            int bytes = bitmap.getByteCount();
            if (bitmapBytes == null || bitmapBytes.length != bytes) bitmapBytes = new byte[bytes];
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bitmapBytes));
            CRC32 crc = new CRC32();
            for (int y = 0; y < frame.height; y++) crc.update(bitmapBytes, y * rowBytes, frame.width * 4);
            JSONArray samples = new JSONArray();
            int[][] points = {{0,0},{1,1},{10,10},{frame.width/2,10},{frame.width-11,10},
                    {10,frame.height/2},{frame.width/2,frame.height/2},
                    {frame.width-11,frame.height/2},{10,frame.height-11},
                    {frame.width/2,frame.height-11},{frame.width-11,frame.height-11}};
            for (int[] p : points) {
                int offset = p[1] * rowBytes + p[0] * 4;
                JSONArray raw = new JSONArray();
                for (int j = 0; j < 4; j++) raw.put(bitmapBytes[offset+j] & 255);
                samples.put(json("x",p[0],"y",p[1],"argb",hex(bitmap.getPixel(p[0],p[1])),
                        "nativeBitmapBytes",raw));
            }
            JSONArray topColumn = new JSONArray();
            for (int y : new int[]{0,1,5,10,16,24,32,48,64,96,128}) {
                if (y < frame.height) topColumn.put(json("x",10,"y",y,"argb",hex(bitmap.getPixel(10,y))));
            }
            int white = 0, expected = 0, total = 0;
            for (int gy=0; gy<17; gy++) for (int gx=0; gx<17; gx++) {
                int color=bitmap.getPixel(gx*(frame.width-1)/16, gy*(frame.height-1)/16);
                if (near(color, Color.WHITE)) white++;
                if (near(color, EXPECTED)) expected++;
                total++;
            }
            String thumbName = String.format("frame-%03d-thumb.png",index);
            int tw=Math.min(640,bitmap.getWidth());
            Bitmap thumbnail=Bitmap.createScaledBitmap(bitmap,tw,
                    Math.max(1,bitmap.getHeight()*tw/bitmap.getWidth()),false);
            saveImage(thumbnail,thumbName);
            if (thumbnail != bitmap) thumbnail.recycle();
            boolean full = index <= 3;
            long age = deliveryElapsedMs-firstDelivery;
            long[] times={2000,5000,9000};
            for(int i=0;i<times.length;i++) if(!laterFull[i] && age>=times[i]) {
                laterFull[i]=true; full=true; break;
            }
            String fullName=full ? String.format("frame-%03d-full.png",index) : "";
            if(full) saveImage(bitmap,fullName);
            emit(json("type","frame","index",index,"sequence",frame.sequence,
                    "hostingGeneration",frame.generation,"captureElapsedMs",frame.captureElapsedMs,
                    "deliveryElapsedMs",deliveryElapsedMs,"diagnosticStartMs",began,
                    "diagnosticEndMs",SystemClock.elapsedRealtime(),"width",frame.width,"height",frame.height,
                    "bitmapWidth",bitmap.getWidth(),"bitmapHeight",bitmap.getHeight(),
                    "bitmapRowBytes",rowBytes,"bitmapByteCount",bytes,"bitmapConfig",String.valueOf(bitmap.getConfig()),
                    "bitmapColorSpace",String.valueOf(bitmap.getColorSpace()),"bitmapIdentity",id(bitmap),
                    "producerPlaneCrc",frame.contentHash,"bitmapValidByteCrc",crc.getValue(),
                    "expectedArgb",hex(EXPECTED),"originalPixelMatches",near(bitmap.getPixel(10,10),EXPECTED),
                    "samples",samples,"topColumn",topColumn,"whiteGridSamples",white,
                    "expectedGridSamples",expected,"gridSamples",total,"thumbnail",thumbName,"fullImage",fullName));
            main.post(() -> snapshot("after-frame",index));
        } catch (Exception failure) {
            error="frame capture failed: "+failure.getClass().getSimpleName()+": "+failure.getMessage();
            emit(json("type","diagnostic-error","index",index,"error",error));
        }
    }

    /** Main-thread observation, separately timed; not asserted atomic with the linked frame. */
    void snapshot(String phase, int frameIndex) {
        long start=SystemClock.elapsedRealtime();
        try {
            Object host=field(hosting,"displayHost");
            Object presentation=host==null?null:field(host,"presentation");
            VirtualDisplay display=host==null?null:(VirtualDisplay)field(host,"virtualDisplay");
            ImageReader reader=host==null?null:(ImageReader)field(host,"imageReader");
            View container=presentation==null?null:((PrivateDisplayHost.PresentationHost)presentation).container();
            WebView view=session.view();
            if(view!=null) {
                ViewTreeObserver observer=view.getViewTreeObserver();
                if(observer.isAlive() && !observers.contains(observer)) {
                    observer.addOnDrawListener(drawListener); observers.add(observer);
                }
            }
            HostingController.Status status=hosting.status();
            View decor=presentation instanceof Presentation && ((Presentation)presentation).getWindow()!=null
                    ? ((Presentation)presentation).getWindow().getDecorView():null;
            JSONArray parents=new JSONArray();
            for(ViewParent p=view==null?null:view.getParent();p instanceof View && parents.length()<6;p=p.getParent()) {
                parents.put(viewInfo((View)p));
            }
            emit(json("type","ui-snapshot","phase",phase,"frameIndex",frameIndex,
                    "sampleStartElapsedMs",start,"sampleEndElapsedMs",SystemClock.elapsedRealtime(),
                    "hostingState",status.state.name(),"attachment",status.attachment.name(),
                    "hostingGeneration",status.generation,"captureActive",status.captureActive,
                    "urlIdentity",view!=null && fixtureUrl.equals(view.getUrl())?fixtureUrl:"(other-or-none)",
                    "webView",viewInfo(view),"expectedContainer",viewInfo(container),
                    "parentIsExpectedContainer",view!=null && container!=null && view.getParent()==container,
                    "parentChain",parents,"presentationIdentity",id(presentation),
                    "virtualDisplayIdentity",id(display),"privateDisplayId",display==null?-1:display.getDisplay().getDisplayId(),
                    "readerIdentity",id(reader),"readerWidth",reader==null?0:reader.getWidth(),
                    "readerHeight",reader==null?0:reader.getHeight(),"readerFormat",reader==null?0:reader.getImageFormat(),
                    "decor",viewInfo(decor),"treeDrawEvents",treeDraws,"lastTreeDrawElapsedMs",lastTreeDraw));
        } catch(Exception failure) {
            error="UI snapshot failed: "+failure.getClass().getSimpleName();
            emit(json("type","diagnostic-error","phase",phase,"error",error));
        }
    }

    void close() { // main thread, after lease release and queued snapshots
        closed=true;
        for(ViewTreeObserver observer:observers) if(observer.isAlive()) observer.removeOnDrawListener(drawListener);
        emit(json("type","diagnostic-end","frames",frameCount,"error",error,
                "imageBytes",imageBytes,"elapsedMs",SystemClock.elapsedRealtime()));
    }

    String error() { return error; }
    String path() { return directory.getAbsolutePath(); }

    private void saveImage(Bitmap bitmap,String name) throws IOException {
        try(OutputStream stream=new FilterOutputStream(new FileOutputStream(new File(directory,name))) {
            @Override public void write(byte[] b,int off,int len) throws IOException {
                if(imageBytes+len>MAX_IMAGES) throw new IOException("image cap exceeded");
                out.write(b,off,len);imageBytes+=len;
            }
            @Override public void write(int b) throws IOException {
                if(imageBytes+1>MAX_IMAGES) throw new IOException("image cap exceeded");
                out.write(b);imageBytes++;
            }
        }) { if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException("PNG failed"); }
    }

    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static int id(Object value) { return value==null?0:System.identityHashCode(value); }
    private static boolean near(int a,int b) { return Math.abs(Color.red(a)-Color.red(b))<=8
            && Math.abs(Color.green(a)-Color.green(b))<=8 && Math.abs(Color.blue(a)-Color.blue(b))<=8; }
    private static String hex(int color) { return String.format("#%08x",color); }
    static JSONObject json(Object... pairs) {
        JSONObject out=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) out.put(String.valueOf(pairs[i]),pairs[i+1]==null?JSONObject.NULL:pairs[i+1]); }
        catch(Exception e) { throw new IllegalArgumentException(e); }
        return out;
    }
    private static JSONObject viewInfo(View view) {
        if(view==null) return json("present",false);
        int[] screen=new int[2],window=new int[2];view.getLocationOnScreen(screen);view.getLocationInWindow(window);
        Rect visible=new Rect();boolean hasVisible=view.getGlobalVisibleRect(visible);
        JSONArray padding=new JSONArray();padding.put(view.getPaddingLeft());padding.put(view.getPaddingTop());
        padding.put(view.getPaddingRight());padding.put(view.getPaddingBottom());
        return json("present",true,"id",id(view),"class",view.getClass().getSimpleName(),
                "width",view.getWidth(),"height",view.getHeight(),"left",view.getLeft(),"top",view.getTop(),
                "screenX",screen[0],"screenY",screen[1],"windowX",window[0],"windowY",window[1],
                "padding",padding,
                "visibility",view.getVisibility(),"windowVisibility",view.getWindowVisibility(),"shown",view.isShown(),
                "attached",view.isAttachedToWindow(),"hardwareAccelerated",view.isHardwareAccelerated(),"alpha",view.getAlpha(),
                "displayId",view.getDisplay()==null?-1:view.getDisplay().getDisplayId(),
                "densityDpi",view.getResources().getConfiguration().densityDpi,
                "globalVisible",hasVisible,"globalVisibleRect",visible.toShortString());
    }
}
