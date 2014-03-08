package com.locationstream.ws;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import com.locationstream.LocationSensorApp.LSAppLog;

/* WS download can not be easier with URLConnection 
 * what about upload ?
 */
public abstract class BaseFeedParser implements FeedParser {

	public static final String TAG = "BaseFeedParser";
	
	private final URL feedUrl;   // wrap with URLConnection

	protected BaseFeedParser(String feedUrl){
		try {
			LSAppLog.e(TAG, "BaseFeedParser feedUrl :" + feedUrl);
			this.feedUrl = new URL(feedUrl);
		} catch (MalformedURLException e) {
			LSAppLog.e(TAG, "BaseFeedParser Exception :" + feedUrl);
			throw new RuntimeException(e);
		}
	}

	protected InputStream getInputStream() {
		try {
			return feedUrl.openConnection().getInputStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}