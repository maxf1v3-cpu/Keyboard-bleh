package org.classickeyboard.legacy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SetupActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(245, 246, 248));
        getWindow().setNavigationBarColor(Color.rgb(245, 246, 248));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(24));
        content.setBackgroundColor(Color.rgb(245, 246, 248));
        if (Build.VERSION.SDK_INT >= 30) {
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(dp(22) + bars.left, dp(24) + bars.top, dp(22) + bars.right, dp(24) + bars.bottom);
                return insets;
            });
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        TextView title = text("Classic Keyboard", 28, Color.rgb(32, 33, 36), true);
        content.addView(title, params(-1, -2));
        TextView subtitle = text("A familiar keyboard, kept local.", 16, Color.rgb(95, 99, 104), false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        status = text("Checking keyboard status…", 14, Color.rgb(95, 99, 104), false);
        LinearLayout statusCard = card();
        statusCard.addView(status, params(-1, -2));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(26);
        content.addView(statusCard, cardParams);

        Button enable = button("1. Enable Classic Keyboard");
        LinearLayout.LayoutParams buttonParams = params(-1, dp(54));
        buttonParams.topMargin = dp(16);
        content.addView(enable, buttonParams);
        enable.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); }
            catch (RuntimeException e) { toast("Open Settings → System → Keyboard → On-screen keyboards."); }
        });

        Button choose = button("2. Select Classic Keyboard");
        LinearLayout.LayoutParams chooseParams = params(-1, dp(54));
        chooseParams.topMargin = dp(10);
        content.addView(choose, chooseParams);
        choose.setOnClickListener(v -> {
            try {
                InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) manager.showInputMethodPicker();
            } catch (RuntimeException e) { toast("Use your device's keyboard picker to select Classic Keyboard."); }
        });

        Button settings = button("Keyboard preferences");
        LinearLayout.LayoutParams settingsParams = params(-1, dp(50));
        settingsParams.topMargin = dp(10);
        content.addView(settings, settingsParams);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        TextView testLabel = text("Try it", 18, Color.rgb(32, 33, 36), true);
        LinearLayout.LayoutParams testLabelParams = params(-1, -2);
        testLabelParams.topMargin = dp(28);
        content.addView(testLabel, testLabelParams);
        EditText test = new EditText(this);
        test.setSingleLine(true);
        test.setTextSize(16);
        test.setHint("Tap here to test the keyboard");
        test.setPadding(dp(14), dp(12), dp(14), dp(12));
        test.setBackground(rounded(Color.WHITE, dp(9), 0xFFE0E3E8));
        test.setOnClickListener(v -> {
            test.setFocusableInTouchMode(true);
            test.requestFocus();
        });
        LinearLayout.LayoutParams testParams = params(-1, dp(54));
        testParams.topMargin = dp(10);
        content.addView(test, testParams);

        TextView privacy = text("Privacy: the keyboard has no Internet permission, sends no keystrokes anywhere, and keeps its English word list on this device. Voice typing, online search, translation and cloud learning are not included.",
                13, Color.rgb(95, 99, 104), false);
        privacy.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams privacyParams = params(-1, -2);
        privacyParams.topMargin = dp(24);
        content.addView(privacy, privacyParams);

        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (status == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        boolean selected = false;
        if (manager != null) {
            for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) enabled = true;
            }
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (current != null && current.startsWith(getPackageName() + "/")) selected = true;
        if (selected) status.setText("Status: Classic Keyboard is enabled and selected.");
        else if (enabled) status.setText("Status: enabled. Select it using the keyboard picker.");
        else status.setText("Status: not enabled yet. Enable it in Android's keyboard settings.");
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(16), dp(14), dp(16), dp(14));
        layout.setBackground(rounded(Color.WHITE, dp(10), 0xFFE0E3E8));
        return layout;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(0xFF4285F4, dp(9), 0xFF4285F4));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != color) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
