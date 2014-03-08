package com.locationstream.twitter;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.PropertyConfiguration;
import twitter4j.http.AccessToken;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.CookieSyncManager;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.http.HttpApi;
import com.locationstream.http.HttpBase;
import com.locationstream.http.OAuthHttp;

public class TwitterApi {
	public static final String TAG = "LSAPP_TWT";
		
	// access token given by user(locstream:locstream1) to app(locstream), hardcode here by intercepting http request.
	//private static String mAccessToken = "232237376-6LMT6CwZC69J4Tmxy2R7TVgUwjTaCPLUcEFYiwKZ";
	//private static String mSecretToken = "ftph98OrKEqjueLLTFQmKCLoOfQUlNgJUB9g4JNdDI";
	
	// api twitter endpoint
	protected static String API_ENDPOINT = "https://api.twitter.com/1/";
	protected static String OAUTH_REQUEST_TOKEN = "https://api.twitter.com/oauth/request_token";  // post
    protected static String OAUTH_ACCESS_TOKEN = "https://api.twitter.com/oauth/access_token";    // post
    protected static String OAUTH_AUTHORIZE = "https://api.twitter.com/oauth/authorize";          // get
    public static final String ACCESS_TOKEN = "access_token";
    public static final String SECRET_TOKEN = "secret_token";
    public static final String CALLBACK_URI = "twitter://callback";
    public static final String CANCEL_URI = "twitter://cancel";
    

    // locstream+1 app: http://dev.twitter.com/apps/615204
    // you can use single user case to use hard-coded token and secret to sign all your http requests.
    //private static final String mConsumerKey="ZzVuziGRLPmU5IewoKPw";
    //private static final String mConsumerSecret="2lrv6JfTkJ96ma7e9988rihw3CV7VeYf1eE623aRM";
    private static String mAccessToken="3743661-FCOFzTV87sTw8MvnVGeePNgcHo6dfWi2xasf4BDE";
    private static String mAccessTokenSecret = "M8BYreceg5vgWsoMxw1Pa7sK0CmtA5A8FexDc";
    private static final String mConsumerKey= "USLG8ce1GgCOTzmxODYyg";
    private static final String mConsumerSecret="Wk3XdtBDyGRLq5dz20NMasrzQ4BfeRvbPSQ1iKaI";
    

    private final DefaultHttpClient mHttpClient = HttpBase.createHttpClient();
    private HttpApi mHttpApi;   // ref to OAuthHttp, access
    
	private int mIcon;
	private CommonsHttpOAuthConsumer mHttpOauthConsumer;
	private CommonsHttpOAuthProvider mHttpOauthProvider;   // encapsulated api oauth endpoints
	
	private static Twitter _twitter = null;  // twitter4j instance
	
	public TwitterApi(int icon) {
		mIcon = icon;
		initTwitter();
		mAccessToken = null;
		mAccessTokenSecret = null;
	}

	public Twitter initTwitter() {
		if(_twitter == null){
			String filename = "/data/data/com.locationstream/databases/twitter4j.properties";
			FileInputStream is = null;
			try{
				is = new FileInputStream(filename);
			}catch(Exception e){
				LSAppLog.i(TAG, "initTwitter twitter4j.properties exception:" + e.toString());
			}
			
			if(is == null){
				return null;
			}
			LSAppLog.i(TAG, "initTwitter with twitter4j.properties");
			PropertyConfiguration conf = new PropertyConfiguration(is);
			try{
				_twitter = new TwitterFactory(conf).getInstance(new AccessToken(mAccessToken, mAccessTokenSecret));
				LSAppLog.i(TAG, "getInstance constructor:" + _twitter);
			}catch(Exception e){
				LSAppLog.i(TAG, "getInstance constructor exception:" + e.toString());
			}
		}
		
		// get the oAuth http
		mHttpApi = new OAuthHttp(mHttpClient, mConsumerKey, mConsumerSecret,1);  // version 1
		return _twitter;
	}
		
	
	
	// calling this func will create tw dialog and finish authorize and store the access tokens. You can use the access token for 
	public static interface DialogListener {
		public void onComplete(Bundle values);
		public void onTwitterError(TwitterError e);
		public void onError(DialogError e);
		public void onCancel();
	}
	
	/**
	 * TW OAuth dance Facade, will create tw dialog, request token, and intercept access token in redirect url from server.
	 */
	public void authorize(Context ctx,
			Handler handler,
			//String consumerKey,
			//String consumerSecret,
			final DialogListener listener) {
				mHttpOauthConsumer = new CommonsHttpOAuthConsumer(mConsumerKey, mConsumerSecret);
				mHttpOauthProvider = new CommonsHttpOAuthProvider(OAUTH_REQUEST_TOKEN, OAUTH_ACCESS_TOKEN, OAUTH_AUTHORIZE);
				CookieSyncManager.createInstance(ctx);
				
				// now create the dialog to get to  
				dialog(ctx, handler, new DialogListener() {

			//@Override
			public void onComplete(Bundle values) {
				CookieSyncManager.getInstance().sync();
				setAccessToken(values.getString(ACCESS_TOKEN));
				setSecretToken(values.getString(SECRET_TOKEN));
				if (isSessionValid()) {
					Log.d(TAG, "token "+getAccessToken()+" "+getSecretToken());
					((OAuthHttp)mHttpApi).setOAuthTokenWithSecret(mAccessToken, mAccessTokenSecret);
					listener.onComplete(values);  // propagate to the caller of authorize function.
				} else {
					onTwitterError(new TwitterError("failed to receive oauth token"));
				}
			}

			//@Override
			public void onTwitterError(TwitterError e) {
				Log.d(TAG, "Login failed: "+e);
				listener.onTwitterError(e);
			}

			//@Override
			public void onError(DialogError e) {
				Log.d(TAG, "Login failed: "+e);
				listener.onError(e);
			}

			//@Override
			public void onCancel() {
				Log.d(TAG, "Login cancelled");
				listener.onCancel();
			}
			
		});
	}
	
	public String logout(Context context) throws MalformedURLException, IOException {
		return "true";
	}
	
	// this one create the twitter dialog that request tokens from api endpoints.
	public void dialog(final Context ctx,
			Handler handler,
			final DialogListener listener) {
		if (ctx.checkCallingOrSelfPermission(Manifest.permission.INTERNET) !=
			PackageManager.PERMISSION_GRANTED) {
			Util.showAlert(ctx, "Error", "Application requires permission to access the Internet");
			return;
		}
		new TwDialog(ctx, mHttpOauthProvider, mHttpOauthConsumer,
				listener, mIcon).show();
	}
	
	public boolean isSessionValid() {
		return getAccessToken() != null && getSecretToken() != null;
	}
	
	public String getAccessToken() {
		return mAccessToken;
	}

	public void setAccessToken(String accessToken) {
		mAccessToken = accessToken;
	}

	public String getSecretToken() {
		return mAccessTokenSecret;
	}

	public void setSecretToken(String secretToken) {
		mAccessTokenSecret = secretToken;
	}

	
	/**
	 * -----------------------------------------------
	 * use OAuth http(signed) to access api resources
	 * -----------------------------------------------
	 */
	public String updateStatus(String status){
		if(_twitter != null){
			try{
				LSAppLog.i(TAG, "updateStatus :"+ status);
				//_twitter.updateStatus(status);
			}catch(Exception e){
				LSAppLog.i(TAG, "updateStatus exception:" + e.toString());
			}
		}
		
		String json = null;
		try {
	    	LSAppLog.d(TAG, "API tips:" + mAccessToken);
	        HttpGet httpGet = mHttpApi.createHttpGet(API_ENDPOINT + "statuses/user_timeline.json",  // ? will be appeneded
	        		new BasicNameValuePair("count", "20"),
	                new BasicNameValuePair("include_rts", "true"));
	        json = mHttpApi.handleHttpRequest(httpGet);  // json result.
    	}catch(Exception e){
    		LSAppLog.e(TAG, "FSAPI exception:" + e.toString());
    	}
    	return json; 
	}
}
