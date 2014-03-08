package com.locationstream;
import static com.locationstream.Constants.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.facebook.FacebookMainActivity;
import com.locationstream.foursquare.FoursquareMainActivity;
import com.locationstream.twitter.TwitterMainActivity;

/**
 *<code><pre>
 * CLASS:
 *  The Main UI of the App
 *  however this app will not have a UI in production. This is just for testing, right Haijin?????
 *
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
public class LocationSensorUI extends Activity {

    public final static String TAG = "LSAPP_UI";

    public static final int MENUITEM_STATUS = 100;
    public static final int MENUITEM_MOCKLOCATION = 101;
    public static final int MENUITEM_DISPLAY_GRAPH = 102;
    public static final int MENUITEM_DETECTION_RADIUS = 103;
    public static final int MENUITEM_UNITTEST = 104;
    public static final int MENUITEM_WIFI = 105;
    public static final int MENUITEM_OPTIN = 106;

    private LocationSensorApp mLSApp;	// reference to app
    private TestCases mTest;

    private Button mFBButton;
    private Button mFSButton;
    private Button mTWButton;
    private Button mMyLocButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLSApp = (LocationSensorApp)getApplication();
        mTest = new TestCases(mLSApp);

        setContentView(R.layout.main);

        mFBButton = (Button)findViewById(R.id.facebook);
        mFSButton = (Button)findViewById(R.id.foursquare);
        mTWButton = (Button)findViewById(R.id.twitter);
        mMyLocButton = (Button)findViewById(R.id.latitude);

        mFBButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                lauchCheckinApp("Facebook");
            }
        });
        mFSButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                lauchCheckinApp("Foursquare");
            }
        });
	    mTWButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                lauchCheckinApp("Twitter");
            }
        });
        mMyLocButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                postMyLocFix();
            }
        });

        // start ls man
        if (mLSApp != null)
            mLSApp.startLocationManager();   // start lsman only after we obtain credential
    }

    public void lauchCheckinApp(String app) {
        Intent appintent = null;
        if("Foursquare".equals(app)) {
            appintent = new Intent(this, FoursquareMainActivity.class);
        }else if("Facebook".equals(app)) {
            appintent = new Intent(this, FacebookMainActivity.class);
        }else if("Twitter".equals(app)){
        	appintent = new Intent(this, TwitterMainActivity.class);
        }
	startActivityForResult(appintent, 1234);  // request code = 0;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LSAppLog.i(TAG, "onActivityResult and starting Loc Man");
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, MENUITEM_STATUS, 0, "Current Location POI Status");
        //menu.add(Menu.NONE, MENUITEM_MOCKLOCATION, 0, "mock a location Geo fix");
        //menu.add(Menu.NONE, MENUITEM_DETECTION_RADIUS, 0, "detection radius status");
        menu.add(Menu.NONE, MENUITEM_DISPLAY_GRAPH, 0, "Display Location Graph");
        menu.add(Menu.NONE, MENUITEM_WIFI, 0, "Wifi hotspot mode");
        menu.add(Menu.NONE, MENUITEM_OPTIN, 0, "toggle opted in");
        menu.add(Menu.NONE, MENUITEM_UNITTEST, 0, "run unit test online");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
        case MENUITEM_STATUS:
            StringBuilder sb = new StringBuilder();
            // {"Lac":"7824","CntryISO":"us","NetTyp":"GSM","NetOp":"310260","Cid":"15415"}
            // Pattern p = Pattern.compile("{\"Lac\":");

            sb.append("Fast Detection on = " + mLSApp.getLSMan().mDetection.isDetectionMonitoringON());
            sb.append("\n");
            if (mLSApp.getLSMan().mDetection.mCurPoiTuple != null) {
                sb.append("Current POI=" + mLSApp.getLSMan().mDetection.mCurPoiTuple.getPoiName() + " :: Next POI = ");
                if (mLSApp.getLSMan().mDetection.mNextPoiTuple != null) {
                    sb.append(mLSApp.getLSMan().mDetection.mNextPoiTuple.getPoiName() + "..\n");
                } else {
                    sb.append("null...not moving to any POI");
                }
                //String curpoistr = mLSApp.getLSMan().mDetection.mCurPoiTuple.toString();
                //sb.append("Cur POI :" + curpoistr + "\n");
            } else {
                sb.append("Cur POI : Not in any POI now..:: Next POI =");
                if (mLSApp.getLSMan().mDetection.mNextPoiTuple != null) {
                    sb.append(mLSApp.getLSMan().mDetection.mNextPoiTuple.getPoiName() + "..\n");
                } else {
                    sb.append("null...not moving to any POI");
                }
            }
            //String bouncingcells = mLSApp.getLSMan().mTelMon.getBouncingCells().toString();
            //sb.append("Bouncing cells:" + bouncingcells);
            //LSAppLog.i(TAG, "LocStatus: " + sb.toString());
            LSAppLog.dbg(this, DEBUG_INTERNAL, sb.toString());
            Toast.makeText(this, sb.toString(), 1).show();
            String poiuri = "geo:"+mLSApp.getLSMan().mDetection.mCurPoiTuple.getLat()+","+mLSApp.getLSMan().mDetection.mCurPoiTuple.getLgt();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(poiuri)));
            break;

        case MENUITEM_DETECTION_RADIUS:
            String radius = "Detection Radius: entering=(2 *" + TARGET_RADIUS + " ) Leaving=Max(measurement_accuracy, "+ TARGET_RADIUS + ")\n";
            radius += "You can set radius through system property debug.mot.locationsensor.radius";
            Toast.makeText(this, radius, 5).show();
            break;

        case MENUITEM_DISPLAY_GRAPH:
            //mLSApp.mGraph.buildGraph();
            break;

        case MENUITEM_MOCKLOCATION:
            Toast.makeText(this, "Moc Location...", 2).show();
            break;

        case MENUITEM_WIFI:
            testWifiConnectivity();
            break;

        case MENUITEM_OPTIN:
            int optin = toggleOptIn();
            Toast.makeText(this, "User Opt In state is: " + optin, 3).show();
            break;

        case MENUITEM_UNITTEST:
            //mTest.testCheckinData();
            //Toast.makeText(this, mTest.testDataConnection(), 5).show();
            //mTest.testDBOverflow();
            mTest.main();
            break;
        }

        return true;
    }

    void testWifiConnectivity() {
        WifiManager mWifiMan = (WifiManager)mLSApp.getSystemService(Context.WIFI_SERVICE);
        
        ConnectivityManager connman = (ConnectivityManager)mLSApp.getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netinfo = connman.getActiveNetworkInfo();
        if (netinfo.getType() == ConnectivityManager.TYPE_MOBILE_DUN) {
            Toast.makeText(this, "Mobile HotSppt....", 1).show();
            LSAppLog.d(TAG, "Wifi Hotspot running...");
        }
        LSAppLog.d(TAG, "Wifi connectivity status:" + netinfo.getType());
        LSAppLog.d(TAG, "DeviceId:" + mLSApp.getLSMan().getPhoneDeviceId());
    }

    int toggleOptIn() {
        int state = Settings.System.getInt(this.getContentResolver(), ADA_ACCEPTED_KEY, 0);
        if (state == 0) {
            state = 1;
        } else {
            state = 0;
        }
        Settings.System.putInt(this.getContentResolver(), ADA_ACCEPTED_KEY, state);
        LSAppLog.d(TAG, "User Opt-in status:" + state);
        return state;
    }

    void postMyLocFix() {
        String nodester = "http://location.nodester.com/fix";
        String joyent = "http://location.no.de/fix";
        String fixUrl = joyent;
        // curl -d "lat=41.884411&lng=-87.625984" http://localhost/fix
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost fixpost = new HttpPost(fixUrl);
        Location curloc = ((LocationManager)getSystemService(Context.LOCATION_SERVICE)).getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        double lat = curloc.getLatitude();
        double lng = curloc.getLongitude();
        LSAppLog.d(TAG, "postMyLocFix: latlng: " + lat + "," + lng);

        try {
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);  //
            nameValuePairs.add(new BasicNameValuePair("lat", Double.toString(lat)));
            nameValuePairs.add(new BasicNameValuePair("lng", Double.toString(lng)));
            fixpost.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8));
            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(fixpost);
            HttpEntity resEntity = response.getEntity();
            if (resEntity != null) {
                LSAppLog.d(TAG, "postMyLocFix: response: " + EntityUtils.toString(resEntity));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // start goog Map app
        String poiuri = "geo:"+lat+","+lng;
        String diruir = "http://maps.google.com/maps?saddr="+lat+","+lng+"&daddr=42.348232,-88.059487";
        final Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(diruir));
        intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
        startActivity(intent);
    }
}
