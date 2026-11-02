package com.nightclock.red;

import android.service.dreams.DreamService;
import android.view.WindowManager;

/** The same clock offered as a system screen saver (Settings > Display > Screen saver). */
public class NightDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        float level = Prefs.level(this);
        setContentView(new ClockView(this));
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Prefs.backlight(level);
        getWindow().setAttributes(lp);
    }
}
