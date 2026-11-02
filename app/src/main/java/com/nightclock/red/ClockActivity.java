package com.nightclock.red;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Full-screen clock shown over the lock screen; closes itself when the charger is unplugged. */
public class ClockActivity extends Activity {
    static final String EXTRA_PREVIEW = "preview";

    /** True from creation until the clock is closed, including while the screen is off. */
    static boolean isOpen;

    private ClockView clock;
    private float level;
    private float dragStartY, dragStartLevel;

    private final BroadcastReceiver unplugged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isOpen = true;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        clock = new ClockView(this);
        setContentView(clock);
        level = Prefs.level(this);
        applyLevel();

        registerReceiver(unplugged, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        getSystemService(NotificationManager.class).cancel(ChargeService.CLOCK_NOTIFICATION_ID);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clock.reload();
        level = Prefs.level(this);
        applyLevel();
        applyOrientation();
        hideSystemBars();
        if (!getIntent().getBooleanExtra(EXTRA_PREVIEW, false)
                && !ChargeService.isPluggedIn(this)) {
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        isOpen = false;
        unregisterReceiver(unplugged);
        super.onDestroy();
    }

    /** Swipe up or down anywhere to change brightness. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartY = event.getY();
                dragStartLevel = level;
                return true;
            case MotionEvent.ACTION_MOVE:
                float range = clock.getHeight() * 0.6f;
                level = Prefs.clamp(dragStartLevel + (dragStartY - event.getY()) / range);
                applyLevel();
                clock.showHint(Math.round(level * 100) + "%");
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Prefs.setLevel(this, level);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void applyLevel() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Prefs.backlight(level);
        getWindow().setAttributes(lp);
        clock.setLevel(level);
    }

    private void applyOrientation() {
        switch (Prefs.orientation(this)) {
            case Prefs.ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Prefs.ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
