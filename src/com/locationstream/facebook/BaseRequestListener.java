package com.locationstream.facebook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import com.facebook.android.FacebookError;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.locationstream.LocationSensorApp.LSAppLog;

/**
 * Skeleton base class for RequestListeners, providing default error 
 * handling. Applications should handle these error conditions.
 *
 */
public abstract class BaseRequestListener implements RequestListener {

    public void onFacebookError(FacebookError e) {
    	LSAppLog.e("Facebook", e.getMessage());
        e.printStackTrace();
    }

    public void onFileNotFoundException(FileNotFoundException e) {
    	LSAppLog.e("Facebook", e.getMessage());
        e.printStackTrace();
    }

    public void onIOException(IOException e) {
    	LSAppLog.e("Facebook", e.getMessage());
        e.printStackTrace();
    }

    public void onMalformedURLException(MalformedURLException e) {
    	LSAppLog.e("Facebook", e.getMessage());
        e.printStackTrace();
    }
    
}
