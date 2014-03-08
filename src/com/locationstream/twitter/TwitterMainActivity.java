package com.locationstream.twitter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TabHost;

import com.locationstream.LocationSensorApp;
import com.locationstream.R;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.foursquare.LoginActivity;
import com.locationstream.twitter.TwitterApi.DialogListener;


//public class FoursquareMainActivity extends TabActivity {
public class TwitterMainActivity extends Activity implements DialogListener {
    public static final String TAG = "LSAPP_TWT";
    
    
    
    private TabHost mTabHost;
    private ArrayAdapter<String> mAdapter;
    private TwitterApi mTWApi;
    private Button mLoginBtn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        
        mTWApi = ((LocationSensorApp)getApplication()).getCheckin().getTwitter();

        // Don't start the main activity if we don't have credentials
        if (mTWApi.isSessionValid() == false) {
        	//redirectToLoginActivity();
        	mTWApi.authorize(this, null, this);  //start the tw oauth dialog.
        	LSAppLog.e(TAG, "TwitterMainActivity :: deprecated by using twitter4j library!");
        }else{
        	LSAppLog.e(TAG, "TwitterMainActivity :: onCreate with credential ready");
        	//finish();
        }
        
        //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        //setContentView(R.layout.tw_main);
        //initViews();  // need TabHost, TabWidget, FrameLayout
        
        //((LocationSensorApp)getApplication()).getCheckin().httpapiCheckin();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
       
    public void initViews(){
    	mLoginBtn = (Button)findViewById(R.id.main_loin_button);
    	final EditText user = (EditText)findViewById(R.id.main_username_edit_text);
    	final EditText pass = (EditText)findViewById(R.id.main_password_edit_text);
    	mLoginBtn.setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) {
                 loginTwitter(user.getText().toString(), pass.getText().toString());
             }
         });
    }
    
    public void loginTwitter(String user, String pass){
    	//OAuthSignpostClient oauthClient = new OAuthSignpostClient("ZzVuziGRLPmU5IewoKPw", "2lrv6JfTkJ96ma7e9988rihw3CV7VeYf1eE623aRM", "oob");
    	//oauthClient.authorizeDesktop();
    	//oauthClient.authorizeUrl();
    	//String v = oauthClient.askUser("Please enter the verification PIN from Twitter");
    	//oauthClient.setAuthorizationCode(v);
    	//String[] accessToken = oauthClient.getAccessToken();
    	//winterwell.jtwitter.Twitter mytwitter = new winterwell.jtwitter.Twitter(user, oauthClient);
    	//LSAppLog.i(TAG, "Tweets:" + mytwitter.getStatus());
    }
    
    
	public void onCancel() {
		LSAppLog.i(TAG, "TwitterMainActivity :: onCancel of authentication..");
	}

	public void onComplete(Bundle values) {
		LSAppLog.i(TAG, "TwitterMainActivity :: onComplete with credential ready");
	}

	public void onError(DialogError e) {
		LSAppLog.i(TAG, "TwitterMainActivity :: onError of authentication.."+e.toString());
	}

	public void onTwitterError(TwitterError e) {
		LSAppLog.i(TAG, "TwitterMainActivity :: onTwitterError of authentication.."+e.toString());
	}
}
