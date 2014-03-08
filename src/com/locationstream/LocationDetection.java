package com.locationstream;

import static com.locationstream.Constants.BEACONSCAN_RESULT;
import static com.locationstream.Constants.DET_BOUNCING_CELL_SIZE;
import static com.locationstream.Constants.FUZZY_MATCH_MIN;
import static com.locationstream.Constants.INVALID_CELL;
import static com.locationstream.Constants.LOCATION_DETECTING_DIST_RADIUS;
import static com.locationstream.Constants.LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS;
import static com.locationstream.Constants.LOCATION_DETECTING_UPDATE_MAX_DIST_METERS;
import static com.locationstream.Constants.LOCATION_DETECTION_POI;
import static com.locationstream.Constants.LOCATION_UPDATE;
import static com.locationstream.Constants.LOCATION_UPDATE_AVAILABLE;
import static com.locationstream.Constants.LS_JSON_WIFIBSSID;
import static com.locationstream.Constants.LS_JSON_WIFISSID;
import static com.locationstream.Constants.MONITOR_RADIUS;
import static com.locationstream.Constants.TARGET_RADIUS;
import static com.locationstream.Constants.WIFI_CONNECTED;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.dbhelper.LocationDatabase;
import com.locationstream.dbhelper.LocationDatabase.PoiTable.Tuple;


/**
*<code><pre>
* CLASS:
*  implements location detection(arrival and leaving) logic based on location updates from platform.
*  this is a singleton instance this class.
*
* RESPONSIBILITIES:
*  This class is responsible for detecting arriving or leaving of any meaningful location.
* it requests location updates from platform and calculate distances to any POIs to detect location.
* Detection is purely distance driven...and distance is calculated from location fix got from platform.
*
* COLABORATORS:
*  VSM, notify VSM upon meaningful location detection(arrival and leaving).
*
* USAGE:
* 	See each method.
*
*</pre></code>
*/


public class LocationDetection {
    private static final String TAG = "LSAPP_LSDet";
    public static final String TRANSIENT = "Transient";

    // for now, We need location update inside POI. In case we can stop request location fix request inside a poi somehow, set the flag.
    private static final boolean stopInsidePoi = false;

    public interface Msg {   // diff with ones in locman
        int START_DETECTION 		= 1000;     // start monitoring
        int STOP_DETECTION	 		= 1001;     // start monitoring
        int PROXIMITY_ALERT 		= 1003;
        int ENTERING_POI		 	= 1004; 	// fire event, done
        int LEAVING_POI		 		= 1005; 	// fire event, done
        int POI_REFRESH				= 1006;
        int LOCATION_PENDINGINTENT	= 1007;
        int START_SCAN				= 1008;
        int NULL 					= 99999;
    };

    /**
     * RUNNING set upon request location fix, reset inside stop detection.
     * FIX_PENDING: set when loc req pending, reset after on detection location update.
     * BEACON_WAITING: set when start leaving active scan. reset inside check beacon scan result.
     */
    private enum DetectionState {
        STOPPED(0), RUNNING(1<<0), LOCATIONFIX_PENDING(1<<1), BEACON_WAITING(1<<2);
        private final int flag;
        DetectionState(int flag) {
            this.flag = flag;
        }
        public int getFlag() {
            return flag;
        }
    };
    private int mStateFlag = 0;

    /**
     * Strategy Pattern: detection algorithm using distance to poi or using poi wifi match.
     *   1. find the closest poi to this fix by distance calculation.
     *   2. if the closest poi has any wifi info captured previously, use wifi match rather than distance match, as fix might not accurate.
     *   3. otherwise, degrade to pure distance driven algorithm.
     *   4. wifissid=[{"wifibssid":"5c:0e:8b:30:f2:c1","wifissid":"MMI-Internet"},{"wifibssid":"5c:0e:8b:30:f2:c0","wifissid":"DSA-Wireless"},...]
     */
    private enum DetectionAlgorithmType {
        DETALG_GOOGLEFIX(0), DETALG_WIFIMATCHING(1<<0);
        private int algorithm;
        DetectionAlgorithmType(int algo) {
            this.algorithm = algo;
        }
        public void setDetectionAlgorithmType(DetectionAlgorithmType algo) {
            algorithm |= algo.getDetectionAlgorithmType();
        }
        public int getDetectionAlgorithmType() {
            return this.algorithm;
        }
        public static DetectionAlgorithmType selectDetectionAlgorithmType(Tuple poituple) {
            // wifissid is jsonarray.toString(), empty set is "[]" !!!
            // use wifi match algorithm only when more than 2 wifis
            if (poituple != null && poituple.getWifiSsid() != null
                    // && ! ("[]".equals(poituple.getWifiSsid().trim()))  // deprecated by the two wifi requirements.
                    && (poituple.getWifiSsid().trim().length() > 150)   // 150 is the no. of chars for at least two wifi ssid, check above wifissid string format.
               ) {
                return DETALG_WIFIMATCHING;
            } else {
                return DETALG_GOOGLEFIX;
            }
        }
    }

    private  WorkHandler mWorkHandler;
    private  MessageHandler mHandler;

    private Context mContext;
    private LocationSensorManager mLSMan;
    //private NoCellLocationManager mLocMan;

    @SuppressWarnings("unused")
    private Handler mLSManHdl;   // save lsman's handle so that we can send message back to parent later.

    private PoiAdapter mPoiAdapter;  // expose to pkg

    private LocationManager mLocationManager;
    private LocationMonitor mLocMon;
    private TelephonyMonitor mTelMon;
    BeaconSensors mBeacon;   // those are injected in components, not this module's private, should be pkg private!

    private LocationTimerTask mTimerTask;
    private PendingIntent mProximityAlertIntent = null;
    private ProximityAlertReceiver mProxyRecver;

    Tuple mCurPoiTuple;  // changed only inside enter leaving poi and checkDiscoveredLocationPoi
    Tuple mNextPoiTuple; // set to next POI where we are moving to.
    private Location mCurLoc;
    private int mMinDist;   // the min of dist to all poi's.
    private Map<String, String> mCurPoiWifiMap;   // cur poi's scanned wifi populated from db. Updated inside entering/leavingPOI
    private Set<String> mCurPoiBagSsid;           // a set of ssids that are ssid bag. i.e., dup ssid name. Updated upon poi change.

    private int mFixCount = 0;   // in order to discard the first stale location fix per instant gratification. set to 0 when stop Location fix request. incr upon every locaiton fix.
    private static final int LEAVING_WIFI_DEBOUNCE_COUNT = 1;  // debounce count, reduce the count to 1 scan is good enough.
    private int mWifiLeavingDebounceCount = 0;  // debounce for wifi leaving....assert leaving only when two consecutive leave happened. Reset when leaving.
    private int mDebounceCount = 0;  // entering, great than 2, leaving, less than -2
    private List<String> mDetCells = new ArrayList<String>(DET_BOUNCING_CELL_SIZE);  // let's limited bouncing cells now
    boolean notifiedVSMOnRestart = false;

    @SuppressWarnings("unused")
    private LocationDetection() {} // hide default constructor

    /**
     * constructor, take the location sensor manager context and the handler so we can notify location sensor manager.
     * @param ctx  location sensor manager context.
     * @param hdl  location sensor message handler.
     */
    public LocationDetection(final Context ctx, final Handler hdl) {
        mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());

        mContext = ctx;
        mLSMan = (LocationSensorManager)ctx;
        //mLocMan = (NoCellLocationManager)ctx;
        mLSManHdl = hdl;

        mPoiAdapter = new PoiAdapter(mContext, mHandler);
        mLocMon = new LocationMonitor(mLSMan, mHandler);  // need detection's own loc monitor here...use my mhandler here !

        // leverage proximity alert
        mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        mProxyRecver = new ProximityAlertReceiver(mContext, mHandler);
        mProxyRecver.start();
        //addProximityAlerts();
        //LocationUpdatePendingIntentReceiver locRecver = new LocationUpdatePendingIntentReceiver(mContext, mHandler);
        //locRecver.start();

        mCurPoiTuple = null;
        mNextPoiTuple = null;
        mCurLoc = null;
        mCurPoiWifiMap = new HashMap<String, String>();  // init
        mCurPoiBagSsid = new HashSet<String>();

        mTimerTask = new LocationTimerTask(this);   // create the timer task.

        mMinDist = Integer.MAX_VALUE;
        notifiedVSMOnRestart = false;
        mStateFlag = DetectionState.STOPPED.getFlag();
    }

    /**
     * initialize reference to Telephony monitor...
     * outside from constructor due to two way dependency between the two components.
     * @param telmon
     * @param beacon
     */
    public void setTelMonBeacon(final TelephonyMonitor telmon, final BeaconSensors beacon) {
        mTelMon = telmon;
        mBeacon = beacon;
        mBeacon.startPassiveBeaconListen(mHandler);  // always listen to wifi scan.
    }

    public Handler getDetectionHandler() {
        return mHandler;
    }

    /**
     * called from location manager on destroy
     */
    public void cleanup() {
        mPoiAdapter.unregisterPoiObserver();
        mProxyRecver.stop();
        stopDetection();
        //mWorkHandler.finalize();
        mTimerTask.clean();
    }

    /**
     * whether location listening monitoring is on-going, only refered from UI status.
     */
    public boolean isDetectionMonitoringON() {
        LSAppLog.d(TAG, "isDetectionMonitoringON: " + mStateFlag + " and CurPoi=" + mCurPoiTuple);
        return isStateFlagSet(DetectionState.RUNNING.getFlag());
    }

    /**
     * set the state flag, if reset fix pending flag, stop location listener cause we got the fix.
     * @param flag, the flag bit defined previous
     * @param set, true if we are setting this flag bit, false we are clearing this flag bit.
     */
    private void setStateFlag(int flag, boolean set) {
        if (set) {
            mStateFlag |= flag;
        } else {
            mStateFlag &= (~flag);
            if (flag == DetectionState.LOCATIONFIX_PENDING.getFlag()) {
                if (stopInsidePoi)
                    mLocMon.stopLocationUpdate();   // enable this if not constantly request location fix every 5 min inside POI.
            }
        }
        LSAppLog.pd(TAG, "setStateFlag: flag=" + flag + " Set(true)Reset(false)="+set+ " mStateFlag=" + mStateFlag);
    }

    /**
     * check whether certain flag is set
     */
    private boolean isStateFlagSet(int flag) {
        return (mStateFlag & flag) == flag;
    }

    /**
     * messge looper
     */
    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }
    private void processMessage(android.os.Message msg) {
        switch (msg.what) {
        case Msg.NULL:
            LSAppLog.d(TAG, "processMessage() : MSG_NULL");
            break;
        case Msg.START_DETECTION:   // from tel mon when cell tower changed
            LSAppLog.d(TAG, "processMessage() : START_DETECTION");
            startDetection();
            break;
        case LOCATION_UPDATE:   // use the global definition
            LSAppLog.d(TAG, "processMessage() : LOCATION_UPDATE available from Location monitor");
            onDetectionLocationUpdate();
            break;
        case Msg.ENTERING_POI:
        case Msg.LEAVING_POI:
            updateDetectionState((Tuple)msg.obj);
            break;
        case Msg.PROXIMITY_ALERT:
            LSAppLog.d(TAG, "processMessage() : PROXIMITY_ALERT pending intent...runDetection with listening update...");
            runDetection("Run detection upon ProximityAlert: {ProximityAlert:true}");
            break;
        case Msg.POI_REFRESH:
            LSAppLog.d(TAG, "processMessage() : POI_REFRESH from poi adapter....");
            onPoiRefresh();
            break;
        case Msg.START_SCAN:
            LSAppLog.d(TAG, "processMessage() : START_SCAN from timer task....");
            startScan();
            break;
        case BEACONSCAN_RESULT:
            LSAppLog.d(TAG, "processMessage() : BEACONSCAN_RESULT Async done....check beacon scan results.");
            checkBeaconScanResult();
            break;
        case WIFI_CONNECTED:
            LSAppLog.d(TAG, "processMessage() : WIFI_CONNECTED...lookup POI table and assert POI if possible");
            handleWifiConnStateChanged((String)msg.obj);
            break;
        case Msg.LOCATION_PENDINGINTENT:
            LSAppLog.d(TAG, "processMessage(): Location Fix pending intent received");
            handleLocationFixFromPendingIntent((Location)msg.obj);
            break;
        default:
            LSAppLog.d(TAG, "processMessage() : wrong message what=" + msg.what);
            break;
        }
    }

    /**
     * check if any POI tagged by the user (in the persistent store) has a matching cell ID
     * @param celljson can be a single string, or string set with && concatenate
     * @return tuple of the matching POI.
     * caveat: if a cell belongs to two POIs, the first POI tuple in the persistence store poi will be returned.
     * the reason is we can not tell which poi is the closest to the cell due to the lack of distance concept here.
     */
    private Tuple getMatchingPoiOfCell(String celljson) {
        Tuple poituple = null;
        List<Tuple> poilist = mPoiAdapter.getPoiList();

        LSAppLog.d(TAG, "cellChangeMatchAnyPoi : bouncing cells:: " + celljson);
        if (null == celljson || poilist.size() == 0) {
            LSAppLog.d(TAG, "cellChangeMatchAnyPoi null bouncing cells...or fake cell push..:: or empty poi: " + poilist.size());
            return null;
        }
        synchronized (poilist) { // returned poi list is already synch wrapped...no sync needed here.
            for (Tuple entry : poilist ) {
                if (Utils.jsonStringSubset(entry.getCellJson(), celljson) ) {
                    poituple = entry;
                    LSAppLog.pd(TAG, "cellChangeMatchAnyPoi matches tuple :: " + poituple.getPoiName() + " ::value::" + poituple.toString());
                    break;  // first match breaks.
                }
            }
        }
        if (poituple == null) {
            LSAppLog.d(TAG, "cellChangeMatchAnyPoi :: not a poi cell");
        }
        return poituple;
    }

    /**
     * find the poi by connected wifi bssid, return the found poi tuple, or null otherwise.
     */
    private Tuple getMatchingPoiByConnWifi(String bssid) {
        Tuple poituple = null;
        List<Tuple> poilist = mPoiAdapter.getPoiList();
        String dbconnwifi = null;

        LSAppLog.d(TAG, "getMatchingPoiByConnWifi :" + bssid);
        if (null == bssid || poilist.size() == 0) {
            LSAppLog.d(TAG, "getMatchingPoiByConnWifi invalid bssid or empty poi: " + poilist.size());
            return null;
        }
        synchronized (poilist) { // returned poi list is already synch wrapped...no sync needed here.
            for (Tuple entry : poilist ) {
                //Set<String> dbWifiSet = Utils.convertStringToSet(entry.getWifiConnMac());  // connected wifi only
                //if (dbWifiSet.contains(bssid)) {
                // String indexOf match is easy and quick for bssid b/c bssid is fixed in length!
                dbconnwifi = entry.getWifiConnMac();  // connected wifi only
                if (dbconnwifi != null && dbconnwifi.indexOf(bssid) >= 0) {
                    poituple = entry;
                    LSAppLog.pd(TAG, "getMatchingPoiByConnWifi matches tuple :: " + poituple.getPoiName() + " ::value::" + poituple.toString());
                    break;  // first match breaks.
                }
            }
        }
        return poituple;
    }

    /** evaluate if detection needs to be started because the new cell tower we just changed into may
     * be near a POI. As a reminder, this detection is looking for a WiFi access point match against
     * the given POI returned in getMatchingPoiOfCell.
     *
     * start location detection only when we are currently in a POI or about into a POI based on cell ids.
     * because this is cell id driven, we know when we are in neary by cells for a poi.
     */
    public void startDetection() {
        String curpoi = null;
        String nextPoiName = null;

        // & delimited.
        String bouncingCellIdsNearby = Utils.convertSetToString(mTelMon.getBouncingCells());
        if (bouncingCellIdsNearby == null) {  // caused by fake cell push
            return;
        }

        LSAppLog.d(TAG, "startDetection: celltower changed and startDetection...");

        if (mPoiAdapter.getPoiList().size() == 0) {
            LSAppLog.pd(TAG, "startDetection: celltower changed : stop location detection...no POIs");
            return;
        }

        String curcell = mTelMon.getValueJSONString();
        Tuple poituple = getMatchingPoiOfCell(bouncingCellIdsNearby);
        // if currently not in a poi, and not nearby a poi, and we do not have any invalid cell, then we can stop.
        if ( mCurPoiTuple == null && poituple == null && !isAnyCellInvalid(bouncingCellIdsNearby) ) {
            LSAppLog.pd(TAG, "startDetection: celltower changed : do nothing because not in a poi and cells are not POI cells ::" + bouncingCellIdsNearby);
            // stopDetection();  !!! stop only when dist off...b/c there could be location fix requestion pending for other reasons.
            notifyVSMLeavingPOIUponRestarted();  // no poi cell match, notify vsm if needed before return.
            return;
        }

        // if currently in a poi, and LAC not changed, stop detection
        if (mCurPoiTuple != null) {
            curpoi = mCurPoiTuple.getPoiName();
            LSAppLog.d(TAG, "startDetection: cell changed: start location monitoring because currently in POI:" + curpoi);
        }

        // if we found a meaningful location, then start passively listening for WiFi scan
        if (poituple != null) { // wifi on when invalid cell or poi cell
            // nextPoiName is a potential target in this cell. If we have multiple targets per cell, this will
            // not always be the actual or final target.
            nextPoiName = poituple.getPoiName();
            mNextPoiTuple = poituple;   // the next poi we are moving into.
            //mBeacon.startPassiveBeaconListen(mHandler);
            LSAppLog.d(TAG, "startDetection: cell changed belong to POI:"+ nextPoiName + " start location update and passive listen on beacon scan");
        }

        if (curcell != null && !(curcell.indexOf(INVALID_CELL) < 0)) {  // Result of method indexOf should be checked for >=0 or <0
            //mBeacon.startPassiveBeaconListen(mHandler);
            LSAppLog.d(TAG, "startDetection: start location monitoring because we have invalid cell -1");
        }

        // Start location monitoring by poi cells or currently in a POI.
        LSAppLog.d(TAG, "startDetection: curPOI : " + curpoi + " :: next POI : " + nextPoiName);
        runDetection("Run Detection upon cell tower change event : {CurPOI:"+curpoi+":NextPOI:"+nextPoiName+"}");
    }

    /** this determines whether an nearby cell id is invalid, returns true if any nearby cell is invalid
     *
     * @param bouncingCellIdsNearby
     * @return
     */
    private boolean isAnyCellInvalid(String bouncingCellIdsNearby) {
        // indexOf() return -1 if there is no invalid cell, great than 0 otherwise.
        if (bouncingCellIdsNearby != null && (bouncingCellIdsNearby.indexOf(INVALID_CELL) < 0)) {
            return false;   // all  the cells are good
        }
        return true;  // there is at least one invalid cell.
    }

    /**
     * Run detection is just start to requesting location update based on following conditions.
     * 1. if wifi is connected within an POI, do not request location update to save power.
     * 2. if wifi disconnected, run a full cycle of req, fix, dist, stop.
     * @param reason
     */
    private void runDetection(String reason) {
        // currently inside POI, wifi connected or poi has wifi, do not request location fix to save power.
        if (mCurPoiTuple != null && ( mBeacon.getWifiConnState() == 1 )) { //|| DetectionAlgorithmType.selectDetectionAlgorithmType(mCurPoiTuple) == DetectionAlgorithmType.DETALG_WIFIMATCHING)) {
            //if (mCurPoiTuple != null && ( mBeacon.getWifiConnState() == 1 || DetectionAlgorithmType.selectDetectionAlgorithmType(mCurPoiTuple) == DetectionAlgorithmType.DETALG_WIFIMATCHING)) {
            mFixCount = 0;
            mLocMon.stopLocationUpdate();  // light weight stop detection by stop location request.
            // mTimerTask.startPeriodicalWifiPolling(LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS);
            setStateFlag(DetectionState.LOCATIONFIX_PENDING.getFlag(), false);  // clear fix pending, running will be cleared inside stop detection.
            LSAppLog.pd(TAG, "runDetection: stop loc req upon wifi connected and inside poi and clear fix pending flag." + reason );
            return;
        }

        if (!isStateFlagSet(DetectionState.RUNNING.getFlag())) {
            setStateFlag(DetectionState.RUNNING.getFlag(), true);
            LSAppLog.d(TAG, reason + " : START Fast Detection!!!");
        }

        if (isStateFlagSet(DetectionState.LOCATIONFIX_PENDING.getFlag())) {
            LSAppLog.pd(TAG, "runDetection: return upon pending for location fix..." + reason );
            if (stopInsidePoi) {
                return;  // enable this if not using constantly loc fix request. // already a location fix request pending, no need to do another.
            }
        } else {
            //setBeaconState(true);
            setStateFlag(DetectionState.LOCATIONFIX_PENDING.getFlag(), true);
            LSAppLog.pd(TAG, "runDetection: setting pending for location fix flag=" + mStateFlag);
        }

        if (stopInsidePoi) { // false at this moment.
            mLocMon.startLocationUpdate(60000, 100, null);  // rest for one min before reporting.
        } else {
            mLocMon.startLocationUpdate(LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS, LOCATION_DETECTING_UPDATE_MAX_DIST_METERS, null);
            //mLocMon.startLocationUpdate(LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS, LOCATION_DETECTING_UPDATE_MAX_DIST_METERS, PendingIntent.getBroadcast(mContext, 0, new Intent(LOCATION_UPDATE_AVAILABLE), PendingIntent.FLAG_UPDATE_CURRENT));
        }
        // mTimerTask.stopPeriodicalWifiPolling();  // stop any running timer task, if exist.
        LSAppLog.pd(TAG, "runDetection: State:" + mStateFlag + " Requesting location update..." + reason + "every:" + LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS);
    }

    /**
     * stop location detection by remove location update request to save power.
     * called from two places: 1. dist off after location fix, 2. leaving poi asserted.
     */
    private void stopDetection() {
        notifyVSMLeavingPOIUponRestarted();  // notify leaving POI upon restart
        mStateFlag = 0;  // clear all bit, where running flag be reset to 0
        mFixCount = 0;
        mLocMon.stopLocationUpdate();
        setBeaconState(false);
        // mBeacon.stopPassiveBeaconListen(mHandler);   // always listen to wifi scan and process it.
        LSAppLog.pd(TAG, "STOP Fast Detection: Current POI null and Cell tower has no POI");
    }

    private void onDetectionLocationUpdate() {
        if (mFixCount == 0) {
            mFixCount++;
            //LSAppLog.pd(TAG, "onDetectionLocationUpdate: first location fix available, but let's discard this stale one and wait for a following good one.");
            //return;  // No, I can not drop any location fix, too aggressive!
        }
        detectPOIUponLocationFix();
        setStateFlag(DetectionState.LOCATIONFIX_PENDING.getFlag(), false);  // clear fix pending flag, will removal the 5 min periodical location request.
    }

    /**
     * you are not moving if you do not get continuous cell push update!
     * stop location listener to save power, use with sensor hub.
     */
    @Deprecated
    private boolean stopLocationRequestInPoi() {
        String curcell = mTelMon.getValueJSONString();
        if (mCurPoiTuple != null) {
            // first, add cell into cache
            if (curcell != null && !mDetCells.contains(curcell)) {
                shiftAddBouncingCells(curcell);
            }
            // the only case we can stop is size not overflow.
            if (mDetCells.size() < DET_BOUNCING_CELL_SIZE) {
                LSAppLog.d(TAG, "stopLocationRequestInPoi ::" + mDetCells.toString());
                stopDetection();
                return true;
            }
            // if detectionalgorithm is wifi, stop location request
            if (DetectionAlgorithmType.selectDetectionAlgorithmType(mCurPoiTuple) == DetectionAlgorithmType.DETALG_WIFIMATCHING) {
                LSAppLog.d(TAG, "stopLocationRequestInPoi :: stop location request upon using wifi matching: " + mCurPoiTuple.getPoiName());
                mLocMon.stopLocationUpdate();
                return true;
            }
        }
        return false;
    }



    /**
     * Callback to process location fix. A strategy pattern to adopted decide which algorithm to used for detection.
     * Strategy Pattern: detection algorithm using distance to poi or using poi wifi match.
     *   1. find the closest poi to this fix by distance calculation.
     *   2. if the closest poi has any wifi info captured previously, use wifi match rather than distance match, as fix might not accurate.
     *   3. otherwise, degrade to pure distance driven algorithm.
     */
    private void detectPOIUponLocationFix() {
        // first, get location update
        mCurLoc = mLocMon.getCurrentLocation();
        if (mCurLoc == null) {
            LSAppLog.pd(TAG, "detectPOIUponLocationFix :: current location is null");
            return;
        }

        // within any POI's radius ?
        double lat = (int)(mCurLoc.getLatitude()*1E6)/1E6;   // 1E6 is good enough
        double lgt = (int)(mCurLoc.getLongitude()*1E6)/1E6;
        long accuracy = mCurLoc.hasAccuracy() ? (long)mCurLoc.getAccuracy() : 0;  // get the accuracy
        LSAppLog.d(TAG, "detectPOIUponLocationFix :: lat=" + lat + " ,lgt=" + lgt + " ,accuracy=" +accuracy);
        
        // already in monitoring...assert if distance radius valid.
        Tuple nextPoiTuple = checkPoiDistance(lat, lgt, accuracy);

        if (mCurPoiTuple != null) {
            detectLeavingPoi(lat, lgt, accuracy, nextPoiTuple);
        } else {
            detectEnteringPoi(lat, lgt, accuracy, nextPoiTuple);
        }

        /** legacy code, deprecated by the above separate entering and leaving function.
        // keeping POI, nothing changed
        if (mCurPoiTuple != null && nextPoiTuple != null && mCurPoiTuple.equals(nextPoiTuple)) {
            mLSMan.getMetricsLogger().logStayInPoi();
            keepingPOI(nextPoiTuple);
            float dist = Utils.distanceTo(lat, lgt, nextPoiTuple.getLat(), nextPoiTuple.getLgt());
            LSAppLog.dbg(mContext, DEBUG_INTERNAL, " detectPOIUponLocationFix: remain in POI:", mCurPoiTuple.getPoiName(), " [ Distance="+dist + " ,lat=" + lat + " ,lgt=" + lgt + " ,accuracy=" +accuracy);
        }
        // switching POI : ( current in a POI and move to another POI)
        else if (mCurPoiTuple != null && nextPoiTuple != null && !mCurPoiTuple.equals(nextPoiTuple)) {
            // given radius is only 100 meters, we should never have overlapped POIs...
            LSAppLog.pd(TAG, "detectPOIUponLocationFix : switch POI: " + mCurPoiTuple.getPoiName() + " ==>" + nextPoiTuple.getPoiName());
            mLSMan.getMetricsLogger().logSwitchPoi();
            switchingPOI(nextPoiTuple);  // decompose to leave and enter
        }
        // leaving a POI : ( current in a POI and moved out of _any_ POI)
        else if (mCurPoiTuple != null && nextPoiTuple == null) {
            int distance = (int) Utils.distanceTo(lat, lgt, mCurPoiTuple.getLat(), mCurPoiTuple.getLgt());
            mLSMan.getMetricsLogger().logLeavingDistance(distance);

            if (false) {  // without doing a pro-active scan, enable this when wifi driver support scan result timestamp.
                mBeacon.getWifiScanResultJson();  // populate wifi scan result
                checkLeavingThrashing();
            } else {
                if (mWifiLeavingDebounceCount > 0) {  // wifi scan already detected leaving
                    LSAppLog.dbg(mContext, DEBUG_INTERNAL, "detectPOIUponLocationFix : wifi debounce already detect leaving, Really Leaving POI >>>:", mCurPoiTuple.getPoiName(), "{lat:lgt:accuracy:"+lat+":"+lgt+":"+accuracy+"}");
                    leavingPOI();   // leave poi, will do stop detection at the end.
                } else {
                    //mWifiLeavingDebounceCount++;  // count google fix as one wifi leave
                    scanBeaconUponLeave();  // do a scan...God, it is async!
                    setStateFlag(DetectionState.BEACON_WAITING.getFlag(), true);
                    LSAppLog.dbg(mContext, DEBUG_INTERNAL, "detectPOIUponLocationFix : Delay Leaving, Check Leaving Beacon: Leaving POI >>>:", mCurPoiTuple.getPoiName(), "{lat:lgt:accuracy:"+lat+":"+lgt+":"+accuracy+"}");
                }
            }

        }
        // entering a POI : (current not in any poi and moved in)
        else if (mCurPoiTuple == null && nextPoiTuple != null) {
            float dist = Utils.distanceTo(lat, lgt, nextPoiTuple.getLat(), nextPoiTuple.getLgt());
            LSAppLog.dbg(mContext, DEBUG_INTERNAL, " detectPOIUponLocationFix: Moving into POI : " + nextPoiTuple.getPoiName(), "[ Distance="+dist + " ,lat=" + lat + " ,lgt=" + lgt + " ,accuracy=" +accuracy);
            mLSMan.getMetricsLogger().logEnterPoi();
            enteringPOI(nextPoiTuple);
        }
        //not in poi and no poi associated with current location
        else if (mCurPoiTuple == null && nextPoiTuple == null) {
            Tuple poicelltuple = getMatchingPoiOfCell(Utils.convertSetToString(mTelMon.getBouncingCells()));
            //current cell is associated with poi
            if (poicelltuple != null) {
                mLSMan.getMetricsLogger().logApproachingPoi();
            }
        }
        ***/

        // stop location tracking when no poi cells and currently not in any POI
        // never stop listening in no cell solution...always getting location updates.
        // if (mLSMan instanceof com.motorola.locationsensor.LocationSensorManager) {
        if (mCurPoiTuple == null && nextPoiTuple == null && null == getMatchingPoiOfCell(Utils.convertSetToString(mTelMon.getBouncingCells()))) {
            mNextPoiTuple = null;
            LSAppLog.pd(TAG, "detectPOIUponLocationFix : STOP Fast Dection : not moving or leaving any POI...stop location monitoring");
            stopDetection();   // stop detection when dist off upon location fix done!!!
        }
        return;
    }

    /**
     * currently inside an POI, got new fix, detect whether we are leaving, or switching poi.
     */
    private void detectLeavingPoi(double lat, double lgt, long accuracy, Tuple nextPoiTuple) {
        if (DetectionAlgorithmType.selectDetectionAlgorithmType(mCurPoiTuple) == DetectionAlgorithmType.DETALG_WIFIMATCHING) {
            LSAppLog.pd(TAG, "detectLeavingPoi : current poi has wifi, using wifi matching algorithm:" + mCurPoiTuple.toString());
            return;
        }

        // Now due to missing of wifi, the det algorithm is pure goog fix...no wifi info can be leveraged.

        // keeping POI, nothing changed
        int dist = 0;
        if (nextPoiTuple != null && mCurPoiTuple.equals(nextPoiTuple)) {
            keepingPOI(nextPoiTuple);
            dist = (int)Utils.distanceTo(lat, lgt, nextPoiTuple.getLat(), nextPoiTuple.getLgt());
            //LSAppLog.dbg(mContext, DEBUG_INTERNAL, " detectLeavingPoi: remain in POI:", mCurPoiTuple.getPoiName(), " [ Distance="+dist + " ,lat=" + lat + " ,lgt=" + lgt + " ,accuracy=" +accuracy);
        }
        // switching POI : ( current in a POI and move to another POI)
        else if (nextPoiTuple != null && !mCurPoiTuple.equals(nextPoiTuple)) {
            // given radius is only 100 meters, we should never have overlapped POIs...
            LSAppLog.pd(TAG, "detectLeavingPoi : switch POI: " + mCurPoiTuple.getPoiName() + " ==>" + nextPoiTuple.getPoiName());
            
            switchingPOI(nextPoiTuple);  // decompose to leave and enter
        }
        // leaving a POI : ( current in a POI and moved out of _any_ POI)
        else if (nextPoiTuple == null) {
            dist = (int) Utils.distanceTo(lat, lgt, mCurPoiTuple.getLat(), mCurPoiTuple.getLgt());
            LSAppLog.pd(TAG, "detectLeavingPoi : no wifi info, Really Leaving POI >>>:" + mCurPoiTuple.getPoiName() + "{lat:lgt:accuracy:"+lat+":"+lgt+":"+accuracy+"}");
            leavingPOI();   // leave poi, will do stop detection at the end.
        }
    }

    /**
     * currently not in any POI, got new fix, detect entering a POI.
     */
    private void detectEnteringPoi(double lat, double lgt, long accuracy, Tuple nextPoiTuple) {
        if (DetectionAlgorithmType.selectDetectionAlgorithmType(nextPoiTuple) == DetectionAlgorithmType.DETALG_WIFIMATCHING) {
            LSAppLog.pd(TAG, "detectEnteringPoi : next poi has wifi, using wifi matching algorithm:" + nextPoiTuple.toString());
            return;
        }

        // Now due to missing of wifi, the det algorithm is pure goog fix...no wifi info can be leveraged.

        // entering a POI : (current not in any poi and moved in)
        if (nextPoiTuple != null) {
            float dist = Utils.distanceTo(lat, lgt, nextPoiTuple.getLat(), nextPoiTuple.getLgt());
            LSAppLog.pd(TAG, " detectEnteringPoi: Moving into POI : " + nextPoiTuple.getPoiName() + "[ Distance="+dist + " ,lat=" + lat + " ,lgt=" + lgt + " ,accuracy=" +accuracy);
            enteringPOI(nextPoiTuple);
        }
    }


    /**
     * fine break the logic to differentiate between entering and leaving.
     * we should have larger entering distance...and smaller leaving distance.
     * return valid poi tuple if location is within a POI
     * return null if location is out of any POI
     * Do not change any global variable.
     */
    public Tuple checkPoiDistance(double lat, double lgt, long accuracy) {
        float dist, enteringDistMin = Integer.MAX_VALUE;
        long enteringR, leavingR;
        Tuple enteringPoiTuple = null, leavingPoiTuple = null;
        mMinDist = Integer.MAX_VALUE;

        List<Tuple> poilist = mPoiAdapter.getPoiList();
        synchronized (poilist) {  // returned poi list is already synch wrapped...no sync needed here.
            // find the best match, not the first match.
            for (Tuple entry : poilist) {
                dist = Utils.distanceTo(lat, lgt, entry.getLat(), entry.getLgt());
                if ((int)dist < mMinDist) {
                    mMinDist = (int)dist;
                }
                enteringR = tuneDetectionRadius(entry.getRadius(), accuracy, false);
                LSAppLog.pd(TAG, "checkPoiDistance :: " + entry.getPoiName() +  " dist=" + dist + " enteringR=" + enteringR+ " fix lat:lgt:accu=" + lat+","+lgt+","+accuracy + " Poi Lat:lgt:accuracy=" + entry.getLat()+","+entry.getLgt()+","+entry.getRadius());

                // check entering any POI first.
                if (dist < enteringR && dist < enteringDistMin) {
                    enteringDistMin = dist;
                    enteringPoiTuple = entry;
                    mNextPoiTuple = entry;
                    LSAppLog.pd(TAG, "checkPoiDistance :: entering POI :" + entry.getPoiName() + " dist:enterR:enterDistMin: " + dist+ ":"+enteringR + ":" + enteringDistMin);
                }
            }
        }

        // check whether we are leaving any poi
        if (mCurPoiTuple != null) {
            leavingR = tuneDetectionRadius(mCurPoiTuple.getRadius(), accuracy, true);
            dist = Utils.distanceTo(lat, lgt, mCurPoiTuple.getLat(), mCurPoiTuple.getLgt());
            if (dist < leavingR) {
                leavingPoiTuple = mCurPoiTuple;
                updatePoiCellsWithCurrentBouncingCells(false);  // picking up missing cell along the way out as long as its within radius.
                LSAppLog.pd(TAG, "checkPoiDistance ::  keep current POI=" + mCurPoiTuple.getPoiName() + " dist=" + dist + " accu=" + accuracy + " leavingR=" + leavingR  );
            } else {
                leavingPoiTuple = null;
                LSAppLog.pd(TAG, "checkPoiDistance :: delayed leaving thru beacon check...current POI=" + mCurPoiTuple.getPoiName() + " dist=" + dist + " accu=" + accuracy + " leavingR=" + leavingR  );
            }
        }

        if (enteringPoiTuple != null) {
            LSAppLog.d(TAG, "checkPoiDistance : entering :" + enteringPoiTuple.getPoiName());
            return enteringPoiTuple;
        } else if (leavingPoiTuple != null) {
            LSAppLog.d(TAG, "checkPoiDistance : keeping :" + leavingPoiTuple.getPoiName());
            return leavingPoiTuple;
        } else {
            LSAppLog.d(TAG, "checkPoiDistance : moving to unknown location after failed to find location dist to any poi...");
            return null;
        }
    }

    private long tuneDetectionRadius(long poiAccuracy, long fixAccuracy,  boolean leaving) {
        if (leaving) {
            return Math.max(fixAccuracy, poiAccuracy) + TARGET_RADIUS;
        }
        //enteringR = Math.min(2*TARGET_RADIUS, accuracy+TARGET_RADIUS);
        // case 1: fine poiAccu,   fine fixAccu,   R=min(2*50, fixAccu+50)
        // case 2: fine poiAccu,   coarse fixAccu, R=min(2*50, fixAccu+50)
        // case 3: coarse poiAccu, fine fixAccu:   R=poiAccu
        // case 4: coarse poiAccu, coarse fixAccu: R=poiAccu
        long enteringR = (poiAccuracy > 1000) ? Math.max(poiAccuracy, fixAccuracy) : Math.min(2*TARGET_RADIUS, fixAccuracy+TARGET_RADIUS);
        return Math.max(enteringR, LOCATION_DETECTING_DIST_RADIUS);  // can not detect within less than 200 meter.
        //return (poiAccuracy > 1000) ? Math.max(poiAccuracy, fixAccuracy) : Math.min(2*TARGET_RADIUS, fixAccuracy+TARGET_RADIUS);
    }

    /**
     * check whether discovered location is a configured meaningful location in Poi table.
     * return poitag, if match; null otherwise.
     */
    public String checkDiscoveredLocationPoi(double lat, double lgt, long accuracy, String curpoi, boolean serviceRestarted) {
        String poitag = null;

        LocationDatabase.PoiTable.Tuple poituple = checkPoiDistance(lat, lgt, accuracy);  // consistently use detection's logic
        if (poituple != null) {
            poitag = poituple.getPoiName();
        }
        if (mCurPoiTuple != null) {
            poitag = mCurPoiTuple.getPoiName();
        }
        runDetection("Location discovered and start detection...");

        /** do not change poi from discovery, one path  from run detection inside detection.
        if (poituple != null) {
            poitag = poituple.getPoiName();
            float dist = Utils.distanceTo(lat, lgt, poituple.getLat(), poituple.getLgt());
            LSAppLog.dbg(mContext, DEBUG_INTERNAL, "checkDiscoveredLocationPoi : entering POI", poitag, "lat:lgt=" + lat+"::"+lgt + " Dist="+dist, " notify VSM");
            sendPoiChangedMessage(poituple);
        } else {
            // up to this point, location not matching any POI, means we are leaving
            if (mCurPoiTuple != null) {
                LSAppLog.dbg(mContext, DEBUG_INTERNAL, "checkDiscoveredLocationPoi leaving POI:" + mCurPoiTuple.getPoiName()+ ": notify VSM");
                sendPoiChangedMessage(null);
            } else if (serviceRestarted) { // service was restarted...re-construct last POI...delayed notify vsm
                LSAppLog.d(TAG, "checkDiscoveredLocationPoi :: service restarted, construct last poi.");
                if (null == resetLastPoiUponRestart()) {
                    mLSMan.mLSApp.getVsmProxy().sendVSMLocationUpdate(TRANSIENT);
                    LSAppLog.pd(TAG, "checkDiscoveredLocationPoi:: notify VSM Transient after reset last poi not a poi");
                }
            } else {   //  ok, just send the transient to VSM.
                mLSMan.mLSApp.getVsmProxy().sendVSMLocationUpdate(TRANSIENT);
                LSAppLog.pd(TAG, "checkDiscoveredLocationPoi:: notify VSM Transient");
            }
        }
        **/
        return poitag;
    }

    /**
     * called upon entering/leaving POI. return false if still debouncing, return true if affirmative.
     * @param entering
     * @return
     */
    private boolean detectionDebouncing(boolean entering) {
        if (entering) {
            mDebounceCount++;
            if (mDebounceCount > 2) {
                mDebounceCount = 0;
                return true;
            }
        } else {
            mWifiLeavingDebounceCount = 0;   // reset upon leaving.
            mDebounceCount--;
            if (mDebounceCount < -2) {
                mDebounceCount = 0;
                return true;
            }
        }
        LSAppLog.d(TAG, "detectionDebouncing : entering =" + entering + " still deboucing with count=" + mDebounceCount);
        return false;
    }

    /**
     * take a detailed log when notify VSM.
     */
    private String notifyVSMLog() {
        long now = System.currentTimeMillis();
        String matchlog = " poi="+mCurPoiTuple.getPoiName()+",timestamp="+now;
        if (mCurLoc!=null) {
            // within any POI's radius ?
            double lat = (int)(mCurLoc.getLatitude()*1E6)/1E6;   // 1E6 is good enough
            double lgt = (int)(mCurLoc.getLongitude()*1E6)/1E6;
            long accuracy = mCurLoc.hasAccuracy() ? (long)mCurLoc.getAccuracy() : 0;  // get the accuracy
            matchlog += ",lat="+lat+",lgt="+lgt+",accuracy="+accuracy;
        }
        return matchlog;
    }

    /**
     * detected entering POI, notify VSM and update cur poi
     */
    private void enteringPOI(final Tuple entry) {
        if (!detectionDebouncing(true)) {
            //return;
        }

        String poi = entry.getPoiName();
        mCurPoiTuple = entry;

        updatePoiCellScannedWifiUponEntering();

        mLSMan.mLSApp.mAppPref.setString(AppPreferences.POI, poi);

        // leave wifi on whenever detection on
        // setBeaconState(false);  // turn off wifi after entering
        // clear the mDetCells
        mDetCells.clear();
    }

    /**
     * detected leaving POI, notify VSM and update cur poi
     */
    private void leavingPOI() {
        if (!detectionDebouncing(false)) {
            //return;
        }
        mLSMan.mLSApp.mAppPref.setString(AppPreferences.POI, TRANSIENT);
        mCurPoiTuple = null;
        setBeaconState(false);    // set wifi off after leaving.
        mCurPoiWifiMap.clear();   // clear the map
        mCurPoiBagSsid.clear();
    }

    /**
     * detected switching POI, notify VSM and update cur poi
     *   given radius is only 100 meters, we should never have overlapped POIs...
     */
    private void switchingPOI(final Tuple entry) {
        String leftpoi = mCurPoiTuple.getPoiName();
        leavingPOI();         // first, leave current POI
        enteringPOI(entry);   // second, entering new poi
        LSAppLog.pd(TAG, "switchingPOI :: notify VSM : >>> switchingPOI POI from <<< ::" + leftpoi + " to >>>" + entry.getPoiName());
    }

    /**
     * detected keeping POI, do nothing
     */
    private void keepingPOI(final Tuple entry) {
        String poi = mCurPoiTuple.getPoiName();
        updatePoiCellsWithCurrentBouncingCells(false);
        mWifiLeavingDebounceCount = 0;  // reset count upon not leaving.
        LSAppLog.pd(TAG, "keepingPOI :: POI : " + poi + " did not change, just updating poi celljsons.");
    }

    /**
     * ensure leaving poi happens when we moving out of a POI while the process was killed and restarted.
     * sent only when: 1. poi valid when process got killed. 2. after restarted again, not in any poi.
     */
    private void notifyVSMLeavingPOIUponRestarted() {
        if (!notifiedVSMOnRestart && mCurPoiTuple == null) {
            String poistatus = mLSMan.mLSApp.mAppPref.getString(AppPreferences.POI);
            if (poistatus != null && !TRANSIENT.equals(poistatus)) {
                LSAppLog.pd(TAG, "notifyVSMLeavingPOIUponRestarted : transient");
                mLSMan.mLSApp.getVsmProxy().sendVSMLocationUpdate(TRANSIENT);
                notifiedVSMOnRestart = true;
            }
        }
    }

    /**
     * called from enteringPOI only.
     * update poi celljsons and scanned wifi upon entering. Scanned wifi come from db, so guaranteed to be correct.
     * collect wifi from loctime table by two means: by lat/lng distance match, or by wifi fuzzy match.
     * If privacy is enforced and we write 0 to loctime table, we need to use wifi fuzzy match all the time.
     */
    private void updatePoiCellScannedWifiUponEntering() {
        JSONArray poiWifiSsidArray = null;
        String poiWifiSsid = mCurPoiTuple.getWifiSsid();
        if (poiWifiSsid == null) {
            poiWifiSsid = mBeacon.getLastWifScanSsid();
        }

        if (poiWifiSsid != null) {
            LSAppLog.d(TAG, "updatePoiCellScannedWifiUponEntering : using Wifi :" + poiWifiSsid);
            poiWifiSsidArray = JSONUtils.getJsonArray(poiWifiSsid);
            mLSMan.mStore.collectCellJsonWifiSsid(poiWifiSsid, poiWifiSsidArray, null);
            mCurPoiTuple.setWifiSsid(poiWifiSsidArray.toString());   // set the scanned wifi only
        } else {  // use wifi match as consolidation algo.
            //poiWifiSsidArray = new JSONArray();
            //mLSMan.mStore.collectCellJsonWifiSsid(mCurPoiTuple.getLat(), mCurPoiTuple.getLgt(), mCurPoiTuple.getRadius(), poiWifiSsidArray, null);
            LSAppLog.d(TAG, "updatePoiCellScannedWifiUponEntering : no wifi around poi, do nothing");
        }
        LSAppLog.d(TAG, "updatePoiCellScannedWifiUponEntering : Updated Wifi :" + mCurPoiTuple.getWifiSsid());

        updatePoiCellsWithCurrentBouncingCells(true);  // update POI's cell once entering into a POI with its bouncing cells
        updateCurPoiWifiMap(mCurPoiTuple.getWifiSsid());  // update the in-mem map
    }

    /**
     * update the cell set of poi with current bouncing cells.
     */
    private boolean updatePoiCellsWithCurrentBouncingCells(boolean forceSync) {
        // only use the current cell info, not the entire bouncing cells!! why ?
        //String curcelljson = mTelMon.getValueJSONString();
        String bouncingcelljsons = Utils.convertSetToString(mTelMon.getBouncingCells());
        return updatePoiCellJsons(bouncingcelljsons, forceSync);
    }

    /**
     * merge update all celljsons for this poi, if any
     * @return if any merge has done, false otherwise
     */
    private boolean updatePoiCellJsons(String celljsons, boolean forceSync) {
        // only merge update whenever in a POI
        if (mCurPoiTuple != null) {
            String curPoiCells = mCurPoiTuple.getCellJson();
            String mergedCells = Utils.mergeSetStrings(curPoiCells, celljsons);
            if (mergedCells != null) {
                mCurPoiTuple.setCellJson(mergedCells);
                mPoiAdapter.updatePoi(mCurPoiTuple);
                LSAppLog.d(TAG, "updatePoiCellJsons :: POI : " + mCurPoiTuple.getPoiName() + " celljsons:: " + mergedCells);
                return true;
            } else if (forceSync) {
                mPoiAdapter.updatePoi(mCurPoiTuple);
                LSAppLog.d(TAG, "updatePoiCellJsons :: force sync by wifi update. POI : " + mCurPoiTuple.getPoiName());
                return true;
            }
        }
        return false;
    }

    /**
     * update the poi's wifi set after location discovery.
     * @param matchedpoi
     * @param wifimacs  use the passed in wifi list from lsman exact at the time of discovery.
     * wifissid=[{"wifibssid":"00:14:6c:14:ec:fa","wifissid":"PInternet"}, {}, ...]
     */
    public void updatePoiBeaconAfterDiscovery(String matchedpoi, String wifissids) {
        if (mCurPoiTuple != null) {
            LSAppLog.d(TAG, "updatePoiBeaconAfterDiscovery: discovered poi:" + matchedpoi + " CurPOI:" + mCurPoiTuple.toString());

            mCurPoiTuple.setWifiSsid((JSONUtils.mergeJsonArrayStrings(mCurPoiTuple.getWifiSsid(), wifissids)));
            // XXX no update of POI's lat/lng/radius.
            //LocationDatabase.LocTimeTable.Tuple bestlocpoi = mLSMan.mStore.findMostAccuracyPoiTuple(mCurPoiTuple.getPoiName());
            mPoiAdapter.updatePoi(mCurPoiTuple);
        }
    }

    /**
     * Wifi scan periodically, so this callback func will be run async periodically.
     */
    private void checkBeaconScanResult() {
        // result sent to us by passive beacon listening, always match beacon regardless whether we are in fast detection state.
        // if already in POI, check left if none beacon matches.
        // if not in POI, checck  POI match
        LSAppLog.pd(TAG, "checkBeaconScanResult: passive beacon scan available asyncly and handle it with BEACONSCAN_RESULT");
        matchBeacons();   // now check scanned beacon match any in the database.
    }

    /**
     * Detection Strategy Algorithm: if wifi info available, use wifi match algorithm, otherwise, degrade to goog fix.
     *
     * sometimes, you get wild different location fix even though you did not move, this is called thrashing.
     * to filter out thrashing, use surrounding beacons to filter out thrashing wild location fixes. The
     * business rules to avoid thrashing are:
     *
     * 1. if any scanned bssid matches any known bssid for this POI, then stay, do not leave.
     * 2. if scan bssid does not match any bssid known in the poi, goto step 3, check for ssid match.
     * 3. if the ssid scanned matches any known ssid for the POI, then stay, do not leave.
     *
     * @return true if really leaving, false otherwise.
     */
    private boolean checkLeavingThrashing() {
        if (mCurPoiTuple == null) {
            //mCurPoiTuple might already been set to null by checkDiscoveredLocationPoi after async beacon scan came back.
            LSAppLog.d(TAG, "checkLeavingThrashing : checkDiscoveredLocationPoi already assert leaving, done!");
            return false;
        }

        LSAppLog.d(TAG, "checkLeavingThrashing : poi wifi ssid: " + mCurPoiTuple.getWifiSsid());
        // check whether we can use wifi match algorithm, if not, quit any wifi algorithm.
        if (DetectionAlgorithmType.selectDetectionAlgorithmType(mCurPoiTuple) != DetectionAlgorithmType.DETALG_WIFIMATCHING) {
            LSAppLog.d(TAG, "checkLeavingThrashing : no wifi info for the poi, bail out" + mCurPoiTuple.toString());
            return false;
        }

        boolean leaving = false;
        if (!mBeacon.mWifiId.matchBssid(mCurPoiWifiMap.keySet())) {
            if ( !mBeacon.mWifiId.matchSsid(mCurPoiBagSsid)) {
                leaving = true;
            }
        }

        if (leaving) {
            mWifiLeavingDebounceCount++;
            LSAppLog.d(TAG, "checkLeavingThrashing : wifi leaving counts :" + mWifiLeavingDebounceCount);
            if (mWifiLeavingDebounceCount >= LEAVING_WIFI_DEBOUNCE_COUNT) {
                LSAppLog.pd(TAG, "checkLeavingThrashing : Beacon check: no match...really Leaving POI :" + mCurPoiTuple.getPoiName());
                leavingPOI();   // should we post a message or just call leave directly
                runDetection("Leaving POI, start detection to verify leaving");
                return true;   // now really left
            }
        } else {
            mWifiLeavingDebounceCount = 0;  // reset count upon match.(not leaving).
            LSAppLog.d(TAG, "checkLeavingThrashing : beacon match, not leaving....reset wifi leaving counts :" + mCurPoiTuple.toString());
        }
        // when reach here, either matchmac true, or no mac match, but bag ssid matches.
        LSAppLog.pd(TAG, "checkLeavingThrashing : Beacon check: match...thrashing Leaving  POI :" + mCurPoiTuple.getPoiName());
        return false;
    }

    /**
     * call path: from beacon scan available msg, check beacon scan result, match beacons.
     * scan surrounding wifis as a way to detect entering or leaving a location only when data connection is bad.
     * wifi is the least priority that work only when data connection is not there: google update is the first priority.
     * if wifi matches poi's, then entering is asserted.
     * if inside a poi, and none wifi matches, leaving can be asserted quickly.
     * @return true if decision is made( entering/leaving), false if no decision has been made.
     *
     * Note: update of POI wifi's only happended from lsman after location capture 15 minutes.
     */
    boolean matchBeacons() {
        // check leaving if already inside a POI. will assert leaving if not beacon match.
        if (null != mCurPoiTuple) {
            LSAppLog.pd(TAG, "matchBeacons: checking leaving by calling check leaving thrashing Poi:" + mCurPoiTuple);
            return checkLeavingThrashing();   // will change poi state if no match leaving.
        }

        /* wifi scan is more trustable than google fix, take it if POI has wifi!
        // take wifi scan cautiously because there are bugs in wifi and it can return staled wifi addresses.
        // to against this, we do not take wifi when data connection is there and we are not in vicinity of any poi.
        //if (mMinDist > MONITOR_RADIUS && mLSMan.isDataConnectionGood()) {
        if (mLSMan.isDataConnectionGood()) {   // entering path, only take wifi when no data connection.
            LSAppLog.pd(TAG, "matchBeacons: bail out have data connection and minDist=" + mMinDist);
            return false;
        }
        */

        // now match entering beacons.
        boolean match = false;
        int maxmatches = 2;
        int curmatches = 0;
        Tuple poituple = null;
        Set<String> scannedbeaconset = mBeacon.getLastWifiScanSsidMap().keySet();

        // String indexOf match is easy and quick for bssid b/c bssid is fixed in length!
        List<Tuple> poilist = mPoiAdapter.getPoiList();
        // poituple = getMatchingPoiOfCell(Utils.convertSetToString(mTelMon.getBouncingCells()));
        synchronized (poilist) {  // returned poi list is already synch wrapped...no sync needed here.
            // for each  poi, check the scanned wifi, find the poi with max matches,
            for (Tuple entry : poilist) {
                curmatches = Utils.intersectSetJsonArray(scannedbeaconset, null, entry.getWifiSsid());
                if ( curmatches >= maxmatches) {
                    match = true;
                    poituple = entry;
                    maxmatches = curmatches;  // update the max
                }
            }
        }

        if (match) {   // now match count at least 2, take anything greater than 2!
            LSAppLog.pd(TAG, "matchBeacons: Beacon match...entering POI :" + poituple.getPoiName());
            enteringPOI(poituple);
            runDetection("Beacon match poi, runDetection...");
            return true;
        } else {
            LSAppLog.pd(TAG, "matchBeacons: Beacon no match any poi...:");
            return false;
        }
    }

    /**
     * got Wifi conn state changed, if inside a POI, update POI connected Wifi info.
     * Note, only update connected wifi only when scanned wifi has seen then connected wifi, to avoid issues with stale cur poi.
     *
     * if POI not asserted, lookup all poi info and assert POI if there is a match.
     * Wifi connected will always stop location request because run detection will stop location request.
     * If we assert POI here with msg ENTER_POI, run detection will be called after entering poi, which will stop location req.
     * wifissid=[{"wifibssid":"00:14:6c:14:ec:fa","wifissid":"PInternet"}, {}, ...]
     *
     * Note: if cur poi disagree with the poi from connected wifi, assert POI from connected wifi for now.
     */
    private void handleWifiConnStateChanged(String bssid) {
        if (bssid != null) {  // valid bssid, wifi connected !
            Tuple poi = getMatchingPoiByConnWifi(bssid);
            // case 1, this connected wifi was not associated with any POI. this is a new connected wifi.
            if (null == poi) {
                // now if we are current already inside a poi, update this information into poi table.
                if (mCurPoiTuple != null) {
                    String scannedwifi = mCurPoiTuple.getWifiSsid();
                    if (scannedwifi != null && scannedwifi.indexOf(bssid) >= 0) {  // promote to connected wifi only from scanned wifi pool.
                        String dbWifis = mCurPoiTuple.getWifiConnMac();
                        if (dbWifis != null && dbWifis.length() > 0) {
                            dbWifis += Utils.DELIM + bssid;
                        } else {
                            dbWifis = bssid;
                        }
                        mCurPoiTuple.setWifiConnMac(dbWifis);
                        mPoiAdapter.updatePoi(mCurPoiTuple);
                        LSAppLog.pd(TAG, "handleWifiConnStateChanged: update bssid into cur poi" + mCurPoiTuple.toString());
                    }  // end of check scanned wifi
                    // we are not inside any poi, do nothing for this connected wifi.
                } else {
                    LSAppLog.pd(TAG, "handleWifiConnStateChanged: conn happened outside any poi, do nothing");
                }
                // case 2, this connected wifi was associated with some POI.
            } else {
                // I am not inside any POI for now, assert POI based on wifi connection info.
                if (mCurPoiTuple == null) {
                    // XXX for mobile station, compare poi scan list to current scan list, assert POI only when at least more than 2 matches.
                    if (Utils.intersectSetJsonArray(mBeacon.getLastWifiScanSsidMap().keySet(), null, poi.getWifiSsid()) >= FUZZY_MATCH_MIN) {
                        mCurPoiTuple = poi;   // take poi from conned wifi.
                        LSAppLog.pd(TAG, "handleWifiConnStateChanged: poi found from db:" + poi.toString() + " assert POI as conned wifi is not mobile station");
                        sendPoiChangedMessage(mCurPoiTuple);
                    } else {
                        LSAppLog.pd(TAG, "handleWifiConnStateChanged: poi found from db:" + poi.toString() + " do not assert POI as conned wifi is mobile station");
                    }
                } else if (mCurPoiTuple != null && mCurPoiTuple.getPoiName().equals(poi.getPoiName())) {
                    LSAppLog.pd(TAG, "handleWifiConnStateChanged: poi found from db:" + poi.toString() + " current poi:"+ mCurPoiTuple.toString());
                } else {
                    // XXX if cur poi not agree with poi from connected wifi, which poi should we take ?
                    // Per venki, can not take wifi conn's due to mobile station issue....always take poi from lat/lng distance.
                    LSAppLog.pd(TAG, "handleWifiConnStateChanged: poi found from db:" + poi.toString() + " not agree with cur poi:"+ mCurPoiTuple);
                }
            }
            // this is wifi disconnected when got null as paramaters
        } else {
            LSAppLog.d(TAG, "handleWifiConnStateChanged: wifi disconnected !!!");
        }
        // now wifi connect state changed, adjust detection state accordingly by stopping location request inside run detection.
        runDetection("Run Detection Upon wifi conn state changed");
    }

    /**
     * upon POI table refresh, if there is an POI without cell info, that means the poi is newly tagged POI.
     * If the last know location is the same as this tagged POI, means user just tagged current location as POI.
     * Assert the entering of POI immediately !!!
     */
    private void onPoiRefresh() {
        boolean foundPoi = false;
        Location curloc;
        double lat, lgt, accuracy;

        List<Tuple> poilist = mPoiAdapter.getPoiList();
        for (Tuple entry : poilist ) {
            if ( entry.getCellJson() == null &&    // this is a newly manually tagged poi. no cell info
                    (null != (curloc = mLocationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)))) {
                lat = curloc.getLatitude();
                lgt = curloc.getLongitude();
                accuracy = curloc.getAccuracy();
                LSAppLog.d(TAG, "onPoiRefresh: lastknownlocation:" + curloc.toString());

                if ((!Utils.compareDouble(lat, entry.getLat()) && !Utils.compareDouble(lgt, entry.getLgt())) ||
                        Utils.distanceTo(curloc.getLatitude(), curloc.getLongitude(), entry.getLat(), entry.getLgt()) <= Math.max(LOCATION_DETECTING_DIST_RADIUS, accuracy)) {
                    LSAppLog.pd(TAG, "onPoiRefresh: LastKnownLocation matches POI:" + entry.toString());
                    foundPoi = true;
                    updateDetectionState(entry);
                    break;
                }
            }
        }

        if (!foundPoi) {
            runDetection("Run detection upon POI_REFRESH...");
        }
    }

    /**
     * club all the set beacon(wifi, bluetooth, etc) state functionality inside here
     */
    private void setBeaconState(boolean onAction) {
        // to avoid keep turning on wifi hence reduce battery drain, only turn on wifi when detecting entering an poi.
        // inside a poi, no need to turn on wifi all the time.
        if (mCurPoiTuple == null || !onAction) {  // Take action when not in a poi OR I am turning it off
            mBeacon.setWifi(onAction);
        } else {
            // mBeacon.setWifi(onAction);   // added for now until accuracy and battery test
            LSAppLog.d(TAG, "set wifi : do nothing when request on and we are already inside poi:"+mCurPoiTuple.getPoiName());
        }
    }

    /**
     * when we started, re-construct last meaningful location information.
     */
    private Tuple resetLastPoiUponRestart() {
        LocationDatabase.LocTimeTable.Tuple t = mLSMan.mStore.getLastLocation();
        if (t != null) {
            // do not do distance check due to the coarse fix from instant gratification upon re-start.
            LocationDatabase.PoiTable.Tuple poituple = null;
            String poiTag = t.getPoiTag();

            if (poiTag != null && poiTag.trim().length() > 0) {
                //LocationDatabase.PoiTable.Tuple poituple = checkPoiDistance(t.getLat(), t.getLgt(), t.getAccuracy());  // consistently use detection's logic
                poituple = mPoiAdapter.getPoiEntry(poiTag);
            }

            if (poituple != null) {
                LSAppLog.pd(TAG, "resetLastPoiUponRestart :" + poituple.getPoiName()+ "::" + t.getLat() + " ::" + t.getLgt() + " ::");
                sendPoiChangedMessage(poituple);
                return poituple;
            }
        }
        return null;
    }

    /**
     * limit bouncing cell size and FIFO update it to avoid carrying a big bag of cells.
     * @param cells
     */
    private void shiftAddBouncingCells(String cells) {
        if (!mDetCells.isEmpty() && mDetCells.size() >= DET_BOUNCING_CELL_SIZE) {
            mDetCells.remove(0);
        }
        mDetCells.add(cells);
    }

    /**
     * set the current detection status with the poi tuple. If poituple is null, means leaving current meaningful location.
     * @param poituple
     */
    public void  updateDetectionState(final Tuple poituple) {
        if (poituple != null) {
            enteringPOI(poituple);
            runDetection("Run detection upon updateDetectionState="+poituple.getPoiName());
        } else if (mCurPoiTuple != null) {
            // run thru full path of loc fix, distance check, and wifi check before really leaving.
            runDetection("Run detection upon discovery telling us to leave POI="+mCurPoiTuple.getPoiName());
            //leavingPOI();   //will cause mCurPoiTuple to be null;
        }
    }

    /**
     * whenever cur poi changed, get the ssid from cur poi db, populate the cur poi wifi map.
     * @param ssidjsonarray is the string repr of wifi json array.
     */
    private void updateCurPoiWifiMap(String ssidjsonarray) {
        mCurPoiWifiMap.clear();
        mCurPoiBagSsid.clear();
        JSONUtils.convertJSonArrayToMap(ssidjsonarray, LS_JSON_WIFIBSSID, LS_JSON_WIFISSID, mCurPoiWifiMap, mCurPoiBagSsid);
    }

    /**
     * post a entering or leaving message to self so that changing of global state got sequentialized to avoid race condition.
     * @param poituple
     */
    private void sendPoiChangedMessage(final Tuple poituple) {
        Message msg = mHandler.obtainMessage();
        if (poituple != null) {
            msg.what = Msg.ENTERING_POI;
        } else {
            msg.what = Msg.LEAVING_POI;
        }
        msg.obj = poituple;
        mHandler.sendMessage(msg);
    }

    /**
     * just request a periodical scan result upon periodical timer intent.
     */
    private void startScan() {
        mBeacon.startBeaconScan(null, mHandler);
    }

    /**
     * looping thru all the POIs and add alert
     */
    @Deprecated
    private void addProximityAlerts() {
        if (false) { // comment out until battery test find out whether this drain battery.
            Intent alertintent = new Intent(LOCATION_DETECTION_POI);
            mProximityAlertIntent = PendingIntent.getBroadcast(mContext, 0, alertintent,  PendingIntent.FLAG_UPDATE_CURRENT);
            LSAppLog.d(TAG, "addProximityAlerts : setting with intent :" + alertintent.toString());

            for (Tuple entry : mPoiAdapter.getPoiList() ) {
                mLocationManager.addProximityAlert(entry.getLat(), entry.getLgt(), MONITOR_RADIUS, -1, mProximityAlertIntent);
                LSAppLog.d(TAG, "addProximityAlerts :: " + entry.getPoiName() + "  :: lat:lgt:=" + entry.getLat()+ ":"+entry.getLgt());
            }
        }
    }

    // Utilize location manager Proximity Alert from android directly
    final static private class ProximityAlertReceiver extends BroadcastReceiver {
        private final Context mContext;
        private final Handler mHandler;
        private boolean mStarted = false;

        public ProximityAlertReceiver(final Context ctx, final Handler handler) {
            this.mContext = ctx;
            this.mHandler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Message msg = mHandler.obtainMessage();
            LSAppLog.d(TAG, "ProximityAlertReceiver : onReceiver :" + action);

            if (LOCATION_DETECTION_POI.equals(action)) {
                msg.what = Msg.PROXIMITY_ALERT;
                mHandler.sendMessage(msg);
            }
        }

        synchronized final void start() {
            if (!mStarted) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(LOCATION_DETECTION_POI);
                mContext.registerReceiver(this, filter);
                mStarted = true;
                LSAppLog.d(TAG, "ProximityAlertReceiver : started and registered");
            }
        }

        synchronized final void stop() {
            if (mStarted) {
                mContext.unregisterReceiver(this);
                mStarted = false;
                LSAppLog.d(TAG, "ProximityAlertReceiver : stoped and unregistered");
            }
        }
    }

    /**
     * handle location fix available pending intent. Not used for now!
     */
    @Deprecated
    private void handleLocationFixFromPendingIntent(Location loc) {
        // just take a log for now
        LSAppLog.pd(TAG, "handleLocationFixFromPendingIntent: onLocationChanged() :" + loc.toString());
    }

    /**
     *  The Bcast receiver for requesting location update thru pendingIntent
     */
    @Deprecated
    final static private class LocationUpdatePendingIntentReceiver extends BroadcastReceiver {
        private final Context mContext;
        private final Handler mHandler;
        private boolean mStarted = false;

        public LocationUpdatePendingIntentReceiver(final Context ctx, final Handler handler) {
            this.mContext = ctx;
            this.mHandler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Message msg = mHandler.obtainMessage();
            LSAppLog.d(TAG, "LocationUpdateReceiver : onReceiver :" + action);

            if (LOCATION_UPDATE_AVAILABLE.equals(action)) {
                if (intent.getExtras() != null) {
                    Location loc = (Location)intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
                    msg.what = Msg.LOCATION_PENDINGINTENT;
                    msg.obj = loc;
                    mHandler.sendMessage(msg);
                }
            }
        }

        synchronized final void start() {
            if (!mStarted) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(LOCATION_UPDATE_AVAILABLE);
                mContext.registerReceiver(this, filter);
                mStarted = true;
                LSAppLog.d(TAG, "LocationUpdateReceiver : started and registered");
            }
        }

        synchronized final void stop() {
            if (mStarted) {
                mContext.unregisterReceiver(this);
                mStarted = false;
                LSAppLog.d(TAG, "LocationUpdateReceiver : stoped and unregistered");
            }
        }
    }
}
