package com.realtimehitchhiker.hitchgo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.StringDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Objects;

/**
 * Created by gishoshan on 06-Dec-17.
 */

public class LocaleUtils {

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ENGLISH, HEBREW})
    public @interface LocaleDef {
        String[] SUPPORTED_LOCALES = {ENGLISH, HEBREW};
    }

    public static final String ENGLISH = "en";
    public static final String HEBREW = "iw";

    public static void initialize(Context context) {
        String lang = getPersistedData(context, Locale.getDefault().getLanguage());
        setLocale(context, lang);
    }

    public static void initialize(Context context, @LocaleDef String defaultLanguage) {
//        String lang = getPersistedData(context, defaultLanguage);
        setLocale(context, defaultLanguage);
    }

    public static void initializeEnglish(Context context) {
        setLocale(context, ENGLISH);
    }

    public static String getLanguage(Context context) {
        return getPersistedData(context, Locale.getDefault().getLanguage());
    }

    public static boolean setLocale(Context context, @LocaleDef String language) {
        persist(context, language);
        return updateResources(context, language);
    }

//    public static boolean setLocale(Context context, int languageIndex) {
//        persist(context, language);
//        if (languageIndex >= LocaleDef.SUPPORTED_LOCALES.length) {
//            return false;
//        }
//
//        return updateResources(context, LocaleDef.SUPPORTED_LOCALES[languageIndex]);
//    }

    private static String getPersistedData(Context context, String defaultLanguage) {
        Log.d("LOCAL_UTILS", "getPersistedData / default: " + defaultLanguage);
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        String lang = sharedPref.getString(context.getString(R.string.pref_language), defaultLanguage);
        Log.d("LOCAL_UTILS", "getPersistedData / return: " + lang);
        return lang;
    }

    public static int getPersistedDataPosition(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        String languageSaved = sharedPref.getString(context.getString(R.string.pref_language), ENGLISH);
        int pos = 0;
        for (String language : LocaleDef.SUPPORTED_LOCALES) {
            if(Objects.equals(language, languageSaved))
                break;
            else
                pos++;
        }

        return pos;
    }

    private static void persist(Context context, String language) {
        Log.d("LOCAL_UTILS", "persist " + language);
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.pref_language), language);
        editor.apply();
    }

    private static boolean updateResources(Context context, String language) {
        Log.d("LOCAL_UTILS", language);
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();

        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        return true;
    }
}