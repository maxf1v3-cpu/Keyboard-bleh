package org.classickeyboard.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        getWindow().setStatusBarColor(Color.rgb(245, 246, 248));
        getWindow().setNavigationBarColor(Color.rgb(245, 246, 248));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(24));
        content.setBackgroundColor(Color.rgb(245, 246, 248));
        if (Build.VERSION.SDK_INT >= 30) {
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(dp(22) + bars.left, dp(20) + bars.top, dp(22) + bars.right, dp(24) + bars.bottom);
                return insets;
            });
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        TextView title = heading("Keyboard settings", 26, true);
        content.addView(title, params(-1, -2));
        TextView subtitle = body("Choose how the keyboard looks and responds. Changes take effect the next time the keyboard is shown.");
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        addSection(content, "Appearance");
        RadioGroup themes = new RadioGroup(this);
        themes.setOrientation(RadioGroup.HORIZONTAL);
        String currentTheme = prefs.getString(Prefs.THEME, "system");
        addTheme(themes, "System", "system", currentTheme);
        addTheme(themes, "Light", "light", currentTheme);
        addTheme(themes, "Dark", "dark", currentTheme);
        LinearLayout themeCard = card();
        themeCard.addView(themes, params(-1, -2));
        LinearLayout.LayoutParams themeParams = params(-1, -2);
        themeParams.topMargin = dp(8);
        content.addView(themeCard, themeParams);
        themes.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = findViewById(checkedId);
            if (checked != null) {
                String value = String.valueOf(checked.getTag());
                prefs.edit().putString(Prefs.THEME, value).apply();
                Toast.makeText(this, "Keyboard theme updated.", Toast.LENGTH_SHORT).show();
            }
        });

        addSection(content, "Typing");
        addSwitch(content, "Show suggestions", "Show locally generated word suggestions above the keys.", Prefs.SUGGESTIONS, true);
        addSwitch(content, "Auto-correction", "Correct a small number of clear one-letter typos using the offline dictionary.", Prefs.AUTOCORRECT, true);
        addSwitch(content, "Glide typing", "Trace across letters to enter a word. This is a lightweight offline approximation.", Prefs.GLIDE, true);
        addSwitch(content, "Long-press accents", "Hold supported letters to enter an accented character.", Prefs.POPUPS, true);

        addSection(content, "Feedback");
        addSwitch(content, "Key sounds", "Play the system key click sound while typing.", Prefs.SOUND, false);
        addSwitch(content, "Vibration", "Use a short vibration on key presses.", Prefs.VIBRATION, false);

        addSection(content, "About and privacy");
        TextView privacy = body("Classic Keyboard works offline. It has no Internet permission and includes no Google Play Services, Google APIs, network requests, voice service, online search, translation, analytics or cloud learning. The English word list is stored in the app. Typed text is not saved by this keyboard.");
        LinearLayout privacyCard = card();
        privacyCard.addView(privacy, params(-1, -2));
        LinearLayout.LayoutParams privacyParams = params(-1, -2);
        privacyParams.topMargin = dp(8);
        content.addView(privacyCard, privacyParams);

        TextView version = body("Classic Keyboard 1.0.0 · Android 8.0 and newer · target API 36");
        LinearLayout.LayoutParams versionParams = params(-1, -2);
        versionParams.topMargin = dp(16);
        content.addView(version, versionParams);

        setContentView(scroll);
    }

    private void addTheme(RadioGroup group, String label, String value, String current) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTag(value);
        button.setId(View.generateViewId());
        button.setChecked(value.equals(current));
        group.addView(button, new RadioGroup.LayoutParams(0, dp(48), 1f));
    }

    private void addSection(LinearLayout root, String title) {
        TextView section = heading(title, 15, true);
        LinearLayout.LayoutParams p = params(-1, -2);
        p.topMargin = dp(22);
        p.bottomMargin = dp(4);
        root.addView(section, p);
    }

    private void addSwitch(LinearLayout root, String title, String description, String key, boolean fallback) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(12), dp(14), dp(10));
        item.setBackground(rounded(Color.WHITE, dp(9), 0xFFE0E3E8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = body(title);
        label.setTextColor(Color.rgb(32, 33, 36));
        label.setTextSize(15);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(key, fallback));
        row.addView(toggle, params(-2, -2));
        item.addView(row, params(-1, -2));

        TextView detail = body(description);
        detail.setTextSize(12);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(3);
        item.addView(detail, detailParams);

        CompoundButton.OnCheckedChangeListener listener = (button, checked) -> prefs.edit().putBoolean(key, checked).apply();
        toggle.setOnCheckedChangeListener(listener);
        item.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));

        LinearLayout.LayoutParams p = params(-1, -2);
        p.topMargin = dp(8);
        root.addView(item, p);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(14), dp(12), dp(14), dp(12));
        layout.setBackground(rounded(Color.WHITE, dp(9), 0xFFE0E3E8));
        return layout;
    }

    private TextView heading(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(32, 33, 36));
        text.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return text;
    }

    private TextView body(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(13);
        text.setTextColor(Color.rgb(95, 99, 104));
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, float radius, int stroke) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
