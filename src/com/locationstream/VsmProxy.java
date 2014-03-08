package com.locationstream;

import static com.locationstream.Constants.*;
import android.content.Intent;

import com.locationstream.LocationSensorApp.LSAppLog;

public final class VsmProxy {
    public static final String TAG = "LSAPP_VSM";

    private LocationSensorApp mLSApp;

    // ok, checkin module should be in lsapp, for now, it lives inside lsman.
    @SuppressWarnings("unused")
    private VsmProxy() { }
    public VsmProxy(LocationSensorApp lsapp) {
        mLSApp = lsapp;
    }

    /**
     * register vsm listeners and unregister upon start and stop
     */
    public void start() {
        //mReceiver.start();
    }
    public void stop() {
        //mReceiver.stop();
    }

    /**
     * broadcast entering into poi intent from location manager. LOCATION_MATCHED_EVENT
     * @param poi
     */
    public void sendVSMLocationUpdate(String poi) {
        Intent locintent = new Intent(VSM_PROXY);
        StringBuilder sb = new StringBuilder();
        sb.append(VSM_LS_PARAM_SETVALUE);
        sb.append("p0="+poi+";");
        sb.append(VSM_LS_PARAM_END);
        locintent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        LSAppLog.d(TAG, "notify VSM: "+ sb.toString());
        mLSApp.sendBroadcast(locintent, PERMISSION);
        LSAppLog.dbg(mLSApp, DEBUG_OUT, poi, locintent.toUri(0));
    }
}
