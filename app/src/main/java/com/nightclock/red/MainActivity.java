package com.nightclock.red;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Settings and permission setup. */
public class MainActivity extends Activity {
    private static final int TEXT = Color.rgb(220, 220, 220);
    private static final int MUTED = Color.rgb(150, 150, 150);
    private static final int RED = Color.rgb(230, 40, 40);

    private LinearLayout column;
    private ClockView preview;
    private View fontRow, formatRow;
    private SeekBar hue;
    private final List<View> swatches = new ArrayList<>();
    private Button overlayButton, notifyButton, batteryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(column);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            column.setPadding(pad + insets.getSystemWindowInsetLeft(),
                    pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(),
                    pad + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = text(getString(R.string.app_name), 26, RED);
        column.addView(title);

        Switch enabled = new Switch(this);
        enabled.setText("Show clock when charging");
        enabled.setTextColor(TEXT);
        enabled.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        enabled.setChecked(Prefs.enabled(this));
        enabled.setOnCheckedChangeListener((b, on) -> {
            Prefs.setEnabled(this, on);
            if (on) ChargeService.start(this);
            else ChargeService.stop(this);
        });
        add(enabled, 16);

        add(checkBox("Only while the phone is locked. If I plug in while using it, "
                + "wait until I lock it.", Prefs.onlyWhenIdle(this),
                on -> Prefs.setOnlyWhenIdle(this, on)), 8);

        addStyleSection();

        add(heading("Clock orientation"), 24);
        add(radioGroup(new String[]{"Follow phone", "Portrait", "Landscape"},
                Prefs.orientation(this), false, value -> Prefs.setOrientation(this, value)), 4);

        add(heading("Brightness"), 24);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Math.round(Prefs.level(this) * 100));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                Prefs.setLevel(MainActivity.this, progress / 100f);
                preview.setLevel(progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        add(seek, 8);
        add(text("Tip: on the clock, swipe up or down to adjust brightness. "
                + "Press back to close it.", 14, MUTED), 4);
        add(button("Preview clock", () -> startActivity(new Intent(this, ClockActivity.class)
                .putExtra(ClockActivity.EXTRA_PREVIEW, true))), 8);

        add(heading("Permissions"), 24);
        add(text("For the clock to appear by itself when you plug in, allow these:", 14, MUTED), 4);
        overlayButton = button("", () -> open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        add(overlayButton, 8);
        notifyButton = button("", () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        add(notifyButton, 8);
        batteryButton = button("", () -> open(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));
        add(batteryButton, 8);
        if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) {
            add(heading("Xiaomi / Redmi"), 24);
            add(text("In \"Other permissions\", allow both \"Display pop-up windows while "
                    + "running in the background\" and \"Show on Lock screen\".", 14, MUTED), 4);
            add(button("Other permissions", () -> openXiaomi(
                    new Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter",
                                    "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", getPackageName()))), 8);
            add(text("Then turn on Autostart for Red Night Clock.", 14, MUTED), 8);
            add(button("Autostart", () -> openXiaomi(new Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"))), 8);
        } else {
            add(text("Some phones (Huawei, Oppo, Vivo) also need \"Autostart\" and "
                    + "\"Show pop-up windows while running in background\" turned on for this "
                    + "app in its App info settings.", 14, MUTED), 8);
            add(button("Open app info", this::openAppInfo), 8);
        }

        add(heading("Screen saver (optional)"), 24);
        add(text("If your phone has a Screen saver setting, you can also choose "
                + "Red Night Clock there and set it to start while charging.", 14, MUTED), 4);
        add(button("Open screen saver settings",
                () -> open(new Intent(Settings.ACTION_DREAM_SETTINGS))), 8);

        setContentView(scroll);
        styleChanged();
        ChargeService.start(this);
    }

    /** Live preview plus face, font, color, time format and extra-line options. */
    private void addStyleSection() {
        add(heading("Clock style"), 24);

        FrameLayout frame = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.BLACK);
        border.setStroke(dp(1), Color.rgb(70, 70, 70));
        border.setCornerRadius(dp(8));
        frame.setBackground(border);
        int inset = dp(2);
        frame.setPadding(inset, inset, inset, inset);
        preview = new ClockView(this);
        frame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
        add(frame, 8);

        add(label("Face"), 12);
        add(radioGroup(new String[]{"Digital", "LED", "Analog"}, Prefs.face(this), true,
                value -> {
                    Prefs.setFace(this, value);
                    styleChanged();
                }), 4);

        LinearLayout fonts = new LinearLayout(this);
        fonts.setOrientation(LinearLayout.VERTICAL);
        fonts.addView(label("Font"));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Prefs.FONT_NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return styleFont((TextView) super.getView(position, convertView, parent), position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleFont((TextView) super.getDropDownView(position, convertView, parent),
                        position);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Prefs.font(this), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setFont(MainActivity.this, position);
                styleChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        fonts.addView(spinner);
        fontRow = fonts;
        add(fontRow, 12);

        add(label("Color"), 12);
        LinearLayout row = null;
        for (int i = 0; i < Prefs.COLORS.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                add(row, i == 0 ? 8 : 12);
            }
            int color = Prefs.COLORS[i];
            View swatch = new View(this);
            swatch.setTag(color);
            swatch.setOnClickListener(v -> {
                Prefs.setColor(this, color);
                styleChanged();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(14);
            row.addView(swatch, lp);
            swatches.add(swatch);
        }
        add(text("Or pick any color:", 14, MUTED), 12);
        hue = new SeekBar(this);
        hue.setMax(359);
        GradientDrawable rainbow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
                        Color.MAGENTA, Color.RED});
        rainbow.setCornerRadius(dp(4));
        hue.setProgressDrawable(rainbow);
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                Prefs.setColor(MainActivity.this, Color.HSVToColor(new float[]{progress, 1f, 1f}));
                styleChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        add(hue, 8);
        add(text("Red is the easiest color on your eyes at night.", 14, MUTED), 8);

        LinearLayout formats = new LinearLayout(this);
        formats.setOrientation(LinearLayout.VERTICAL);
        formats.addView(label("Time format"));
        formats.addView(radioGroup(new String[]{"12-hour with AM/PM", "12-hour", "24-hour"},
                Prefs.format(this), false, value -> {
                    Prefs.setFormat(this, value);
                    styleChanged();
                }));
        formatRow = formats;
        add(formatRow, 12);

        add(checkBox("Show battery level", Prefs.showBattery(this), on -> {
            Prefs.setShowBattery(this, on);
            styleChanged();
        }), 8);
        add(checkBox("Show date", Prefs.showDate(this), on -> {
            Prefs.setShowDate(this, on);
            styleChanged();
        }), 0);
    }

    /** Applies style settings to the preview and shows only the options the face uses. */
    private void styleChanged() {
        preview.reload();
        int face = Prefs.face(this);
        fontRow.setVisibility(face == Prefs.FACE_DIGITAL ? View.VISIBLE : View.GONE);
        formatRow.setVisibility(face == Prefs.FACE_ANALOG ? View.GONE : View.VISIBLE);
        int selected = Prefs.color(this);
        float[] hsv = new float[3];
        Color.colorToHSV(selected, hsv);
        hue.setProgress(Math.round(hsv[0]));
        for (View swatch : swatches) {
            int color = (Integer) swatch.getTag();
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            if (color == selected) dot.setStroke(dp(3), Color.WHITE);
            else dot.setStroke(dp(1), Color.rgb(70, 70, 70));
            swatch.setBackground(dot);
        }
    }

    private TextView styleFont(TextView view, int font) {
        view.setTypeface(Prefs.typeface(font));
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        return view;
    }

    @Override
    protected void onResume() {
        super.onResume();
        overlayButton.setText(status("Display over other apps", Settings.canDrawOverlays(this)));
        boolean notify = Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notifyButton.setText(status("Notifications", notify));
        boolean unrestricted = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(getPackageName());
        batteryButton.setText(status("Unrestricted battery use", unrestricted));
        // Brightness can be changed by swiping on the clock.
        preview.reload();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        onResume();
    }

    private static String status(String name, boolean granted) {
        return name + (granted ? "  ✓ allowed" : "  — tap to allow");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "This phone doesn't have that setting", Toast.LENGTH_LONG).show();
        }
    }

    /** Xiaomi's own settings pages move between versions; fall back to App info. */
    private void openXiaomi(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            openAppInfo();
        }
    }

    private void openAppInfo() {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void add(View view, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        column.addView(view, lp);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView heading(String s) {
        return text(s, 18, RED);
    }

    private TextView label(String s) {
        return text(s, 15, TEXT);
    }

    private CheckBox checkBox(String label, boolean checked, OnToggle onToggle) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT);
        box.setChecked(checked);
        box.setOnCheckedChangeListener((b, on) -> onToggle.toggled(on));
        return box;
    }

    private interface OnToggle {
        void toggled(boolean on);
    }

    /** Options numbered from 0; {@code onPick} receives the chosen index. */
    private RadioGroup radioGroup(String[] labels, int selected, boolean horizontal,
                                  IntConsumer onPick) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(labels[i]);
            option.setTextColor(TEXT);
            if (horizontal) {
                group.addView(option, new RadioGroup.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                group.addView(option);
            }
            if (i == selected) group.check(option.getId());
            int value = i;
            option.setOnCheckedChangeListener((b, on) -> {
                if (on) onPick.accept(value);
            });
        }
        return group;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
