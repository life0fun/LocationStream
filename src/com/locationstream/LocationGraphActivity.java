package com.locationstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.locationstream.LocationSensorApp.LSAppLog;

public class LocationGraphActivity extends Activity {
    private static String TAG = "LSAPP_GraphUI";

    private LocationSensorApp mLSApp;	// reference to app

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLSApp = (LocationSensorApp)getApplication();
        LSAppLog.d(TAG, "creating graph activity dialog");
        //promptDialog(mLSApp.mGraph.predictNextDestinations());
    }

    public void promptDialog(String msg) {
        LSAppLog.d(TAG, "creating graph activity dialog");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
        .setCancelable(false)
        .setNegativeButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                LocationGraphActivity.this.finish();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
