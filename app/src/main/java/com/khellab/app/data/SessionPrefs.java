package com.khellab.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Remembers the current testing session (which athlete, which venue) using SharedPreferences,
 * so the coach does not have to retype it before every test. Every test screen reads from here.
 */
public class SessionPrefs {

    private static final String PREFS_NAME = "khellab_session";
    private static final String KEY_NAME = "athlete_name";
    private static final String KEY_AGE = "athlete_age";
    private static final String KEY_GENDER = "athlete_gender";
    private static final String KEY_VENUE = "venue_name";
    private static final String KEY_LAT = "venue_lat";
    private static final String KEY_LNG = "venue_lng";
    private static final String KEY_DEVICE_ID = "device_id";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveAthlete(Context context, String name, int age, String gender) {
        prefs(context).edit()
                .putString(KEY_NAME, name)
                .putInt(KEY_AGE, age)
                .putString(KEY_GENDER, gender)
                .apply();
    }

    public static void saveVenueName(Context context, String venue) {
        prefs(context).edit().putString(KEY_VENUE, venue).apply();
    }

    public static void saveVenueLocation(Context context, double lat, double lng) {
        // SharedPreferences has no putDouble, so we store the text form of the number
        prefs(context).edit()
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LNG, String.valueOf(lng))
                .apply();
    }

    public static String getAthleteName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static int getAthleteAge(Context context) {
        return prefs(context).getInt(KEY_AGE, 0);
    }

    public static String getAthleteGender(Context context) {
        return prefs(context).getString(KEY_GENDER, "Male");
    }

    public static String getVenueName(Context context) {
        return prefs(context).getString(KEY_VENUE, "");
    }

    public static double getVenueLat(Context context) {
        return Double.parseDouble(prefs(context).getString(KEY_LAT, "0"));
    }

    public static double getVenueLng(Context context) {
        return Double.parseDouble(prefs(context).getString(KEY_LNG, "0"));
    }

    /** A random id made once per install, used to name this phone's documents in Firebase. */
    public static String getDeviceId(Context context) {
        String id = prefs(context).getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8);
            prefs(context).edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }
}
