package com.locationstream.foursquare;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;

import com.locationstream.AppPreferences;
import com.locationstream.LocationSensorApp;
import com.locationstream.R;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.foursquare.VenueParser.Venue;


//public class FoursquareMainActivity extends TabActivity {
public class FoursquareMainActivity extends Activity {
    public static final String TAG = "API_FSQACT";
    
    // client_id and secret is locationstream app registered to 4sq.
    private static final String mOAuthEP = "https://foursquare.com/oauth2/authenticate?client_id=TI05WPK23W2P3YZGGU1YCJMAGJSSU1PAJQH4ZE3TDSN4221V&response_type=token&redirect_uri=location.no.de/oauth";
    
    private TabHost mTabHost;
    private ArrayAdapter<String> mAdapter;
    private FoursquareHttpApi mFSApi;
    private VenuesBroadcastReceiver mVenueReceiver;
    
    private LinearLayout mContent;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        
        mFSApi = ((LocationSensorApp)getApplication()).getCheckin().getFoursquare();
        // register the new loc bcast listener.
        mVenueReceiver = new VenuesBroadcastReceiver();
        mVenueReceiver.register();

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        //setContentView(R.layout.foursquare_main);
        //setContentView(R.layout.foursquare_venuelist);
        //initTabHost();  // need TabHost, TabWidget, FrameLayout

        /*
        // Don't start the main activity if we don't have credentials
        if (mFSApi.hasCredentials() == false) {
        	redirectToLoginActivity();  // async task from login there will call startActivity to start this one again.
        }
        LSAppLog.d(TAG, "FoursquareMainActivity :: onCreate with credential ready");
        
        fetchDataAndUpdateViews();
        */
        setupWebView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mVenueReceiver);
    }
    
    void populateListAdapter(List<String> lname){
    	setContentView(R.layout.foursquare_venuelist);
    	
    	mAdapter = new ArrayAdapter<String>(this, R.layout.venue_row, R.id.venue_row, lname);
    	LSAppLog.d(TAG, "populateListAdapter:");
    	
    	ListView listview = (ListView) findViewById(R.id.venue_listview);
        listview.setVerticalScrollBarEnabled(true);
        listview.setOnCreateContextMenuListener(this);
        listview.setVisibility(View.VISIBLE);
        listview.setAdapter(mAdapter);
    }
    
    
    private void setupWebView() {
    	mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        
        mWebView = new WebView(this);
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.getSettings().setJavaScriptEnabled(true);
        //mWebView.setLayoutParams(FILL);
   
        mWebView.setWebViewClient(new WebViewClient() {
        	// If authentication works, we'll get redirected to a url with a pattern like:  
            //    http://YOUR_REGISTERED_REDIRECT_URI/#access_token=ACCESS_TOKEN
            // Watch every page load request by override onPageStarted() in the web client and grab the token out.
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
            	LSAppLog.d(TAG, "setWebViewClient: onPageStarted:" + url);
                String fragment = "#access_token=";
                int start = url.indexOf(fragment);
                if (start > -1) {
                    // You can use the accessToken for api calls now.
                    String accessToken = url.substring(start + fragment.length(), url.length());
                    LSAppLog.d(TAG, "OAuth complete, setting access token: [" + accessToken + "].");
                    mFSApi.setOAuthToken(accessToken);
                    // launch an async task here
                    executeFetchTask("4b98fc95f964a520785a35e3");
                }
            }
        });
        mContent.addView(mWebView);
        setContentView(mContent);  // remember to set content view.
        
        mWebView.loadUrl(mOAuthEP);
    }
    
    void executeFetchTask(String endpoint) {
		//new APITask().execute("4b98fc95f964a520785a35e3");
		new APITask().execute("42.288,-88.000");
	}
	
    /**
     * all modern api returns json string. Old APIs return xml. Parse Json string or XML result.  
     */
	private class APITask extends AsyncTask<String, Integer, String> {
	     protected String doInBackground(String... endpoint) {
	    	 // VenueParser parser = new VenueParser();
	    	 //String json = mFSApi.tips(endpoint[0]);
	    	 String json = mFSApi.venues(endpoint[0]);
	    	 LSAppLog.d(TAG, "APITask result:" + json);
	    	 return json;
	     }

	     protected void onProgressUpdate(Integer... progress) {
	         //setProgressPercent(progress[0]);
	    	 LSAppLog.d(TAG, "onProgressUpdate:" + progress);
	     }

	     protected void onPostExecute(String json) {
	    	 LSAppLog.d(TAG, "onPostExecute: API call done");
	    	 populateListAdapter(processJSONResult(json));
	     }
	};
	
	List<String> processJSONResult(String json){
		List<String> lname = new ArrayList<String>();
		try{
			JSONObject resp = new JSONObject(json);
			//JSONArray items = resp.getJSONObject("response").getJSONObject("tips").getJSONArray("items");
			JSONArray items = resp.getJSONObject("response").getJSONArray("venues");
			for (int i=0;i<items.length();i++){
				JSONObject item = items.getJSONObject(i);
				String cat = (String)item.getJSONArray("categories").getJSONObject(0).get("name");
				//lname.add((String)item.get("text"));
				lname.add((String)item.get("name") + " : " + cat);
			}
		}catch(Exception e){
			LSAppLog.e(TAG, "JSONObject:" + e.toString());
		}
		return lname;
	}

	
    public void fetchDataAndUpdateViews() {
    	LocationSensorApp mLSApp = (LocationSensorApp)getApplication();
   
    	LSAppLog.d(TAG, "updateViews: get venu list after logging...");
    	
    	List<Venue> venuelist = mLSApp.getCheckin().listNearByVenues(Double.toString(mLSApp.getLSMan().getCurLocLat()), Double.toString(mLSApp.getLSMan().getCurLocLng()));
    	List<String> venuename = new ArrayList<String>();
    	
    	for(Venue v : venuelist){
    		LSAppLog.d(TAG, v.toString());
    		venuename.add(v.mName);
    	}
    	
    	populateListAdapter(venuename);
    }
    
    // register bcast recv for venue list update in order to show it on UI.
    private class VenuesBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	LSAppLog.e(TAG, "VenuesBroadcastReceiver::" + intent.getAction().toString());
            if (LocationSensorApp.LS_ACTION_NEW_LOCATION.equals(intent.getAction())) {
                fetchDataAndUpdateViews();
            }
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(LocationSensorApp.LS_ACTION_NEW_LOCATION);
            registerReceiver(this, intentFilter);
        }
    }

    
    private void initTabHost() {
        if (mTabHost != null) {
            throw new IllegalStateException("Trying to intialize already initializd TabHost");
        }

        //mTabHost = getTabHost();
        
        // We may want to show the friends tab first, or the places tab first, depending on
        // the user preferences.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
 
        // We can add more tabs here eventually, but if "Friends" isn't the startup tab, then
        // we are left with "Places" being the startup tab instead.
        String[] startupTabValues = getResources().getStringArray(R.array.startup_tabs_values);
        String startupTab = settings.getString(AppPreferences.PREFERENCE_STARTUP_TAB, startupTabValues[0]);
        //Intent intent = new Intent(this, NearbyVenuesActivity.class);
        /****
        if (startupTab.equals(startupTabValues[0])) {
            TabsUtil.addNativeLookingTab(this, mTabHost, "t1", getString(R.string.checkins_label), 
                    R.drawable.friends_tab, new Intent(this, FriendsActivity.class));
            TabsUtil.addNativeLookingTab(this, mTabHost, "t2", getString(R.string.nearby_label), 
                    R.drawable.places_tab, intent);
        } else {
            intent.putExtra(NearbyVenuesActivity.INTENT_EXTRA_STARTUP_GEOLOC_DELAY, 4000L);
            TabsUtil.addNativeLookingTab(this, mTabHost, "t1", getString(R.string.nearby_label), 
                    R.drawable.places_tab, intent);
            TabsUtil.addNativeLookingTab(this, mTabHost, "t2", getString(R.string.checkins_label), 
                    R.drawable.friends_tab, new Intent(this, FriendsActivity.class));
        } 
        
        // 'Me' tab, just shows our own info. At this point we should have a
        // stored user id, and a user gender to control the image which is
        // displayed on the tab.
        String userId = ((Foursquared) getApplication()).getUserId();
        String userGender = ((Foursquared) getApplication()).getUserGender();
        
        Intent intentTabMe = new Intent(this, UserDetailsActivity.class);
        intentTabMe.putExtra(UserDetailsActivity.EXTRA_USER_ID, userId == null ? "unknown"
                : userId);
        TabsUtil.addNativeLookingTab(this, mTabHost, "t3", getString(R.string.main_activity_tab_title_me), 
                UserUtils.getDrawableForMeTabByGender(userGender), intentTabMe);
        **/
        
        mTabHost.setCurrentTab(0);
    }
    
    private void redirectToLoginActivity() {
    	LSAppLog.e(TAG, "redirectToLoginActivity");
        setVisible(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY | 
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | 
            Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();    // with finish cur window.
    }
    
}
