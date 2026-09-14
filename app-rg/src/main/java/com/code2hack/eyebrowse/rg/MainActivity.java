package com.code2hack.eyebrowse.rg;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

/**
 * The Reading Glasses launcher for this slice: an honest, legible "not connected" state.
 *
 * <p>It holds no WebView, opens no network connection, offers no pairing or browser control and
 * claims no working Phone link.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.rg_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
