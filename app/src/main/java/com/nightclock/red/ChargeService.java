package com.nightclock.red;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Charger-connected broadcasts are only delivered to running apps, so this foreground
 * service stays alive to listen for them and open the clock.
 */
public class ChargeService extends Service {
    static final int CLOCK_NOTIFICATION_ID = 2;
    private static final int WATCH_NOTIFICATION_ID = 1;
    private static final String CHANNEL_WATCH = "watch";
    private static final String CHANNEL_CLOCK = "clock";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                showClock(context);
            } else if (Prefs.onlyWhenIdle(context) && isPluggedIn(context)
                    && !ClockActivity.isOpen) {
                // Screen turned off while charging: the user has just locked the phone.
                // Skipped while the clock is already open so the power button can still
                // turn the screen off over it.
                showClock(context);
            }
        }
    };

    static void start(Context c) {
        if (Prefs.enabled(c)) c.startForegroundService(new Intent(c, ChargeService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, ChargeService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels(this);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_WATCH)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Red clock will show while charging")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(WATCH_NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(WATCH_NOTIFICATION_ID, n);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isPluggedIn(Context c) {
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    static void showClock(Context c) {
        // The screen also goes dark against your ear during calls; never cover a call.
        int audioMode = c.getSystemService(AudioManager.class).getMode();
        if (audioMode == AudioManager.MODE_IN_CALL
                || audioMode == AudioManager.MODE_IN_COMMUNICATION) {
            return;
        }
        if (Prefs.onlyWhenIdle(c)) {
            boolean screenOn = c.getSystemService(PowerManager.class).isInteractive();
            boolean locked = c.getSystemService(KeyguardManager.class).isKeyguardLocked();
            if (screenOn && !locked) return;
        }

        Intent clock = new Intent(c, ClockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // "Display over other apps" lets a background app open an activity directly.
        if (Settings.canDrawOverlays(c)) {
            c.startActivity(clock);
            return;
        }

        // Otherwise fall back to a full-screen notification, which opens the clock when the
        // screen is off and shows a tappable alert when it is on.
        createChannels(c);
        PendingIntent pi = PendingIntent.getActivity(c, 1, clock,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL_CLOCK)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Charging")
                .setContentText("Tap to show the red clock")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(CLOCK_NOTIFICATION_ID, n);
    }

    private static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel watch = new NotificationChannel(CHANNEL_WATCH,
                "Charger watcher", NotificationManager.IMPORTANCE_MIN);
        watch.setShowBadge(false);
        NotificationChannel clock = new NotificationChannel(CHANNEL_CLOCK,
                "Open clock", NotificationManager.IMPORTANCE_HIGH);
        clock.setSound(null, null);
        clock.enableVibration(false);
        clock.setShowBadge(false);
        nm.createNotificationChannel(watch);
        nm.createNotificationChannel(clock);
    }
}
