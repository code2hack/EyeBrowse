package com.code2hack.eyebrowse.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * The ordinary non-exported foreground hosting service. It carries the hosting lifetime while the
 * browser stays usable in the background: it enters the foreground promptly, shows visible status
 * with an explicit Stop action, is START_NOT_STICKY, and never stops merely because the Activity
 * exits. All hosting logic lives in {@link HostingController}; this class is the Android lifetime
 * shell, without boot/remote start or any exported command surface.
 */
public final class HostingService extends Service {

    static final String ACTION_START = "com.code2hack.eyebrowse.phone.hosting.START";
    static final String ACTION_STOP = "com.code2hack.eyebrowse.phone.hosting.STOP";

    private static final String CHANNEL_ID = "eyebrowse_hosting";
    private static final int NOTIFICATION_ID = 51001;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            enterForeground();
            HostingController.get(this).onServiceReady(this);
        } catch (RuntimeException error) {
            // Recoverable foreground-entry/readiness failure at its owner rolls the start back
            // explicitly instead of escaping service creation (F8); fatal VM failures rethrow.
            HostingController.get(this).onServiceEntryFailed(error);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            HostingController.get(this).stop();
            stopSelf();
            return START_NOT_STICKY;
        }
        // START_NOT_STICKY: no automatic hosting after process death; recovery is an explicit
        // Phone-side restart.
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        HostingController.get(this).onServiceDestroyed(this);
        // Remove the foreground notification with the service; the platform does not reliably
        // retract it on this device when a started-foreground service merely stops.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private void enterForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.hosting_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);

        Intent stopIntent = new Intent(this, HostingService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hosting)
                .setContentTitle(getString(R.string.hosting_notification_title))
                .setContentText(getString(R.string.hosting_notification_text))
                .setOngoing(true)
                .addAction(0, getString(R.string.action_hosting_stop), stopPendingIntent)
                .build();
        // specialUse (API 34+) is declared in the manifest with its subtype property; below 34 the
        // plain foreground start is the ordinary path.
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
