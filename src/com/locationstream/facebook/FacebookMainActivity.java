package com.locationstream.facebook;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.facebook.android.DialogError;
import com.facebook.android.FacebookError;
import com.facebook.android.Facebook.DialogListener;
import com.locationstream.LocationSensorApp;
import com.locationstream.R;
import com.locationstream.LocationSensorApp.LSAppLog;

public class FacebookMainActivity extends Activity {
	
	public static final String TAG = "API_FBACT";
    
    private LoginButton mLoginButton;
    private TextView mText;
    private Button mPostButton;
    //private Button mRequestButton;
    //private Button mDeleteButton;
    //private Button mUploadButton;
    
    private LocationSensorApp mLSApp; 
    private FacebookApi mFB; 
    private AuthBroadcastReceiver mReceiver;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mLSApp = (LocationSensorApp)getApplication();
        mFB = mLSApp.getCheckin().getFacebook();
        
        mReceiver = new AuthBroadcastReceiver();
        mReceiver.register();
        
        initViews();
        
        if(mFB.hasValidSession()){
        	//mFB.updateStatus("fighting against my stupidity by updating status!!!");
        }
        
        mFB.refreshMe();
        /*
        mFB.mFacebook.dialog(this, "feed", new DialogListener() {
            //@Override
            public void onComplete(Bundle values) {
            	LSAppLog.d(TAG, values.getString("message"));
            }

            //@Override
            public void onFacebookError(FacebookError error) {}

            //@Override
            public void onError(DialogError e) {}

            //@Override
            public void onCancel() {}
       });
       */
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
    
    public void initViews() {
    	 setContentView(R.layout.fb_main);
         
    	 mText = (TextView) FacebookMainActivity.this.findViewById(R.id.fb_txt);
         
         mLoginButton = (LoginButton) findViewById(R.id.fb_login);
         mLoginButton.init(mFB.mFacebook, mFB.PERMISSIONS);
         
         mPostButton = (Button) findViewById(R.id.fb_postbutton);
         mPostButton.setOnClickListener(new OnClickListener() {
             public void onClick(View v) {
                 //mFB.mFacebook.dialog(FacebookMainActivity.this, "stream.publish", new FacebookApi.LSFBDialogListener());
            	 mFB.postDialog(FacebookMainActivity.this);
             }
         });
         //mUploadButton = (Button) findViewById(R.id.fb_uploadbutton);
         
         if(mFB.hasValidSession()){
        	 mText.setText("You have successfully logged in...Enjoy your location posted to your facebook wall  !!!");
         }
     }
    
     private class AuthBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	LSAppLog.e(TAG, "AuthBroadcastReceiver::" + intent.getAction().toString());
            if (LocationSensorApp.LS_ACTION_LOGIN_FB.equals(intent.getAction())) {
                mText.setText("You have successfully logged in...Enjoy your location posted to your facebook wall  !!!");
            }
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(LocationSensorApp.LS_ACTION_LOGIN_FB);
            registerReceiver(this, intentFilter);
        }
    }
}
