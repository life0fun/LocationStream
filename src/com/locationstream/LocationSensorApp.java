package com.locationstream;

import static com.locationstream.Constants.*;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
/**
 *<code><pre>
 * CLASS:
 *  Our application class, Getting init before anything started.
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
public class LocationSensorApp extends Application {

    private static final String TAG = "LSApp_App";
    
    // all the intent action for the app defined here!
    public static final String LS_ACTION_NEW_LOCATION = "com.motorola.locationsensor.newlocation";
    public static final String LS_ACTION_VENUES = "com.motorola.locationsensor.venues";
    public static final String LS_ACTION_USERS = "com.motorola.locationsensor.users";
    public static final String LS_ACTION_CHECKINS = "com.motorola.locationsensor.checkins";

    public static final String LS_ACTION_LOGIN_FB = "com.motorola.locationsensor.login_fb";
    public static final String LS_ACTION_LOGIN_FS = "com.motorola.locationsensor.login_fs";


    private String mVersion = null;

    //private RemoteResourceManager mRemoteResourceManager;
    AppPreferences mAppPref;

    private LocationSensorManager mLSMan = null;  // available after location sensor manager service started.
    //private NoCellLocationManager mLSMan = null;  // available after location sensor manager service started.
    private VsmProxy mVSMProxy = null;
    //public LocationGraph mGraph;
    private WSCheckin mCheckin = null;

    @Override
    public void onCreate() {
        super.onCreate();

        mVersion = Utils.getVersionString(this, PACKAGE_NAME);

        // Setup Prefs
        mAppPref = new AppPreferences(this);

        mVSMProxy = new VsmProxy(this);
        mCheckin = new WSCheckin(this);
        
        // XXX for location graph
        // mGraph = new LocationGraph(this);
        // mGraph.buildGraphIfEmpty();

        LSAppLog.d(TAG, "LSAPP constructor");
    }

    @Override
    public void onTerminate() {
        mVSMProxy.stop();
    }

    /**
     * callback for location man to set reference, init all services when lsman ready
     * @param lsman
     */
    @Deprecated
    public void setNoCellLSMan(NoCellLocationManager lsman) {
        mLSMan = lsman;
        mVSMProxy.start(); // notify upper layer client location app is ready!
        LSAppLog.d(TAG, "setNoCellLSMan : LSMan ready....spread out!!!");
    }

    public void setLSMan(LocationSensorManager lsman) {
        mLSMan = lsman;
        mVSMProxy.start(); // notify upper layer client location app is ready!
        LSAppLog.d(TAG, "setLSMan : LSMan ready....spread out!!!");
    }

    /**
     * return the reference to location manager
     * @return
     */
    public LocationSensorManager getLSMan() {
        return mLSMan;
    }

    /**
     * get the reference to VSM proxy
     */
    public VsmProxy getVsmProxy() {
        return mVSMProxy;
    }
    
    /**
     * get the reference to ws checkin engine
     * @return
     */
    public WSCheckin getCheckin() {
        return mCheckin;
    }

    /**
     * out logger
     * @author e51141
     *
     */
    public static class LSAppLog {
        public static void i(String tag, String msg) {
            if (LOG_VERBOSE) Log.i(tag, msg);
        }
        public static void e(String tag, String msg) {
            Log.e(tag, msg);
        }
        public static void d(String tag, String msg) {
            if (LOG_VERBOSE) Log.d(tag, msg);
        }
        public static void pd(String tag, String msg) {
            Log.d(tag, msg);  // always platform debug logging
        }
        public static void dbg(Context ctx, String direction, String... msg) {
            //if(!LOG_VERBOSE) return;

            ContentValues values = new ContentValues();
            StringBuilder sb = new StringBuilder();

            if (direction.equals(DEBUG_OUT)) {
                values.put(DEBUG_STATE, msg[0]);
            }

            for (String s : msg) {
                sb.append(s);
                sb.append(" : ");
            }

            values.put(DEBUG_COMPKEY, VSM_PKGNAME);
            values.put(DEBUG_COMPINSTKEY,PACKAGE_NAME);
            values.put(DEBUG_DIRECTION, direction);
            values.put(DEBUG_DATA, sb.toString());
            Log.d("LSAPP_DBG", sb.toString());
            try {
                ctx.getContentResolver().insert(DEBUG_DATA_URI, values);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * @return current app version
     */
    public String getVersion() {
        if (mVersion != null) {
            return mVersion;
        } else {
            return "";
        }
    }

    /**
     * start location manage
     */
    public void startLocationManager() {
        Intent myIntent = new Intent(this, LocationSensorManager.class);
        //Intent myIntent = new Intent(this, NoCellLocationManager.class);
        myIntent.putExtra(INTENT_PARM_STARTED_FROM_BOOT, false);
        ComponentName component = this.startService(myIntent);
        if (component != null) {
            LSAppLog.d(TAG, "Location Sensor Services started: " + component.toShortString());
        } else {
            LSAppLog.d(TAG, "Location Sensor Services start failed.");
        }
    }

    public static void sendMessage(Handler hdl, int what, Object obj, Bundle data) {
        LSAppLog.e(TAG, "Sending Message to " + hdl + ": msg :" + what);
        Message msg = hdl.obtainMessage();
        msg.what = what;
        if (obj != null)
            msg.obj = obj;
        if (data != null)
            msg.setData(data);
        hdl.sendMessage(msg);
    }
}
