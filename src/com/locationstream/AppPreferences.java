package com.locationstream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;


/**
*<code><pre>
* CLASS:
*  To handle App setting and preference
*
* RESPONSIBILITIES:
*
* COLABORATORS:
*
* USAGE:
* 	See each method.
*
*</pre></code>
*/
public class AppPreferences {

    private static final String TAG = "LSAPP_PREF";
    public static String PREFERENCE_FB_LOGIN = "fb_login";
    public static String PREFERENCE_FB_PASSWORD = "fb_password";
    public static String PREFERENCE_FS_LOGIN = "fs_login";
    public static String PREFERENCE_FS_PASSWORD = "fs_password";
    public static String PREFERENCE_STARTUP_TAB = "startup_tab";

    public static final String POI = "poi";

    private LocationSensorApp mLSApp;
    public static SharedPreferences mPref;

    public AppPreferences(LocationSensorApp lsapp) {
        mLSApp = lsapp;
        mPref = mLSApp.getSharedPreferences(Constants.PACKAGE_NAME, 0);
    }

    /**
     * Get the value of a key
     * @param key
     * @return
     */
    public static String getString(String key) {
        return mPref.getString(key, null);
    }

    /**
     * Set the value of a key
     * @param key
     * @return
     */
    public void setString(String key, String value) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(key, value);
        editor.commit();
    }
}
