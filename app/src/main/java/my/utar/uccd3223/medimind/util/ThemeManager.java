package my.utar.uccd3223.medimind.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private static final String PREFS_NAME = "medimind_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemeManager() {}

    public static void applyStoredTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        return mode == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static void toggleTheme(Context context) {
        boolean darkMode = isDarkMode(context);
        int newMode = darkMode ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, newMode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(newMode);
    }
}
