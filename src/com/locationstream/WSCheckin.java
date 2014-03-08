package com.locationstream;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.DefaultHttpClient;

import android.text.format.DateUtils;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.facebook.FacebookApi;
import com.locationstream.foursquare.FoursquareHttpApi;
import com.locationstream.foursquare.VenueParser;
import com.locationstream.foursquare.VenueParser.Venue;
import com.locationstream.twitter.TwitterApi;
import com.locationstream.ws.LSWSBase;
import com.locationstream.ws.WSRequest;
import com.locationstream.ws.WSResponse;

// All the ws aggregator...
public class WSCheckin implements LSWSBase.WSRequestCallback {
    public static final String TAG = "API_Checkin";

    private LocationSensorApp mLSApp;

    private FacebookApi mFBApi;
    private FoursquareHttpApi mFSApi;  // foursquare ws api, use global instance in ls app
    private TwitterApi mTWApi;
    private final DefaultHttpClient mHttpClient;

    // all the ws collection
    private Map<String, WebServiceApi> mWSMap = new HashMap<String, WebServiceApi>();

    private LocationSensorManager mLSMan;   // use for getting current loc info for checkin
    private LSWSBase mWSBase;
    private WSRequest mReq;
    private WSResponse mResp;

    // ok, checkin module should be in lsapp, for now, it lives inside lsman.
    public WSCheckin(LocationSensorApp lsapp) {
        mLSApp = lsapp;
        mLSMan = lsapp.getLSMan();

        // this is the only place global fs ws api got created!!!! keep global refer to shared SNS
        mFBApi = new FacebookApi(mLSApp);     // should be singleton.
        mFSApi = new FoursquareHttpApi(mLSApp);  // not use oauth for now
        mTWApi = new TwitterApi(R.drawable.twitter);
        mWSMap.put("Facebook", mFBApi);
        mWSMap.put("Foursquare", mFSApi);
        //mWSMap.put("Twitter", mTWApi);

        // legacy of using wsbase for foursquare checkin.
        //mWSBase = new LSWSBase(mLSMan, false);
        //mReq = new WSRequest("http://api.foursquare.com", "8472718084:passwd");

        mHttpClient = new DefaultHttpClient();
        LSAppLog.e(TAG, "Checkin constructor");

        loadDefaultSessions();  // need set cred for the newly created socket, http client.
    }

    public FacebookApi getFacebook() {
        return  mFBApi;
    }
    public FoursquareHttpApi getFoursquare() {
        return mFSApi;
    }
    public TwitterApi getTwitter() {
        return mTWApi;
    }

    public void loadDefaultSessions() {
        // Log into Foursquare, if we can.
        mFSApi.loadFoursquare();
    }


    public boolean handleResponse(WSResponse resp) {
        LSAppLog.e(TAG, resp.toString());
        return true;
    }

    public void wsbaseCheckin() {
        // first, get and set the url to this request
        //String venuesurl = mReq.getVenusUrl(42.977, -80.009);
        String venuesurl = mReq.getCheckinUrl(355004);
        mWSBase.initiateRequest(mReq, this);
    }

    public boolean isFSApiReady() {
        return mFSApi.hasCredentials(); // && !TextUtils.isEmpty(getUserId());
    }

    public void checkinCurrentLocation(String lat, String lgt, String accuracy, String place) {
    	// timestamp:2011-01-04 17:02:43,lat:42.288407,lgt:-88​.000429,accuracy:60,poi:null
    	// new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
    	String status = "timestamp:"+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())+ ",lat:" + lat + ",lgt:"+lgt+",accuracy:"+accuracy+",poi:"+place;
        try {
            mFSApi.hasCredentials();
            List<Venue> venues = listNearByVenues(lat, lgt);
            if (venues.size() > 0) {
                // checkin the first one in the venue list  to foursquare
                mFSApi.checkin(venues.get(0).mId, null, lat, lgt, null, null, null, null, false, false, false, false);
                LSAppLog.e(TAG, "checkinCurrentLocation Foursqure: " + venues.get(0).mName);
            } else {
                mFSApi.checkin(null, null, lat, lgt, null, null, null, null, false, false, false, false);
            }
        }catch(Exception e) {
            LSAppLog.e(TAG, "checkinCurrentLocation Foursqure:" + e.toString());
            //mFSApi.clearAllCredentials();
        }
        
        try{
        	LSAppLog.e(TAG, "checkinCurrentLocation Facebook:" + status);
        	mFBApi.updateStatus(status);
        }catch(Exception e){
        	LSAppLog.e(TAG, "checkinCurrentLocation Facebook:" + e.toString());
        }
        
        try{
        	LSAppLog.e(TAG, "checkinCurrentLocation Twitter:" + status);
        	mTWApi.updateStatus(status);
        }catch(Exception e){
        	LSAppLog.e(TAG, "checkinCurrentLocation Twitter:" + e.toString());
        }
    }

    public void httpapiCheckin() {
        try {
            mFSApi.hasCredentials(); // check credential first
            mFSApi.checkin("355004", null, null, null, null, null, null, null, false, false, false, false);
            LSAppLog.e(TAG, "httpapiCheckin :: checking success");
        } catch (Exception e) {
            LSAppLog.e(TAG, "httpapiCheckin :: checkin exception " + e.toString());
            //mFSApi.clearAllCredentials();
        }
    }

    public List<Venue> listNearByVenues(String lat, String lgt) {
        //String lat = Double.toString(mLSMan.mCurLat);  // "42.288605";
        //String lgt = Double.toString(mLSMan.mCurLgt);  // "-88.000716"
    	LSAppLog.d(TAG, "listNearByVenues: FSQ: " + lat + ":" + lgt);
        VenueParser v = new VenueParser(mFSApi, lat, lgt);  // create a new venue parser for this lat/lgt
        v.getVenues();
        for (Venue e : v.mVenues) {
            LSAppLog.d(TAG, e.toString());
        }
        return Collections.synchronizedList(v.mVenues);
    }
}
