package com.locationstream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;

import com.locationstream.LocationSensorApp.LSAppLog;
import static com.locationstream.Constants.*;

/**
 *<code><pre>
 * CLASS:
 *  implements Beacon(wifi and bt) scan logic for better location accuracy and better detection.
 *
 * RESPONSIBILITIES:
 *  turn on wifi and initiate wifi scan if possible.
 *
 * COLABORATORS:
 *   Location sensor manager and location detection.
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
public final class BeaconSensors {
    public static final String TAG = "LSAPP_Beacon";

    private static final IntentFilter mWifiStautsFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    private static final IntentFilter mWifiScanResultFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    private static final IntentFilter mBTDeviceFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    private static final IntentFilter mBTScanDoneFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

    private LocationSensorManager mLSMan;

    // a list of message handler from the upper layer apps so we can send message to them. Now mainly lsman and detection.
    private List<Handler> mCallerHandlers;

    private AtomicInteger mWifiConnState;   // wifi connection state, disconnected(0), connected(1),
    private boolean mWifiScanInitedByMe = false;  // when wifi scan result came by, only take the one inited by me.

    // Algorithm: Automated self driven module to populate and parse {cell, surrounding} table.
    // 1. always use cell json string as key, and a string set of address as value.
    // 2. reverse lookup is hard, not a use case at all if cell id always available, e.g., which loc this ssid belongs to ?
    // 3. convert string
    WifiId mWifiId;                 // contains latest scanned wifi bssid and ssid and encapsulated id comparation logic there.
    long mLastScanTimestamp;	// read-only outside the module.
    private String mPOI;   // the poi tag we are matching against.

    WifiManager mWifiMan;       // Wifi manager
    protected List<ScanResult> mAPs;
    private enum WifiState { UNINITIALIZED, DISABLE, ENABLE }   // my wifi status
    private boolean mWifiOnByMe = false;

    private BluetoothAdapter mBTAdapter;  // bluetooth adapter.

    @SuppressWarnings("unused")
    private BeaconSensors() {} // hide default constructor

    /**
     * constructor, remember the reference to location sensor manager and telephone monitor.
     * @param lsman
     * @param telmon
     */
    public BeaconSensors(LocationSensorManager lsman, TelephonyMonitor telmon) {
        mLSMan = lsman;

        mWifiMan = (WifiManager)mLSMan.getSystemService(Context.WIFI_SERVICE); // should always have bt

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBTAdapter != null) {
            /* first, set the bluetooth name for pairing */
            // mBTAdapter.setName(TAG); // IKSTABLEFIVE-1147, can not randomly set device name.
            LSAppLog.pd(TAG, "BTMan init() successfully");
        } else {
            // Device does not support Bluetooth
            LSAppLog.pd(TAG, "BTMan init() Could not get bluetooth adapter");
        }

        mPOI = null;
        //mCallerHandlers = Collections.synchronizedList(new ArrayList<Handler>());
        mCallerHandlers = new ArrayList<Handler>();  // should use hashMap, put-if-absent idiom ?

        mWifiConnState = new AtomicInteger();
        mWifiConnState.set(0);  // init to disconnected
        mWifiId = new WifiId();  // inited inside constructor.

        mWifiStautsFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mLSMan.registerReceiver(mWifiStatusReceiver, mWifiStautsFilter);
        registerBeaconReceivers();  // always registered to wifi scan.
    }

    /**
     *  the wifi scan callback receiver to get a list of wifi scan mac addr set.
     */
    private BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {  // scan will happen every 5 min.
                mLastScanTimestamp = System.currentTimeMillis();  // note down the scan result timestamp!
                getWifiScanResultJson();    // populate the addr set from scan result
                doneBeaconScan(mWifiScanReceiver);  // notify the scan result
            }
        }
    };

    /**
     * Wifi status change listener to reset flag to indicate whether wifi turned on by me.
     */
    private BroadcastReceiver mWifiStatusReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {  // Wifi Enabled Disabled
                // all I care is on....if anybody turn on wifi..cancel my pending action for me to always off wifi.
                // ignore state change caused by me ! (on while saved is disable, off when
                if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_ENABLED) {
                    LSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state enable...don't care.");
                } else if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_DISABLED) {
                    mWifiOnByMe = false;
                    LSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state disabled...clear and disable myself also!");
                } else if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_DISABLING) {
                    LSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state disabling...wait for disabled!");
                }
            }
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) { // Wifi connection
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                        if (intent.getStringExtra(WifiManager.EXTRA_BSSID) != null) {
                            mWifiConnState.set(1);
                            LSAppLog.pd(TAG, "mWifiStatusReceiver : Wifi connection state changed...connected!" + intent.getStringExtra(WifiManager.EXTRA_BSSID));
                            notifyWifiConnected(intent.getStringExtra(WifiManager.EXTRA_BSSID));
                        }
                    }
                    if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.DISCONNECTED) {
                        mWifiConnState.set(0);
                        LSAppLog.pd(TAG, "mWifiStatusReceiver : Wifi connection state changed...disconnected!");
                        notifyWifiConnected(null);   // send null to indicate wifi disconnected.
                    }
                }
            }
        }
    };

    /**
     * For _EACH_ device, _ONE_ ACTION_FOUND Intent will be broadcast
     */
    private BroadcastReceiver mBTScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    LSAppLog.d(TAG, "mBTScanReceiver onRecive() Discovered device :" + device.getName() + ":" + device.getAddress());
                }
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                LSAppLog.d(TAG, "mBTScanReceiver DISCOVERY_FINISHED");
                unregisterBeaconReceivers(mBTScanReceiver);  // un-register this listener
                //doneBeaconScan(mBTScanReceiver);
            }
        }
    };

    /**
     * register listener every time we start a scan, and un-register once scan done.
     */
    private void registerBeaconReceivers() {
        // all async device scan listeners
        mLSMan.registerReceiver(mWifiScanReceiver, mWifiScanResultFilter);  // register receiver, harmless if already registered
        //mLSMan.registerReceiver(mBTScanReceiver, mBTDeviceFilter);
        //mLSMan.registerReceiver(mBTScanReceiver, mBTScanDoneFilter);
    }

    /**
     * remove the registered listener.
     */
    private void unregisterBeaconReceivers(BroadcastReceiver recv) {
        try {
            mLSMan.unregisterReceiver(recv);
        } catch (Exception e) {
            LSAppLog.d(TAG, e.toString());
        }
    }

    /**
     * clean up all registered listeners upon cleanup, when object is being destroyed.
     */
    public void cleanupReceivers() {
        try {
            mLSMan.unregisterReceiver(mWifiStatusReceiver); // wifi on off status
            mLSMan.unregisterReceiver(mWifiScanReceiver);   // wouldn't hurt if unregister two more times
        } catch (Exception e) {
            LSAppLog.d(TAG, e.toString());
        }
    }

    /**
     * inner class that encapsulate wifi bssid and ssid and the logic of comparing bssid and ssid
     */
    static class WifiId {
        Map<String, String> mWifiMap;  // the wifi map of the latest round of scan result. Key is bssid, value is ssid.
        JSONArray mWifiSSID;    // all the ssid of the surrounding wifis
        String mWifiConnMac;    // the connected wifi bssid, you can only have one connected wifi at any time.

        public WifiId() {
            mWifiMap = Collections.synchronizedMap(new HashMap<String, String>());    // passed in idmap is inited somewhere.
            mWifiSSID = null;
            mWifiConnMac = null;
        }

        public void updateWithLastestScanResult(Map<String, String> map) {
            mWifiMap = map;    // update the map with the latest, if any.
        }

        /**
         * pass in a set of bssid, find out whether there is any match of passed in bssid in the latest scanned wifi.
         * @param bssid  the bssid arg normally from db poi information.
         * @return true if there are any matches, false otherwise.
         */
        public boolean matchBssid(Set<String> bssidset) {
            return Utils.fuzzyMatchSets(bssidset, mWifiMap.keySet(), true);
        }

        /**
         * pass in a set of ssid, which is poi's bag ssid, and match them against the latest scanned ssids.
         * @param ssidset  the bag ssid set from poi
         * @return true if there are any matches, false otherwise.
         */
        public boolean matchSsid(Set<String> ssidset) {
            return Utils.fuzzyMatchSetInMapValue(ssidset, mWifiMap) > 0 ? true : false;
        }
    }

    /**
     * initiate a beacon scan to devices surround this poi
     * called from LSMan after 15 min when POI location is saved...or upon leave POI.
     * output: update the db table with scanned MAC addr
     * send BEACONSCAN_RESULT to LSMan
     */
    public boolean startBeaconScan(String poi, Handler hdl) {
        addCallerHandler(hdl);

        mPOI = poi;         // concurrently set poi ?
        registerBeaconReceivers();
        //setWifi(true);   // enable wifi before scan! XXX do not enable wifi in any case.
        mWifiMan.startScan();

        // need user's permission to enable bluetooth
        // if (mBTAdapter != null)
        //    mBTAdapter.startDiscovery();  // no effect if bt not enabled by user permission.

        LSAppLog.pd(TAG, "startBeaconScan...upon settle down POI=" + mPOI);
        return true;
    }

    /*
     * Android will do wifi scan periodically(5 min) if wifi is on, passively listening to that.
     */
    public boolean startPassiveBeaconListen(Handler hdl) {
        addCallerHandler(hdl);
        registerBeaconReceivers();  // register receiver first, harmless if already registered
        return true;
    }

    /**
     * remove the passive wifi scan listener.
     * @param hdl  the message handler of the caller...could be lsman or detection.
     */
    public void stopPassiveBeaconListen(Handler hdl) {
        removeCallerHandler(hdl);
        try {
            // No need to unregister receiver, keep the wifi listener registered in hope to trigger cell tower changes timely.
            // unregisterBeaconReceivers(mWifiScanReceiver);
        } catch (Exception e) {
            LSAppLog.d(TAG, e.toString());
        }
        LSAppLog.d(TAG, "stopPassiveBeaconListen: stop passive wifi scan listening upon curpoi detected or left poi!");
    }

    /**
     * called from location sensor manager beacon scan result processing.
     * @param hdl
     */
    public void removeCallerHandler(Handler hdl) {
        synchronized (mCallerHandlers) {
            if (mCallerHandlers.contains(hdl)) {
                mCallerHandlers.remove(hdl);
            }
            LSAppLog.d(TAG, "RemoveCallerHander : caller:" + hdl + "Callers :" + mCallerHandlers.toString());
        }
    }

    /**
     * the caller want to receive wifi scan result thru message sent to its handler.
     * @param hdl  caller's handler.
     */
    private void addCallerHandler(Handler hdl) {
        synchronized (mCallerHandlers) {
            if (! mCallerHandlers.contains(hdl)) {
                mCallerHandlers.add(hdl);   // add handler as observer anyway
            }
        }
    }

    /**
     * notify all the registered handlers upon wifi scan result thru async message.
     */
    private void notifyAllRegisteredHandlers(int what, Object obj, Bundle data) {   // notify all the callers explicitly registered!
        synchronized (mCallerHandlers) {
            for (Handler hdl : mCallerHandlers) {
                LSAppLog.pd(TAG, "notifyAllCallers : notify caller POI=" + mPOI + " caller:" + hdl);
                LocationSensorApp.sendMessage(hdl, what, obj, data);
            }
        }
    }

    /**
     * Insert scan results into db. driven by scan result available and bt discovery finished from listeners!
     */
    private void doneBeaconScan(BroadcastReceiver receiver) {
        boolean scanresult = false;

        if (mCallerHandlers.size() == 0) {  // detection will always registered to beacon scan results.
            LSAppLog.d(TAG, "doneBeaconScan: nobody cares, do nothing...:" + mCallerHandlers.size());
            return;
        }

        if (!mWifiScanInitedByMe) {
            mWifiScanInitedByMe = true;
            scanresult = mWifiMan.startScan();  // will get another scan result back
            LSAppLog.d(TAG, "doneBeaconScan: start my own scan return:" + scanresult);
        } else {
            mWifiScanInitedByMe = false;
            notifyAllRegisteredHandlers(BEACONSCAN_RESULT, (Object)mPOI, null);
        }
    }

    /**
     * notify detection the connected wifi's bssid
     */
    private void notifyWifiConnected(String bssid) {
        mWifiId.mWifiConnMac = bssid;
        LSAppLog.d(TAG, "notifyWifiConnected: " + mWifiId.mWifiConnMac);
        LocationSensorApp.sendMessage(mLSMan.getDetection().getDetectionHandler(), WIFI_CONNECTED, bssid, null);
    }

    /**
     * all we care is list of ssids...json is overkill, collect all ssids into address set.
     */
    synchronized void getWifiScanResultJson() {
        mAPs = mWifiMan.getScanResults();  // if wifi is off, this could return null
        mWifiId.mWifiMap.clear();
        mWifiId.mWifiSSID = new JSONArray();  // this is used to update into db

        if (null != mAPs && mAPs.size() > 0) {
            try {
                for (ScanResult entry : mAPs) {
                    JSONObject entryjson = new JSONObject();
                    entryjson.put(LS_JSON_WIFISSID, entry.SSID);   // should never be empty
                    entryjson.put(LS_JSON_WIFIBSSID, entry.BSSID);
                    mWifiId.mWifiSSID.put(entryjson);
                    mWifiId.mWifiMap.put(entry.BSSID, entry.SSID);   // put into map.

                    LSAppLog.d(TAG, "getWifiJson: ScanResult :" + entry.BSSID);
                }
            } catch (JSONException e) {
                LSAppLog.e(TAG, "getWifiJson Exception:" + e.toString());
                mWifiId.mWifiSSID = null;
                mWifiId.mWifiMap.clear();
            }
        }
    }

    /**
     * @return scanned wifi ssid as a compact JSON string
     */
    public String getLastWifScanSsid() {
        if (mWifiId.mWifiSSID != null) {
            return mWifiId.mWifiSSID.toString(); // the string in the db {"wifibssid":"00:23:f8:6f:31:94","wifissid":"ZyXEL"}
        }
        return null;
    }

    /**
     * @return a copy of scanned wifi ssids as a map
     */
    public Map<String, String> getLastWifiScanSsidMap() {
        //return Collections.unmodifiableMap(mWifiMap);
        return mWifiId.mWifiMap;
    }

    /**
     * @return the current connected wifi mac
     */
    public String getConnectedWifi() {
        return mWifiId.mWifiConnMac;
    }

    /**
     * No need to worry about success of turning it on, because if it doesn't come on,
     * we won't be burning any extra battery because WiFi listening is passive.
     *
     * Logic table:
     * getWiFiState()   onAction   onByMeFlag  Result
     * ---------------  --------   ----------  ------
     *       ON           ON           false    leave
     *       ON           OFF          true     turnOff
     *       ON           OFF          false    leave
     *       OFF          ON           true     turnOn
     *       OFF          OFF            x      leave
     * The Wifi State listener will reset onByMeFlag is wifi is on by me and user turned it off.
     * No need to worry if not called in pair....screen off will turn WiFi off always.
     *    Log: WifiService: setWifiEnabled enable=false, persist=true, pid=14941, uid=10006
     * @param onAction on or off
     */
    public void setWifi(boolean onAction) {
        WifiState curstate = WifiState.UNINITIALIZED;

        // reduce states to binary states
        int state = mWifiMan.getWifiState();
        if (state == WifiManager.WIFI_STATE_DISABLED || state == WifiManager.WIFI_STATE_DISABLING) {
            curstate = WifiState.DISABLE;
        } else if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING) {
            curstate = WifiState.ENABLE;
        } else {
            LSAppLog.d(TAG, "setWifi: what I am supposed to do if unknown state");
            return;
        }

        // three do nothing situations.
        // 1.on : it it is already on, do nothing.
        // 2.off: if it is not me who turned wifi on, do nothing.
        // 3.Airplane mode on.
        // 4.Wifi hotspot on
        if ( (onAction  && curstate == WifiState.ENABLE) ||
                (!onAction && curstate == WifiState.DISABLE) ||
                (onAction && Utils.isAirplaneModeOn((Context)mLSMan)) 
           ) {
            LSAppLog.d(TAG, "setWifi: do nothing : action=" + onAction + ": curWifiState=" + curstate + ": or airplane mode on: or wifi hotspot on.");
            return;
        }

        if (onAction) {
            mWifiOnByMe = true;
            LSAppLog.d(TAG, "setWifi :: ON : saving Current wifi state before enabling : savedstate: " + curstate);
            mWifiMan.setWifiEnabled(true);    // on action
        } else if (mWifiOnByMe && mLSMan.getLSManState() != LocationSensorManager.LSManState.TIMEREXPIRED_WAITING4FIX) {
            WifiInfo wifiInfo = mWifiMan.getConnectionInfo();
            if (wifiInfo != null && SupplicantState.COMPLETED == wifiInfo.getSupplicantState()) {
                LSAppLog.d(TAG, "setWifi :: OFF: not off because wifi connection on:" + mWifiMan.getConnectionInfo());
            } else {
                mWifiOnByMe = false;
                mWifiMan.setWifiEnabled(false);   // restore
                LSAppLog.d(TAG, "setWifi :: OFF: restoring disabled WIFI : savedstate : " + curstate);
            }
        } else {
            LSAppLog.d(TAG, "setWifi :: OFF: leave wifi on because not turned on by me !");
        }
    }

    /**
     * get the current wifi state, reduce the states to two states, on and off.
     * @return
     */
    public boolean isWifiEnabled() {
        int state = mWifiMan.getWifiState();
        if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    /**
     * get the current wifi connection state,
     * @return true if wifi supplicant state is COMPLETE, false otherwise
     */
    public boolean isWifiConnected() {
        WifiInfo wifiInfo = mWifiMan.getConnectionInfo();
        if (wifiInfo != null && SupplicantState.COMPLETED == wifiInfo.getSupplicantState()) {
            LSAppLog.d(TAG, "isWifiConnected :: Yes, Wifi state is completed: " + wifiInfo.getSupplicantState());
            return true;
        }
        return false;
    }

    /**
     * return the current wifi conn state by checking class state variable directly.
     * State flag in updated inside wifi state change callback.
     * @return wifi conn state, 0:disconnected, 1:connected.
     */
    public int getWifiConnState() {
        return mWifiConnState.get();
    }
}
