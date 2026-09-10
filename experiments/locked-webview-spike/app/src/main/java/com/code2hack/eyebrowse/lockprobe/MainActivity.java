package com.code2hack.eyebrowse.lockprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EyeBrowse locked-WebView spike");
        title.setTextSize(22f);
        root.addView(title, matchWrap());

        TextView note = new TextView(this);
        note.setText(
                "Issue #1 validation only. Start the probe while unlocked, then securely lock the phone. " +
                "Use adb broadcasts to scroll/click/type while locked and pull telemetry/latest.png with run-as. " +
                "No CXR, Pi, Mihomo, tailnet, or MediaProjection is involved.");
        note.setTextSize(15f);
        note.setPadding(0, dp(12), 0, dp(12));
        root.addView(note, matchWrap());

        Button start = new Button(this);
        start.setText("Start probe");
        start.setOnClickListener(v -> startProbe());
        root.addView(start, matchWrap());

        Button scroll = new Button(this);
        scroll.setText("Sanity: scroll down");
        scroll.setOnClickListener(v -> command("scrollDown", null));
        root.addView(scroll, matchWrap());

        Button click = new Button(this);
        click.setText("Sanity: click test button");
        click.setOnClickListener(v -> command("click", null));
        root.addView(click, matchWrap());

        Button type = new Button(this);
        type.setText("Sanity: type 'from-phone-ui'");
        type.setOnClickListener(v -> command("type", "from-phone-ui"));
        root.addView(type, matchWrap());

        Button stop = new Button(this);
        stop.setText("Stop probe");
        stop.setOnClickListener(v -> stopProbe());
        root.addView(stop, matchWrap());

        TextView paths = new TextView(this);
        paths.setText(
                "Evidence files:\n" +
                "files/lockprobe/telemetry.jsonl\n" +
                "files/lockprobe/latest.png\n\n" +
                "See experiments/locked-webview-spike/README.md for exact adb commands.");
        paths.setTextSize(14f);
        paths.setPadding(0, dp(14), 0, 0);
        root.addView(paths, matchWrap());

        setContentView(root);
    }

    private void startProbe() {
        Intent intent = new Intent(this, LockProbeService.class)
                .setAction(LockProbeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopProbe() {
        Intent intent = new Intent(this, LockProbeService.class)
                .setAction(LockProbeService.ACTION_STOP);
        startService(intent);
    }

    private void command(String command, String value) {
        LockProbeService service = LockProbeService.getInstance();
        if (service != null) {
            service.handleCommand(command, value);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
