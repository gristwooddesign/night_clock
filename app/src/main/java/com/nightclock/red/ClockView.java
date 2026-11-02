package com.nightclock.red;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * The clock face (digital, LED or analog) with an optional line of date and battery level
 * below it, on black. The whole block drifts slowly and bounces off the screen edges so no
 * pixel stays lit in the same place for long.
 */
public class ClockView extends View {
    private static final long TICK_MS = 200;
    /** Seven-segment patterns for 0-9; bit 0 is segment a (top) through bit 6, g (middle). */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final Calendar calendar = Calendar.getInstance();
    private final String datePattern =
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd");

    // Style settings, read in reload().
    private int face, format, color;
    private boolean showBattery, showDate;

    private int battery = -1;
    private String hint;
    private long hintUntil;

    // Face layout, measured in relayout().
    private float faceW, faceH, digitH, capH, gap, lineGap;
    private float segW, segT, segGap, colonW;
    // The block is the face plus the small line under it.
    private String line = "";
    private float lineW, blockW, blockH;
    // Top-left of the block and its drift velocity in pixels per tick.
    private float x = -1, y = -1, dx, dy;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            step();
            invalidate();
            postDelayed(this, TICK_MS);
        }
    };

    public ClockView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        timePaint.setFontFeatureSettings("tnum");
        smallPaint.setFontFeatureSettings("tnum");
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        float speed = Math.max(1f, getResources().getDisplayMetrics().density * 0.3f);
        dx = random.nextBoolean() ? speed : -speed;
        dy = random.nextBoolean() ? speed : -speed;
        reload();
    }

    /** Re-reads the style settings; call after they change. */
    public void reload() {
        Context c = getContext();
        face = Prefs.face(c);
        format = Prefs.format(c);
        color = Prefs.color(c);
        showBattery = Prefs.showBattery(c);
        showDate = Prefs.showDate(c);
        Typeface typeface = face == Prefs.FACE_DIGITAL
                ? Prefs.typeface(Prefs.font(c))
                : Typeface.create("sans-serif", Typeface.NORMAL);
        timePaint.setTypeface(typeface);
        smallPaint.setTypeface(typeface);
        setLevel(Prefs.level(c));
        if (getWidth() > 0) relayout(getWidth(), getHeight());
    }

    public void setLevel(float level) {
        int tinted = Prefs.tint(color, level);
        timePaint.setColor(tinted);
        smallPaint.setColor(tinted);
        shapePaint.setColor(tinted);
        invalidate();
    }

    /** Shows short text in place of the date/battery line for a moment. */
    public void showHint(String text) {
        hint = text;
        hintUntil = SystemClock.uptimeMillis() + 1500;
        updateLine();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Intent sticky = getContext().registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) updateBattery(sticky);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        getContext().unregisterReceiver(batteryReceiver);
        super.onDetachedFromWindow();
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        battery = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
        updateLine();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        relayout(w, h);
    }

    private void relayout(int w, int h) {
        float shortSide = Math.min(w, h);
        // Landscape has room for a bigger face relative to the short side.
        boolean landscape = w > h;
        float size;
        switch (face) {
            case Prefs.FACE_LED:
                size = shortSide * (landscape ? 0.3f : 0.2f);
                layoutLed(size);
                if (faceW > w * 0.85f) layoutLed(size * w * 0.85f / faceW);
                break;
            case Prefs.FACE_ANALOG:
                layoutAnalog(shortSide * (landscape ? 0.62f : 0.6f));
                break;
            default:
                size = shortSide * (landscape ? 0.4f : 0.28f);
                layoutDigital(size);
                if (faceW > w * 0.85f) layoutDigital(size * w * 0.85f / faceW);
        }
        updateLine();

        if (x < 0) {
            x = random.nextFloat() * Math.max(0, w - blockW);
            y = random.nextFloat() * Math.max(0, h - blockH);
        }
        x = Math.min(x, Math.max(0, w - blockW));
        y = Math.min(y, Math.max(0, h - blockH));
        invalidate();
    }

    private void layoutDigital(float size) {
        timePaint.setTextSize(size);
        digitH = textHeight(timePaint, "0");
        smallPaint.setTextSize(size * 0.28f);
        capH = textHeight(smallPaint, "M");
        gap = size * 0.08f;
        lineGap = digitH * 0.4f;
        faceW = timePaint.measureText("00:00") + ampmWidth();
        faceH = digitH;
    }

    /** Seven-segment digits {@code height} tall. */
    private void layoutLed(float height) {
        digitH = height;
        segW = height * 0.55f;
        segT = height * 0.13f;
        segGap = height * 0.12f;
        colonW = segT * 3;
        smallPaint.setTextSize(height * 0.4f);
        capH = textHeight(smallPaint, "M");
        gap = height * 0.12f;
        lineGap = height * 0.4f;
        faceW = 4 * segW + 2 * segGap + colonW + ampmWidth();
        faceH = height;
    }

    private void layoutAnalog(float diameter) {
        faceW = faceH = diameter;
        smallPaint.setTextSize(diameter * 0.1f);
        capH = textHeight(smallPaint, "M");
        lineGap = diameter * 0.08f;
    }

    private float ampmWidth() {
        if (format != Prefs.FORMAT_12_AMPM) return 0;
        return gap + Math.max(smallPaint.measureText("AM"), smallPaint.measureText("PM"));
    }

    private static float textHeight(Paint paint, String text) {
        Rect r = new Rect();
        paint.getTextBounds(text, 0, text.length(), r);
        return r.height();
    }

    private void updateLine() {
        StringBuilder sb = new StringBuilder();
        if (hint != null && SystemClock.uptimeMillis() < hintUntil) {
            sb.append(hint);
        } else {
            if (showDate) {
                calendar.setTimeInMillis(System.currentTimeMillis());
                sb.append(DateFormat.format(datePattern, calendar));
            }
            if (showBattery && battery >= 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(battery).append('%');
            }
        }
        line = sb.toString();
        lineW = smallPaint.measureText(line);
        blockW = Math.max(faceW, lineW);
        blockH = faceH + (line.isEmpty() ? 0 : lineGap + capH);
    }

    private void step() {
        updateLine();
        float maxX = Math.max(0, getWidth() - blockW);
        float maxY = Math.max(0, getHeight() - blockH);
        x += dx;
        y += dy;
        if (x < 0 || x > maxX) {
            dx = -dx;
            x = Math.max(0, Math.min(x, maxX));
        }
        if (y < 0 || y > maxY) {
            dy = -dy;
            y = Math.max(0, Math.min(y, maxY));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        calendar.setTimeInMillis(System.currentTimeMillis());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        float faceX = x + (blockW - faceW) / 2f;
        switch (face) {
            case Prefs.FACE_LED:
                drawLed(canvas, faceX, hour, minute);
                break;
            case Prefs.FACE_ANALOG:
                drawAnalog(canvas, faceX, hour, minute);
                break;
            default:
                drawDigital(canvas, faceX, hour, minute);
        }

        if (!line.isEmpty()) {
            canvas.drawText(line, x + (blockW - lineW) / 2f, y + blockH, smallPaint);
        }
    }

    private String hourText(int hour) {
        if (format == Prefs.FORMAT_24) return twoDigits(hour);
        return String.valueOf(hour % 12 == 0 ? 12 : hour % 12);
    }

    private static String twoDigits(int value) {
        return (value < 10 ? "0" : "") + value;
    }

    private String ampm(int hour) {
        if (format != Prefs.FORMAT_12_AMPM) return null;
        return hour < 12 ? "AM" : "PM";
    }

    private void drawDigital(Canvas canvas, float faceX, int hour, int minute) {
        String time = hourText(hour) + ":" + twoDigits(minute);
        String ampm = ampm(hour);
        float timeW = timePaint.measureText(time);
        float total = timeW + (ampm == null ? 0 : gap + smallPaint.measureText(ampm));
        float left = faceX + (faceW - total) / 2f;
        canvas.drawText(time, left, y + digitH, timePaint);
        if (ampm != null) canvas.drawText(ampm, left + timeW + gap, y + capH, smallPaint);
    }

    private void drawLed(Canvas canvas, float faceX, int hour, int minute) {
        String digits = hourText(hour) + twoDigits(minute);
        int hourCount = digits.length() - 2;
        String ampm = ampm(hour);
        float total = digits.length() * segW + (digits.length() - 2) * segGap + colonW
                + (ampm == null ? 0 : gap + smallPaint.measureText(ampm));
        float left = faceX + (faceW - total) / 2f;

        shapePaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < digits.length(); i++) {
            if (i == hourCount) {
                float mid = left + colonW / 2f;
                float half = segT / 2f;
                canvas.drawRect(mid - half, y + digitH * 0.3f - half,
                        mid + half, y + digitH * 0.3f + half, shapePaint);
                canvas.drawRect(mid - half, y + digitH * 0.7f - half,
                        mid + half, y + digitH * 0.7f + half, shapePaint);
                left += colonW;
            } else if (i > 0) {
                left += segGap;
            }
            drawSegments(canvas, SEGMENTS[digits.charAt(i) - '0'], left);
            left += segW;
        }
        if (ampm != null) canvas.drawText(ampm, left + gap, y + capH, smallPaint);
    }

    private void drawSegments(Canvas canvas, int bits, float left) {
        float t = segT;
        float l = left + t / 2f, r = left + segW - t / 2f;
        float top = y + t / 2f, mid = y + digitH / 2f, bottom = y + digitH - t / 2f;
        if ((bits & 1) != 0) horizontalSegment(canvas, l, r, top);
        if ((bits & 2) != 0) verticalSegment(canvas, r, top, mid);
        if ((bits & 4) != 0) verticalSegment(canvas, r, mid, bottom);
        if ((bits & 8) != 0) horizontalSegment(canvas, l, r, bottom);
        if ((bits & 16) != 0) verticalSegment(canvas, l, mid, bottom);
        if ((bits & 32) != 0) verticalSegment(canvas, l, top, mid);
        if ((bits & 64) != 0) horizontalSegment(canvas, l, r, mid);
    }

    /** A hexagonal segment along y = cy from x0 to x1, with pointed ends. */
    private void horizontalSegment(Canvas canvas, float x0, float x1, float cy) {
        float h = segT / 2f, g = segT * 0.15f;
        x0 += g;
        x1 -= g;
        path.reset();
        path.moveTo(x0, cy);
        path.lineTo(x0 + h, cy - h);
        path.lineTo(x1 - h, cy - h);
        path.lineTo(x1, cy);
        path.lineTo(x1 - h, cy + h);
        path.lineTo(x0 + h, cy + h);
        path.close();
        canvas.drawPath(path, shapePaint);
    }

    private void verticalSegment(Canvas canvas, float cx, float y0, float y1) {
        float h = segT / 2f, g = segT * 0.15f;
        y0 += g;
        y1 -= g;
        path.reset();
        path.moveTo(cx, y0);
        path.lineTo(cx + h, y0 + h);
        path.lineTo(cx + h, y1 - h);
        path.lineTo(cx, y1);
        path.lineTo(cx - h, y1 - h);
        path.lineTo(cx - h, y0 + h);
        path.close();
        canvas.drawPath(path, shapePaint);
    }

    private void drawAnalog(Canvas canvas, float faceX, int hour, int minute) {
        float r = faceW / 2f, cx = faceX + r, cy = y + r;
        shapePaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 12; i++) {
            boolean major = i % 3 == 0;
            shapePaint.setStrokeWidth(r * (major ? 0.045f : 0.025f));
            radial(canvas, cx, cy, i * 30, r * (major ? 0.8f : 0.88f), r * 0.97f);
        }
        shapePaint.setStrokeWidth(r * 0.07f);
        radial(canvas, cx, cy, (hour % 12) * 30 + minute * 0.5f, 0, r * 0.5f);
        shapePaint.setStrokeWidth(r * 0.045f);
        radial(canvas, cx, cy, minute * 6, 0, r * 0.78f);
        shapePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.06f, shapePaint);
    }

    /** Line from radius {@code from} to {@code to} at {@code degrees} clockwise from 12. */
    private void radial(Canvas canvas, float cx, float cy, float degrees, float from, float to) {
        double a = Math.toRadians(degrees);
        float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        canvas.drawLine(cx + from * sin, cy - from * cos, cx + to * sin, cy - to * cos, shapePaint);
    }
}
