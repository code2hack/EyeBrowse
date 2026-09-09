package com.code2hack.eyebrowse.lockprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class CommandReceiver extends BroadcastReceiver {
    private static final String TAG = "EyeBrowseLockProbe";
    public static final String ACTION_COMMAND = "com.code2hack.eyebrowse.lockprobe.COMMAND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COMMAND.equals(intent.getAction())) {
            return;
        }

        String command = intent.getStringExtra("command");
        String value = intent.getStringExtra("value");
        LockProbeService service = LockProbeService.getInstance();
        if (service == null) {
            Log.w(TAG, "Command ignored because probe service is not running: " + command);
            return;
        }
        service.handleCommand(command, value);
    }
}
