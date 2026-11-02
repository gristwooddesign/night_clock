package com.nightclock.red;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;

/** Stored settings plus the mapping from the single brightness level to screen output. */
final class Prefs {
    private static final String ENABLED = "enabled";
    private static final String ONLY_WHEN_IDLE = "only_when_idle";
    private static final String LEVEL = "level";
    private static final String ORIENTATION = "orientation";
    private static final String FACE = "face";
    private static final String FONT = "font";
    private static final String COLOR = "color";
    private static final String FORMAT = "format";
    private static final String SHOW_BATTERY = "show_battery";
    private static final String SHOW_DATE = "show_date";

    static final int ORIENTATION_AUTO = 0;
    static final int ORIENTATION_PORTRAIT = 1;
    static final int ORIENTATION_LANDSCAPE = 2;

    static final int FACE_DIGITAL = 0;
    static final int FACE_LED = 1;
    static final int FACE_ANALOG = 2;

    static final int FORMAT_12_AMPM = 0;
    static final int FORMAT_12 = 1;
    static final int FORMAT_24 = 2;

    static final String[] FONT_NAMES =
            {"Standard", "Light", "Thin", "Condensed", "Bold", "Serif", "Monospace"};

    static final int[] COLORS = {
            0xFFFF0000, // red
            0xFFFF5A00, // orange
            0xFFFFB000, // amber
            0xFF20FF40, // green
            0xFF00E5FF, // cyan
            0xFF3070FF, // blue
            0xFFB050FF, // purple
            0xFFFF40A0, // pink
            0xFFFFFFFF, // white
    };

    private Prefs() {}

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, true);
    }

    static void setEnabled(Context c, boolean value) {
        get(c).edit().putBoolean(ENABLED, value).apply();
    }

    static boolean onlyWhenIdle(Context c) {
        return get(c).getBoolean(ONLY_WHEN_IDLE, false);
    }

    static void setOnlyWhenIdle(Context c, boolean value) {
        get(c).edit().putBoolean(ONLY_WHEN_IDLE, value).apply();
    }

    static int orientation(Context c) {
        return get(c).getInt(ORIENTATION, ORIENTATION_AUTO);
    }

    static void setOrientation(Context c, int value) {
        get(c).edit().putInt(ORIENTATION, value).apply();
    }

    static int face(Context c) {
        return get(c).getInt(FACE, FACE_DIGITAL);
    }

    static void setFace(Context c, int value) {
        get(c).edit().putInt(FACE, value).apply();
    }

    static int font(Context c) {
        return get(c).getInt(FONT, 0);
    }

    static void setFont(Context c, int value) {
        get(c).edit().putInt(FONT, value).apply();
    }

    static int color(Context c) {
        return get(c).getInt(COLOR, COLORS[0]);
    }

    static void setColor(Context c, int value) {
        get(c).edit().putInt(COLOR, value).apply();
    }

    static int format(Context c) {
        return get(c).getInt(FORMAT, FORMAT_12_AMPM);
    }

    static void setFormat(Context c, int value) {
        get(c).edit().putInt(FORMAT, value).apply();
    }

    static boolean showBattery(Context c) {
        return get(c).getBoolean(SHOW_BATTERY, true);
    }

    static void setShowBattery(Context c, boolean value) {
        get(c).edit().putBoolean(SHOW_BATTERY, value).apply();
    }

    static boolean showDate(Context c) {
        return get(c).getBoolean(SHOW_DATE, false);
    }

    static void setShowDate(Context c, boolean value) {
        get(c).edit().putBoolean(SHOW_DATE, value).apply();
    }

    /** Typeface for an index into FONT_NAMES. */
    static Typeface typeface(int font) {
        switch (font) {
            case 1:
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case 2:
                return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case 3:
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case 4:
                return Typeface.create("sans-serif", Typeface.BOLD);
            case 5:
                return Typeface.SERIF;
            case 6:
                return Typeface.MONOSPACE;
            default:
                return Typeface.create("sans-serif", Typeface.NORMAL);
        }
    }

    /** Brightness level from 0 (darkest) to 1 (brightest). */
    static float level(Context c) {
        return get(c).getFloat(LEVEL, 0.3f);
    }

    static void setLevel(Context c, float value) {
        get(c).edit().putFloat(LEVEL, clamp(value)).apply();
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * The lower half of the range dims the clock color itself with the backlight at its
     * minimum; the upper half keeps the full color and raises the backlight.
     */
    static float backlight(float level) {
        return 0.01f + Math.max(0f, (level - 0.5f) * 2f) * 0.99f;
    }

    static int tint(int color, float level) {
        float scale = 0.12f + 0.88f * Math.min(1f, level * 2f);
        return Color.rgb(Math.round(Color.red(color) * scale),
                Math.round(Color.green(color) * scale),
                Math.round(Color.blue(color) * scale));
    }
}
