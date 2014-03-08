package com.locationstream;

import static com.locationstream.Constants.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;

import com.locationstream.LocationSensorApp.LSAppLog;

/**
 *<code><pre>
 * CLASS:
 *  Timer task is a stand alone task that performs periodical jobs.
 *    1. poll the wifi passive scan result before async notification from wifi is available.
 *
 * RESPONSIBILITIES:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public final class LocationTimerTask extends BroadcastReceiver {

    private static final String TAG = "LSAPP_TimerTask";
    public static final String TIMERTASK_STARTSCAN =  PACKAGE_NAME + ".startscan";

    private Context mContext;
    private LocationDetection mLSDet;
    private LocationSensorManager mLSMan;
    private Timer mTimer;
    private AlarmManager mAlarmMan;

    private Map<String, PendingIntent> mTasks;

    /**
     * this class encapsulate methods that exploit sensor-hub to detect user movement from accelo-meter.
     */
    
    /**
     * Constructor
     * @param context
     * @param hdl
     */
    public LocationTimerTask(LocationDetection lsdet) {
        mLSDet = lsdet;
        mContext = (Context)LocationSensorManager.getInstance();
        mTasks = new HashMap<String, PendingIntent>();
        mAlarmMan = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(TIMERTASK_STARTSCAN);
        mContext.registerReceiver(this, filter);

        // only enable for spyder P2 with accelo-meter available
        // mSensorHub = new LocationSensorHub(mContext, this);
        // mSensorHub.start();
    }

    /**
     * finalizer, un-reg the listener, called by location detection upon destroy.
     */
    public void clean() {
        mContext.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LSAppLog.d(TAG, ": onReceiver : Got timer Event :" + action);
        Message msg = mLSDet.getDetectionHandler().obtainMessage();

        if (TIMERTASK_STARTSCAN.equals(action)) {
            msg.what = LocationDetection.Msg.START_SCAN;
            LSAppLog.d(TAG, ": onReceive : Got scan timer expired Event :" + action);
        }
        mLSDet.getDetectionHandler().sendMessage(msg);
    }

    /**
     * start repeated wifi polling task, add the task to the hash map.
     */
    public void startPeriodicalWifiPolling(long cycle) {
        PendingIntent pi = mTasks.get(TIMERTASK_STARTSCAN);
        if (pi == null) {
            Intent i = new Intent(TIMERTASK_STARTSCAN);
            pi = PendingIntent.getBroadcast(mContext, 0, i,  PendingIntent.FLAG_UPDATE_CURRENT);
            mTasks.put(TIMERTASK_STARTSCAN, pi);
            long firstWake = System.currentTimeMillis() + cycle;
            mAlarmMan.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstWake, cycle, pi);
            LSAppLog.d(TAG, "startPeriodicalWifiPolling : started");
        }
    }

    /**
     * stop the running periodical wifi polling
     */
    public void stopPeriodicalWifiPolling() {
        PendingIntent pi = mTasks.get(TIMERTASK_STARTSCAN);
        if (pi != null) {
            pi.cancel();
            mAlarmMan.cancel(pi);
            mTasks.remove(TIMERTASK_STARTSCAN);
            LSAppLog.d(TAG, "stopPeriodicalWifiPolling : stopped");
        }
    }

    /**
     * TDD drives out roles and IF between collaborators.
     */
    public static class Test {
        /**
         * start the timer task, return the job Id
         * @param cycle, the periodical cycle
         * @return the job id
         */
        public static void startPeriodicalPolling() {
        }
        /**
         * stop the on-going periodical job
         * @param jobId
         */
        public static void stopPeriodicalPolling(int jobId) {
        }
    }
}
