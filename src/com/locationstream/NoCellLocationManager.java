package com.locationstream;
import static com.locationstream.Constants.*;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.DateUtils;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.dbhelper.LocationDatabase;
import com.locationstream.dbhelper.LocationDatabase.LocTimeTable.Tuple;

/**
 * This logic is mainly for proof of concept use...it request location update from platform all the time
 * and is not power conscious. Deprecated for now.
 *<code><pre>
 * CLASS:
 *  implements background location track service mother.
 *
 * RESPONSIBILITIES:
 *  Coordinate all the components to tracking and logging location information.
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
@Deprecated
@SuppressWarnings("unused")
public final class NoCellLocationManager extends LocationSensorManager {
    private static final String TAG = "LSAPP_NoCell";

    public interface Msg {
        int START_MONITORING 		= 100;    // start monitoring
        int STOP_MONITORING 		= 101;     // start monitoring
        int LOCATION_MATCHED_EVENT 	= 102; 		   // fire event, done
        int KEEP_MONITORING			= 103;

        int HEARTBEAT_TIMER_EXPIRED = 200; 	  // expired every 2 housr with idle
        int START_SELF_HEALING 		= 201;	  // start self healing
        int LOCATION_TIMER_EXPIRED  = 202;

        int CELLTOWER_CHANGED 	    = 300;  // get network fix
        int CELLTOWER_DONE 			= 302;

        int BEACONSCAN_RESULT		= 401;
        int DETECTION_ENTERING		= 402;
        int DETECTION_LEAVING		= 403;
        int NULL 					= 99999;
    }

    // expose bindalbe location sensor APIs
    //private LocationSensorEngineApiHandler mAPIHandler;
    //private VSMApiHandler mVSMHandler;   // IF to virtual sensor manager

    private enum State {UNINITIALIZED, CREATED, RUNNING, TIMEREXPIRED }
    private State   mState = State.UNINITIALIZED;
    private static NoCellLocationManager _sinstance;

    private  WorkHandler mWorkHandler;
    private  MessageHandler mHandler;

    //LocationSensorApp       mLSApp;
    //LocationMonitor         mLocMon;
    //TelephonyMonitor        mTelMon;
    //LocationDetection mDetection;

    private AlarmManager mAlarmMan;
    private LocationStore mHealer;
    private PendingIntent mTimerExpiredIntent = null;   // the intent give to alarm manager for broadcast.
    private TimerEventReceiver mTimerEventRecvr;

    private String mCurPOI;

    // lat, lgn, starttime to uniquely define a loc row in db
    private double mCurLat = 0.0;
    private double mCurLgt = 0.0;
    private long   mCurAccuracy = 0;
    private long mCurStartTime = 0;
    private long mNewCellStartTime = 0;   // set together with scheduling of 15 min timer.

    //WSCheckin mCheckin;
    BeaconSensors mBeacon;

    /**
     * singleton constructor
     */
    public static NoCellLocationManager getInstance() {
        if (_sinstance == null) {
            throw new IllegalStateException("LocationSensorManager is not initialized");
            //s_instance = new LocationSensorManager();
        }
        return _sinstance;
    }

    private void _initialize() {
        _sinstance = this;
        mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());

        //populateAllVirtualSensors();
        mLSApp = (LocationSensorApp)getApplication();

        mAlarmMan = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        mHealer = new LocationStore(this);
        mTimerEventRecvr = new TimerEventReceiver(this, mHandler);

        mState = State.CREATED; // created
    }

    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        _initialize();
        LSAppLog.i(TAG, "onCreate() called");
    }

    /**
     * @see android.app.Service#onStart(Intent,int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // real service started here!!
        if (mState.ordinal() >= State.RUNNING.ordinal()) {
            LSAppLog.i(TAG, "onStartCommand : already running, do nothing");
            if (intent != null && true == intent.getBooleanExtra(INTENT_PARM_STARTED_FROM_VSM, false)) {
                LSAppLog.i(TAG, "onStartCommand : receiving VSM Init complete after running, send created");
                handleVSMInitComplete();  // may get here after poi detection done...and sent transient will confuse vsm.
            }
            //hijack test here
            //mHealer.fixAccuracy(true);
            return START_STICKY;
        }

        mState = State.RUNNING;   // running... should not have any race condition here!
        mCurLat = mCurLgt = 0.0;
        mCurAccuracy = mCurStartTime = mNewCellStartTime = 0;
        LSAppLog.i(TAG, "onStartCommand start running...");

        mDetection = new LocationDetection(this, mHandler);
        mTelMon = new TelephonyMonitor(this, mHandler, mDetection.getDetectionHandler());
        mTelMon.startTelMon();
        mBeacon = new BeaconSensors(this, mTelMon);
        mLocMon = new LocationMonitor(this, mHandler);
        mDetection.setTelMonBeacon(mTelMon, mBeacon);	// set telmon now!!

        mTimerEventRecvr.start();
        mHealer.scheduleConsolidationJob();  // setup heal time once started!

        // set the upper layer know the reference to lsman
        mLSApp.setNoCellLSMan(this);

        // log into db when service restarted
        logServiceRestarted();

        // fire a celltower push update when started
        mHandler.sendEmptyMessage(Msg.START_MONITORING);  // ok, start mon

        return START_STICKY;
    }

    public Handler getLSManHandler() {
        return mHandler;
    }

    /**
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cancelAlarmTimer();

        mTelMon.stopTelMon();  // unregister cell change first
        if (mTimerEventRecvr != null) mTimerEventRecvr.stop();
        stopLocationMonitor();
        mLocMon.cleanup();
        mDetection.cleanup();
        mBeacon.cleanupReceivers();

        updateCurrentLocationDuration(System.currentTimeMillis());

        LSAppLog.dbg(this, DEBUG_INTERNAL, "service killed...", "update current location time..." );
    }


    /**
     * if null pending intent, means no pending 15 min timer schedule
     */
    public final PendingIntent getPendingIntent() {
        return mTimerExpiredIntent;
    }

    private final void setPendingIntent(Intent intent) {
        if (intent != null) {
            //mTimerExpiredIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            mTimerExpiredIntent = PendingIntent.getBroadcast(this, 0, intent,  PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            LSAppLog.i(TAG, "setPendingIntent : setting with intent :" + intent.toString());
        } else {
            mTimerExpiredIntent = null;
            LSAppLog.i(TAG, "setPendingIntent : nullify the pending intent :");
        }
    }

    private final void cancelAlarmTimer() {
        mAlarmMan.cancel(mTimerExpiredIntent);
        if (mTimerExpiredIntent != null) {
            LSAppLog.i(TAG, "cancelAlarmTimer : canceling existing pending 15 min timer" + mTimerExpiredIntent.toString());
            mTimerExpiredIntent.cancel();
            mTimerExpiredIntent = null;
        }
    }

    // Give a location timeout pending intent to alarm manager and ask it to fire at certain time.
    private void scheduleAlarmTimer(long delay) {
        // first, cancel all the pending alarm
        cancelAlarmTimer();

        long nowtime = System.currentTimeMillis();
        // schedule a new alarm with new pending intent
        Intent timeoutIntent = new Intent(ALARM_TIMER_LOCATION_EXPIRED); // create intent with this action
        timeoutIntent.putExtra(ALARM_TIMER_SET_TIME, nowtime );
        LSAppLog.i(TAG, "scheduleAlarmTimer with Time :" + nowtime);
        setPendingIntent(timeoutIntent);

        mAlarmMan.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+delay, mTimerExpiredIntent);
        LSAppLog.i(TAG, "scheduleAlarmTimer : " + delay/60000 + " min later from : " + SystemClock.elapsedRealtime());
    }

    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }

    private interface ErrTxt {
        String PREFIX_PC = "processMessage(): ";
        String PREFIX_0 = "celltower changed... ";
        String PREFIX_1 = PREFIX_0+" first time app is running. ";
        String PREFIX_2 = PREFIX_PC+" Start location monitor upon first time started!";
        String PREFIX_3 = PREFIX_PC+" onLocationUpdate available...";
    }

    private void processMessage(android.os.Message msg) {
        switch (msg.what) {
        case Msg.NULL:
            LSAppLog.i(TAG, "processMessage() : MSG_NULL");
            break;
        case Msg.START_MONITORING:
            // start network loc listening and schedule check every 10 interval
            if (! mLocMon.isNetworkProviderEnabled() && ! mLocMon.isGpsProviderEnabled()) {
                LSAppLog.d(TAG, ErrTxt.PREFIX_2+"...network provider not enabled....do nothing!!!");
            } else {
                LSAppLog.d(TAG, ErrTxt.PREFIX_2);
                startLocationMonitor(LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS, LOCATION_DETECTING_UPDATE_MAX_DIST_METERS);
            }
            break;
        case LOCATION_UPDATE:  // location update available
            onLocationUpdate();  // schedule timer
            break;
        case Msg.LOCATION_TIMER_EXPIRED:
            LSAppLog.dbg(this, DEBUG_INTERNAL, "Location Delayed timer expired, get location fix with wifi on");
            mNewCellStartTime = ((Long)msg.obj).longValue();  // start time recorded when schedule timer.
            mState = State.TIMEREXPIRED;
            mBeacon.setWifi(true);  // enable wifi for better accuracy
            stopLocationMonitor();
            startLocationMonitor(LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS, LOCATION_DETECTING_UPDATE_MAX_DIST_METERS); // this will cause on location change
            LSAppLog.i(TAG, "processMessage() : LOCATION_TIMER_EXPIRED...restart quickly to get location fix");
            break;
        case Msg.KEEP_MONITORING:
            mState = State.RUNNING;
            setPendingIntent(null);  // clear up pending timer so I can update end time upon next moving!
            LSAppLog.i(TAG, "processMessage() : KEEP_MONITORING...saved current location, Wifi Off, and keep listening delta radius!");
            mBeacon.setWifi(false);  // OFF wifi after location capture, in monitor state.
            break;
        case Msg.START_SELF_HEALING:
            LSAppLog.i(TAG, ErrTxt.PREFIX_PC+": MSG_START_SELF_HEALING...submit fix task!");
            mHealer.consolidateLocations(true);  // clubcheckin
            break;
        case Msg.HEARTBEAT_TIMER_EXPIRED:
            LSAppLog.i(TAG, ErrTxt.PREFIX_PC+": MSG_HEARTBEAT_TIMER...alive timer expired, send monitor msg to take a location fix");
            // send a start monitoring msg
            msg.what = Msg.START_MONITORING;
            msg.obj =  Long.valueOf(System.currentTimeMillis());
            //mHandler.sendMessage(msg);
            break;
        case Msg.STOP_MONITORING:
            LSAppLog.i(TAG, ErrTxt.PREFIX_PC+": MSG_STOP_MONITORING");
            setPendingIntent(null);  // nullify mTimerExpiredIntent so bouncing cell map can be cleared.
            stopLocationMonitor();   // stop after getting location fix.
            mHandler.removeMessages(Msg.START_MONITORING);
            break;
        case BEACONSCAN_RESULT:   // result from POI beacon scan
            String matchedpoi = (String)msg.obj;
            updateDiscoveredLocationPoiBeacon(matchedpoi);
            LSAppLog.i(TAG, "processMessage() : BEACONSCAN_RESULT after discovered CurPOI: "+ mCurPOI + " :matchedpoi::" + matchedpoi);
            mBeacon.removeCallerHandler(mHandler);  // done with beacon scan, no wifi scan update anymore.
            mBeacon.setWifi(false);
            break;
        default:
            LSAppLog.e(TAG, "processMessage() : unknown msg::" + msg.what);
            break;
        }
    }

    private void onLocationUpdate() {
        LSAppLog.dbg(this, DEBUG_INTERNAL, "Location Update pushed to me...save location or schedule a new timer ? : timer expired ? = " + (mState == State.TIMEREXPIRED));

        if (mState == State.TIMEREXPIRED) {
            LSAppLog.i(TAG, "onLocationUpdate() : timer expired...save current location!");
            Message nextStep = saveCurrentLocation();  // next step should always be stop monitoring
            mHandler.sendMessage(nextStep);
            return;  // return here!
        }

        if (Double.compare(mCurLat, 0.0) == 0) {  // first time
            LSAppLog.i(TAG, ErrTxt.PREFIX_3+" :: first time measurement...3 second timer only...");
            mBeacon.setWifi(true);  //  wifi on upon measurements
            scheduleAlarmTimer(3000);  // set 15 min timer...
        } else {
            if (null == getPendingIntent()) { // after saved and settle down...new update indicate a new move!
                updateCurrentLocationDuration(System.currentTimeMillis());
                LSAppLog.d(TAG, ErrTxt.PREFIX_3 + "moving to new location and update current location end time.");
            }
            LSAppLog.i(TAG, ErrTxt.PREFIX_3+" :: schedule 15 timer started and agile detection started....");
            scheduleAlarmTimer(TIMER_HEARTBEAT_INTERVAL);  // set 15 min timer...
            mDetection.startDetection();  // detection kicks in upon every location update ?
        }
    }


    /**
     * when entering this func, network tracking should be on
     * get the current location fix, figure out whether within any POIs, and
     * do next step appropriately.
     * @return
     * types of msg returned; <pre><code>
     *    1.start mon if fix not ready, simulate push if unknow cell,
     *    2.fire event if within radix and not same POI...or stop if same POI
     *  always add new location to database.
     *  </pre>
     */
    private Message saveCurrentLocation() {
        String addr = "";
        Message msg = mHandler.obtainMessage();
        msg.what = Msg.KEEP_MONITORING;    // init to be stop network tracking

        Location curloc = mLocMon.getCurrentLocation();
        String jsonstr = mTelMon.getValueJSONString();  // should never be null, given we are triggered by cell tower change

        double lat = (int)(curloc.getLatitude()*1E6)/1E6;   // 1E6 is good enough
        double lgt = (int)(curloc.getLongitude()*1E6)/1E6;
        long accuracy = curloc.hasAccuracy() ? (long)curloc.getAccuracy() : 0;  // get the accuracy

        // optimization, do not call Geocode, look up database first!
        Tuple t = mHealer.findClosestOverlappingLocation(lat, lgt, accuracy);
        if (t == null) {  // did not found best match
            addr = getLocationAddress(lat, lgt);
            LSAppLog.i(TAG, "saveCurrentLocation : getaddr from Geocode :" + addr);
        } else {
            addr = t.getAccuName();
            LSAppLog.i(TAG, "saveCurrentLocation : getaddr from database :" + addr);
        }

        // what we do with blank address ? this is google failure...recover by our wifi logic ?
        if (addr.length() == 0) { // if blank address, treat it as unknown location
            LSAppLog.i(TAG, "saveCurrentLocation :: blank addr :: " + lat + "::" + lgt);
            //msg.what = Msg.START_MONITORING;   // re start monitoring
            //msg.obj = null;
            //return msg;
        }

        // slow path...will change *CurPOI* inside this function, order matters!
        checkLocationMatch(lat, lgt, accuracy);

        // last, add this new loc in db before return...cur poi used from check location match
        addCurrentLocToDb(jsonstr, lat, lgt, accuracy, mNewCellStartTime, addr, mCurPOI, t);

        if (mCurPOI != null) {
            mBeacon.startBeaconScan(mCurPOI, mHandler); // scan and store beacon's around POI.
        }

        return msg;  // upon here, msg is keep monitoring.
    }


    // the place to update both celltowers and loctime table upon a new loc fix
    private void addCurrentLocToDb(String newjson, double newlat, double newlgt, long accuracy, long starttime, String addr, String poi, Tuple healedtuple) {
        ContentValues loctimeval = new ContentValues();

        Uri loctimeuri = LocationSensorProvider.LOCTIME_CONTENT_URI;
        Uri celltoweruri = LocationSensorProvider.CELL_CONTENT_URI;
        LSAppLog.i(TAG, "addCurrentLocToDb " + loctimeuri.toString() + ":: " + celltoweruri.toString());

        ContentResolver cr = getContentResolver();
        long nowtime = System.currentTimeMillis();

        loctimeval.clear();
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.LAT, newlat);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.LGT, newlgt);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.STARTTIME, mNewCellStartTime);  // insert using cell start time
        // use the system time b/c loc fix could be stopped awhile ago
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.ENDTIME, nowtime);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.NAME, addr);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.COUNT, (nowtime-mNewCellStartTime)/DateUtils.MINUTE_IN_MILLIS + 1);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.ACCURACY, accuracy);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.ACCUNAME, addr);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.POITAG, poi);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.CELLJSONVALUE, newjson);

        Uri locuri = cr.insert(loctimeuri, loctimeval);

        // piggyback instantaneous self healing but not checkin
        mHealer.consolidateLocations(false);

        // after insertion, update the current cursors
        mCurLat = newlat;
        mCurLgt = newlgt;
        mCurAccuracy = accuracy;
        mCurStartTime = mNewCellStartTime;   // take new start time for the last row

        if (healedtuple != null) {
            broadcastDiscoveredLocation(0, healedtuple.getLat(), healedtuple.getLgt(), 0, addr, "", null);
        } else {
            broadcastDiscoveredLocation(0, mCurLat, mCurLgt, mCurAccuracy, addr, "", null);
        }

        LSAppLog.d(TAG, "addCurrentLocToDb :: POI: " + poi + " : Accuracy : " + accuracy + " :Cur location value :: " + "::" + newlat + "::" + newlgt + "::" + nowtime);
        LSAppLog.dbg(this, DEBUG_INTERNAL, "Location Captured to database :: POI: " + mCurPOI + "::" + addr + "::(lat:lgt:accu:time)::" + mCurLat + "::" + mCurLgt + "::Accuracy : " + mCurAccuracy  + "::time:" + mCurStartTime);

    }

    // only called upon cell tower bouncing push update message.
    // update the end time of current location entry based on (lat, lgt, starttime)
    private void updateCurrentLocationDuration(long nowtime) {
        ContentValues loctimeval = new ContentValues();
        Uri loctimeuri = LocationSensorProvider.LOCTIME_CONTENT_URI;

        ContentResolver cr = getContentResolver();

        if (! Utils.compareDouble(mCurLat, 0.0) || ! Utils.compareDouble(mCurLgt, 0.0)) {
            LSAppLog.i(TAG, "updateCurrentLocAccuTime :: invalide current cell info...maybe due to restart POI reconstruct...bail out!!!");
            return;
        }

        //-------------------------------------------------------
        // update loctime table based on (lat, lgt, starttime)
        //-------------------------------------------------------
        String locwhere  = "(" +  LocationDatabase.LocTimeTable.Columns.LAT + " = " + mCurLat + " AND " +
                           LocationDatabase.LocTimeTable.Columns.LGT + " = " + mCurLgt + " AND " +
                           LocationDatabase.LocTimeTable.Columns.STARTTIME + " = " + mCurStartTime + " )";

        loctimeval.clear();
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.ENDTIME, nowtime);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.COUNT, (nowtime-mCurStartTime)/DateUtils.MINUTE_IN_MILLIS); // in minutes

        try {
            int row = cr.update(loctimeuri, loctimeval, locwhere, null);
            LSAppLog.i(TAG, "updateCurrentLocAccuTime :: update current location :: where " + locwhere + "row=" + row +
                       " starttime : " + mCurStartTime );
        } catch (Exception e) {
            LSAppLog.e(TAG, "updateCurrentLocAccuTime :: Exception: " + e.toString());
        }

        return;
    }

    private void checkLocationMatch(double lat, double lgt, long accuracy) {
        boolean samepoi = false;
        String poitag = null;
        LocationDatabase.PoiTable.Tuple poituple = null;

        poituple = mDetection.checkPoiDistance(lat, lgt, accuracy);  // consistently use detection's logic

        if (poituple != null) {
            poitag = poituple.getPoiName();
            if (mCurPOI != null && mCurPOI.equals(poitag)) {
                samepoi = true;
                LSAppLog.i(TAG, "slow path detection..checkLocationMatch :: same POI:" + mCurPOI);
            } else {
                mCurPOI = poitag;
            }
            LSAppLog.d(TAG, "slow path detection....checkLocationMatch :: matching POI:" + mCurPOI + "lat:lgt=" + lat+"::"+lgt);
            onLocationMatch(mCurPOI, poituple);
            float dist = Utils.distanceTo(lat, lgt, poituple.getLat(), poituple.getLgt());
            LSAppLog.dbg(this, DEBUG_INTERNAL, "Slow Path location detection. entering POI", poitag, " Dist="+dist, " notify VSM");
        } else {
            LSAppLog.d(TAG, "slow path detection....checkLocationMatch :: new location does not match any POI...leaving cur poi ?" + mCurPOI);
            // ok, not within any target, one last check if we are going to stop, not stop if cell is unknown
            // up to this point, location not matching any POI, means we are leaving
            if (mCurPOI != null) {
                LSAppLog.d(TAG, "slow path detection....checkLocationMatch :: Leaving current POI: " + mCurPOI);
                onLocationLeave(mCurPOI);  // notify vsm
                LSAppLog.dbg(this, DEBUG_INTERNAL, "Slow Path location detection. leaving POI:", mCurPOI, ": notify VSM");
            }
            mCurPOI = null;   // reset poi cache if we are out!
        }
    }

    /**
     * Emit and event upon Poi match
     * @param poi
     */
    private void onLocationMatch(String poi, LocationDatabase.PoiTable.Tuple poituple) {
        LSAppLog.dbg(this, DEBUG_INTERNAL, "Location captured..:: notify VSM...onLocationMatch POI ::" + poi);
        sendBroadcast(new Intent(VSM_LOCATION_CHANGE).putExtra("status", "arrive"));
        mLSApp.getVsmProxy().sendVSMLocationUpdate(poi);
        //mDetection.setPoiByLSMan(poituple);
    }

    /**
     * Emit and event upon leave POI
     */
    private void onLocationLeave(String poi) {
        LSAppLog.dbg(this, DEBUG_INTERNAL, "Location captured..:: notify VSM..onLocationLeave :: POI ::" + poi);
        sendBroadcast(new Intent(VSM_LOCATION_CHANGE).putExtra("status", "leave"));
        mLSApp.getVsmProxy().sendVSMLocationUpdate("Transient");
    }

    /*
     * put a initial entry in db to indication service getting restarted due to low resource
     */
    private void logServiceRestarted() {
        ContentValues loctimeval = new ContentValues();

        Uri loctimeuri = LocationSensorProvider.LOCTIME_CONTENT_URI;
        ContentResolver cr = getContentResolver();

        loctimeval.clear();
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.LAT, 0);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.LGT, 0);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.STARTTIME, System.currentTimeMillis());  // insert using nowtime
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.ENDTIME, System.currentTimeMillis());
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.COUNT, 1);
        loctimeval.put(LocationDatabase.LocTimeTable.Columns.CELLJSONVALUE, "Service Restarted abnormally...last location entry invalid");

        cr.insert(loctimeuri, loctimeval);
        LSAppLog.i(TAG, "logServiceRestarted ::");
        LSAppLog.dbg(this, DEBUG_INTERNAL, "service restarted...", "capturing location immediately" );
    }

    /**
     * the timer expired event handler for location 15min timer and self healing timer
     * @author e51141
     *
     */
    @SuppressWarnings("unused")
    final static private class TimerEventReceiver extends BroadcastReceiver {
        private final Context mContext;
        private final Handler mHandler;
        private boolean mStarted = false;

        TimerEventReceiver(final Context ctx, final Handler handler) {
            this.mContext = ctx;
            this.mHandler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LSAppLog.i(TAG, "TimerEventReceiver : onReceiver : Got timer expired Event :" + action);
            Message msg = mHandler.obtainMessage();

            if (ALARM_TIMER_LOCATION_EXPIRED.equals(action)) {
                // 15 min timer expired. get location fix now!
                msg.what = Msg.LOCATION_TIMER_EXPIRED;
                msg.obj =  Long.valueOf(intent.getLongExtra(ALARM_TIMER_SET_TIME, System.currentTimeMillis()));
                LSAppLog.i(TAG, "TimerEventReceiver : onReceiver : LOCATION_TIMER_EXPIRED Setting with current TIME :" + msg.obj );
            } else if (ALARM_TIMER_SELF_HEALING_EXPIRED.equals(action)) {
                msg.what = Msg.START_SELF_HEALING;
                LSAppLog.i(TAG, "onReceive : ALARM_TIMER_SELF_HEALING_EXPIRED : send MSG_START_SELF_HEALING");
            } else if (ALARM_TIMER_ALIVE_EXPIRED.equals(action)) { // 2 housr alive timer expired
                msg.what = Msg.HEARTBEAT_TIMER_EXPIRED;
                LSAppLog.i(TAG, "onReceive : ALARM_TIMER_ALIVE_EXPIRED : send MSG_HEARTBEAT_TIMER");
            }

            mHandler.sendMessage(msg);
        }

        synchronized final void start() {
            if (!mStarted) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ALARM_TIMER_LOCATION_EXPIRED);
                filter.addAction(ALARM_TIMER_SELF_HEALING_EXPIRED);
                mContext.registerReceiver(this, filter);
                mStarted = true;
                LSAppLog.i(TAG, "TimerEventReceiver : started and registered");
            }
        }

        synchronized final void stop() {
            if (mStarted) {
                mContext.unregisterReceiver(this);
                mStarted = false;
                LSAppLog.i(TAG, "TimerEventReceiver : stoped and unregistered");
            }
        }
    }

    // if first time start, get loation quick
    private boolean startLocationMonitor(long minTime, float minDistance) {
        LSAppLog.i(TAG, "LSMan Start listening location update...");
        return mLocMon.startLocationUpdate(minTime, minDistance, null);
    }

    private void stopLocationMonitor() {
        LSAppLog.i(TAG, "LSMan Stop listening location update...");
        mLocMon.stopLocationUpdate();
    }

    private void handleVSMInitComplete() {
        String poi = null;
        if (mDetection != null) {
            if (mDetection.mCurPoiTuple != null)
                poi = mDetection.mCurPoiTuple.getPoiName();
            else
                poi = "Transient";
            mLSApp.getVsmProxy().sendVSMLocationUpdate(poi);
        }
    }

    public LocationDetection getDetection() {
        return mDetection;
    }
}
