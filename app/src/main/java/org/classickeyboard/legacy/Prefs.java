package org.classickeyboard.legacy;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "classic_keyboard_preferences";
    static final String THEME = "theme";
    static final String SUGGESTIONS = "suggestions";
    static final String AUTOCORRECT = "autocorrect";
    static final String SOUND = "sound";
    static final String VIBRATION = "vibration";
    static final String POPUPS = "popups";
    static final String GLIDE = "glide";

    private Prefs() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context, String key, boolean fallback) {
        return get(context).getBoolean(key, fallback);
    }

    static boolean dark(Context context) {
        String theme = get(context).getString(THEME, "system");
        if ("dark".equals(theme)) return true;
        if ("light".equals(theme)) return false;
        int night = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
