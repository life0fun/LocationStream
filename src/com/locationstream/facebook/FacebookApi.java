package com.locationstream.facebook;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;
import com.locationstream.LocationSensorApp;
import com.locationstream.WebServiceApi;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.facebook.SessionEvents.AuthListener;
import com.locationstream.facebook.SessionEvents.LogoutListener;

// with all the api url and parsers
public class FacebookApi implements WebServiceApi {
	public static String TAG = "API_FBAPI";
	private static String PREFERENCE_FB_LOGIN = "FBLogin";
	private static String PREFERENCE_FB_PASSWORD = "FBPasswd";
	
	// Your Facebook Application ID must be set before running this example
    // See http://www.facebook.com/developers/createapp.php
    public static final String APP_ID = "132928800072721";   // my blur app
    
    private LocationSensorApp	mLSApp;
    public Facebook mFacebook;
    public AsyncFacebookRunner mAsyncRunner;
    public String mText;   // current status
    
    /**
     * you need to complete this permission to be able to do full list of graph accesses.
     */
    public static final String[] PERMISSIONS =
        new String[] {"publish_stream", "read_stream", "offline_access", "user_likes"};   // publish_stream" extended permission for writing
  
    public FacebookApi(LocationSensorApp lsapp) {
    	mLSApp = lsapp;
     	mFacebook = new Facebook();
        mAsyncRunner = new AsyncFacebookRunner(mFacebook);
        
        SessionStore.restore(mFacebook, mLSApp);
        SessionEvents.addAuthListener(new LSFBAuthListener());
        SessionEvents.addLogoutListener(new LSFBLogoutListener());
    }

    public boolean hasValidSession() {
    	return mFacebook.isSessionValid();
    }
    
    public boolean loginUser(String login, String password, Editor editor) {
    	editor.putString(PREFERENCE_FB_LOGIN, login);
		editor.putString(PREFERENCE_FB_PASSWORD, password);
		if (!editor.commit()) {
			return false;
		}
	    return true;
	}

	public boolean logoutUser(String login, String password, Editor editor) {
		return editor.clear().commit();
	}
	
	public void refreshMe() {
		//mAsyncRunner.request("me", new LSFBRequestListener());
		//mAsyncRunner.request("me/feed", new LSFBRequestListener());
		mAsyncRunner.request("me/likes", new LSFBRequestListener());
	}
	
    public void updateStatus(final String message) {
    	// publish_stream" extended permission in order to write to the feed.
    	// $facebook->api( '/me/feed/', access_token, msg)
    	// /PROFILE_ID/feed arguments { message, picture, link, name, caption, description, source}
        Bundle params = new Bundle();
        params.putString("message", message);
        mAsyncRunner.request("me/feed", params, "POST", new WallPostRequestListener()); //new AsyncRequestListener() {
    }

    public void postDialog(Context activity) {
    	mFacebook.dialog(activity, "stream.publish", new LSFBDialogListener());
    }
    
    public int uploadImage() {
    	// /PROFILE_ID/feed arguments { message, picture, link, name, caption, description, source}
        Bundle params = new Bundle();
        params.putString("method", "photos.upload");

        URL uploadFileUrl = null;
        try {
            uploadFileUrl = new URL(
                "http://www.facebook.com/images/devsite/iphone_connect_btn.jpg");
        } catch (MalformedURLException e) {
        	e.printStackTrace();
        }
        try {
            HttpURLConnection conn= (HttpURLConnection)uploadFileUrl.openConnection();
            conn.setDoInput(true);
            conn.connect();
            int length = conn.getContentLength();
        	
            byte[] imgData =new byte[length];
            InputStream is = conn.getInputStream();
            is.read(imgData);
            params.putByteArray("picture", imgData); 
       
        } catch  (IOException e) {
            e.printStackTrace();                	
        }
                 	
        mAsyncRunner.request(null, params, "POST", new LSFBUploadListener());
        return 0;
    }
    
    public class LSFBAuthListener implements AuthListener {
        public void onAuthSucceed() {
            mText = "You have logged in! Enjoy your location published to your Facebook Wall !!!";
            //mLSApp.sendBroadcast(new Intent(LocationSensorApp.LS_ACTION_LOGIN_FB));
        }

        public void onAuthFail(String error) {
            mText = "Login Failed: " + error;
        }
    }
    
    public class LSFBLogoutListener implements LogoutListener {
        public void onLogoutBegin() {
            mText = "Logging out...";
        }
        
        public void onLogoutFinish() {
            mText = "You have logged out! ";
        }
    }
    
    public class LSFBRequestListener extends BaseRequestListener {
        public void onComplete(final String response) {
            try {
                // process the response here: executed in background thread
                LSAppLog.d(TAG, "Graph: " + response);
                JSONObject json = Util.parseJson(response); // refer to Util.java
                final String name = json.getString("name");
                mText = ("Hello there, " + name + "!");
            } catch (JSONException e) {
                LSAppLog.e(TAG, "JSON Exception:" + e.toString());
            } catch (FacebookError e) {
            	LSAppLog.e(TAG, "FacebookError: " + e.getMessage());
            }
        }
    }
 
    public class LSFBUploadListener extends BaseRequestListener 
    {
        public void onComplete(final String response) {
            try {
                // process the response here: (executed in background thread)
                LSAppLog.e(TAG, "Response: " + response.toString());
                JSONObject json = Util.parseJson(response);
                final String src = json.getString("src");
                mText = ("Hello there, photo has been uploaded at \n" + src);
            } catch (JSONException e) {
            	LSAppLog.e(TAG,  "JSON Error in response");
            } catch (FacebookError e) {
            	LSAppLog.e(TAG,  "Facebook Error: " + e.getMessage());
            }
        }
    }
    
    public class WallPostRequestListener extends BaseRequestListener 
    {
        public void onComplete(final String response) {
            LSAppLog.e(TAG, "Response: " + response.toString());
            String message = "<empty>";
            try {
                JSONObject json = Util.parseJson(response);
                message = json.getString("message");
            } catch (JSONException e) {
            	LSAppLog.e(TAG, "JSON Error in response");
            } catch (FacebookError e) {
            	LSAppLog.e(TAG, "Facebook Error: " + e.getMessage());
            }
            final String text = "Your Wall Post: " + message;
            mText = text;
        }
    }
    
    public class WallPostDeleteListener extends BaseRequestListener {
        public void onComplete(final String response) {
            if (response.equals("true")) {
            	LSAppLog.e(TAG, "Response: Successfully deleted wall post " + response.toString());
            	mText = ("Deleted Wall Post");
            } else {
            	LSAppLog.e(TAG, "Could not delete wall post");
            }
        }
    }
    
    public class LSFBDialogListener extends BaseDialogListener 
    {
        public void onComplete(Bundle values) {
            final String postId = values.getString("post_id");
            if (postId != null) {
                LSAppLog.e(TAG, "Dialog Success! post_id=" + postId);
                mAsyncRunner.request(postId, new WallPostRequestListener());
            } else {
            	LSAppLog.e(TAG, "No wall post made");
            }
        }
    }
}
