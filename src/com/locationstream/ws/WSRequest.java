/**
 * the class is for web service.
 */
package com.locationstream.ws;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.os.Parcel;
//import android.security.MessageDigest;
import android.util.Base64;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.ws.LSWSBase.ErrorCodes;

public class WSRequest
{
	public static final String TAG = "WSRequest";
	public static final String ACTION =  "com.motorola.locationsensor.ws.request";
	public static final String ACTION_AIDL = "com.motorola.locationsensor.ws.request.aidl"; // this guy is if you want to get your response via aidl instead of intent
	public static final String KEY_REQUEST_DATA = "com.motorola.locationsensor.ws.key.requestdata";
	protected static final String DEFAULT_QUERY_STRING = "?k=android&f=pb&of=0";
	private static final List<Header> defaultHdrs = new ArrayList<Header>();
	
	/**
	 * ResponseLevel - how often you'd like to get responses back from this request.  The following
	 * levels are ordered in terms of decreasing granularity.  When set to something other than ALL 
	 * (which is the default) you'll get a response per "thing" and then a response with null data
	 * and an error of ResponseFinishedError so you'll know there's nothing left on the wire.
	 */
	public static enum ResponseLevel
	{
		CHUNK, 	// The lowest level chunk read off the wire
		ITEM,  	// A complete GPB or byte array sent from the other end
		ALL;	// The entire payload sent from the other end
	}
	
	protected byte[] mData = null;
	protected Exception mException = null;
	protected ErrorCodes mError = null;
	protected String mErrorMsg = null;
	protected byte mRetryCount = 0; // how many times we've retried this request
	protected byte mMaxRetries = 0; // max number of times we'll retry the request, negative number means infinite
	protected ResponseLevel mResponseLevel = ResponseLevel.ALL;
	protected int mOffset = 0; // in case we want to try this request again, where to start the response from
	protected String mReqKey = null;  // in case we want to try this request again, which key did we use for the response
	protected boolean mUseAuth = false; // whether to set the chunk token secret when reading the response.

	
	// wrap request into URL and URLConnection
	protected URL mURL;
	protected URLConnection mURLConn;
	protected String mURLString;
	protected UrlEncodedFormEntity mUrlArgsEntity;
	protected WSResponse mResp;
	
	// do nothing constructor
	public WSRequest(String rooturl, String user_semicol_passwd) {
		try{
			mURL = new URL(rooturl);
		}catch(MalformedURLException e){
		}
		
		// set up http header for this specific request
		String cred = Base64.encodeToString(user_semicol_passwd.getBytes(), 0);
		defaultHdrs.add(new BasicHeader("User-Agent", "Mozilla/5.0 (iPhone; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1C10 Safari/419.3"));
		//defaultHdrs.add(new BasicHeader("Content-Type", "application/x-www-form-urlencoded"));
		//defaultHdrs.add(new BasicHeader("Authorization", "Basic " + cred));
	}
	
	public void setWSResponse(WSResponse resp){ mResp = resp; }  // deprecated by createResponse
	
	public WSResponse getWSResponse() {
		return mResp;
	}
	
    protected void _writeToParcel(Parcel out, int flags) 
    {
    	if (mData != null && (mData.length > 0)) {
    		out.writeInt(mData.length);
        	out.writeByteArray(mData);
    	} else {
    		out.writeInt(0);
    	}
    	out.writeInt(_getError().toValue());
    	out.writeString(_getErrorMsg());
    	out.writeByte(mRetryCount);
    	out.writeByte(mMaxRetries);
    	out.writeString(mResponseLevel.toString());
    	out.writeInt(mOffset);
    	out.writeString(mReqKey);
    	out.writeByte((byte)(mUseAuth ? 1 : 0));
    }

    protected void _readFromParcel(Parcel in) 
    {
    	int dataLen = in.readInt();
    	if (dataLen != 0) {
    		mData = new byte[dataLen];
    		in.readByteArray(mData);
    	} else {
    		mData = null;
    	}
    	mException = null;
    	mError = ErrorCodes.fromValue(in.readInt());
    	mErrorMsg = in.readString();
    	mRetryCount = in.readByte();
    	mMaxRetries = in.readByte();
    	mResponseLevel = ResponseLevel.valueOf(in.readString());
    	mOffset = in.readInt();
    	mReqKey = in.readString();
    	mUseAuth = in.readByte() == 1;
    }
    
    protected ErrorCodes _getError() 
    {
    	if (mException != null) {
    		return ErrorCodes.E_FAIL;
    	} else {
    		return ErrorCodes.E_NONE;
    	}	
    }
    protected String _getErrorMsg() 
    {
    	if (mException != null) {
    		return mException.toString();
    	} else {
    		return null;
    	}
    }
    
    @Override
    public String toString() 
    {
    	return "WSRequest(" + getRootUrl() + ")";
    }
    
    public ResponseLevel getResponseLevel() 
    {
    	return mResponseLevel;
    }
    
    /**
     * getRootUrl - returns the root url for this request.
     * 
     * @return the root url string for this request
     */
    protected String getRootUrl() { return mURL.toString(); }
    
    /**
     * getResource() - this is the resource part of the url for this request.  If you don't have
     * one then return null (which as you can see below is the default).
     * 
     * @return null		- this request doesn't have a resource (default)
     * @return String	- the resource for this request 
     */
    protected String getResource() { return null; }
    
    // Override this guy if you want to return something different...obviously...
    public HttpEntity getEntity() 
    {
    	//ByteArrayEntity reqEntity = new ByteArrayEntity(mData);
    	//reqEntity.setContentType("application/x-www-form-urlencoded");
    	LSAppLog.e(TAG, mUrlArgsEntity.toString());
    	return mUrlArgsEntity;
    }
    
    /**
     * The normal URL pattern is:
     * 
     * 	serverBaseUrl[getRootUrl()]/accountId/deviceId/[getResource()]?k=android&l=loadBalancerValue&f=pb&p=[payloadSize]&h=[signBody]&o=[mOffset]
     * 
     * If a request subclass doesn't follow this, then they'll need to override the getUrl() function
     * and do what's appropriate for it.  The body signature is only included if there is indeed a body.
     * The getResource() is optional, if not present, then obviously it's not in the url
     * 
     */
    public String getUrl(final String serverBaseUrl, final String accountId, final String deviceId, final String loadBalancerValue) 
    { 
    	StringBuilder url = new StringBuilder(serverBaseUrl);
		url.append(getRootUrl())
		.append("/")
		.append(accountId)
		.append("/")
		.append(deviceId);
		
		String resource = getResource();
		if (resource != null) {
			url.append("/").append(resource);
		}
		
		url.append("?k=android&l=")
		.append(loadBalancerValue)
		.append("&f=pb&p=")
		.append(getBodySize());
		
		String bodySign = signBody();
		if (bodySign.length() > 0) {
			url.append("&h=").append(bodySign);
		}
		url.append("&of=").append(mOffset);
		
		if (mReqKey != null) {
			url.append("&dk=").append(mReqKey);
		}
		
		LSAppLog.e(TAG, url.toString());
		
		return url.toString();
    }
    
    public String getUrl() {
    	// get the default url with this request object
    	return mURLString;
    }
    
    public String getVenusUrl(double lat, double lgt) 
    { 
    	// "http://api.foursquare.com/v1/venues.json?geolat=42.977&geolong=-80.009"
    	StringBuilder url = new StringBuilder(mURL.toString());
    	url.append("venues.json?").append("geolat=").append(lat).append("&geolong=").append(lgt);
    	LSAppLog.e(TAG, "getVenusUrl:" + url);
    	mURLString = url.toString();
    	return mURLString;
    }
    
    public String getUsersUrl(double lat, double lgt) 
    { 
    	// "http://api.foursquare.com/v1/users.json?geolat=42.977&geolong=-80.009"
    	StringBuilder url = new StringBuilder(mURL.toString());
    	url.append("venues.json?").append("geolat=")
    	.append(lat).append("&geolong=")
    	.append(lgt);
    	LSAppLog.e(TAG, "getVenusUrl:" + url);
    	mURLString = url.toString();
    	return mURLString;
    }
    
    public String getCheckinUrl(long vid) 
    { 
    	// "http://api.foursquare.com/v1/venues.json?geolat=42.977&geolong=-80.009"
    	StringBuilder url = new StringBuilder(mURL.toString());
    	url.append("/v1/checkin"); //.append("vid=").append(vid).append("&twitter=1");
    	LSAppLog.e(TAG, "getVenusUrl:" + url);
    	
    	//List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    	List<NameValuePair> args = new ArrayList<NameValuePair>(1);
    	args.add(new BasicNameValuePair("vid", Long.toString(vid)));
    	try{
    		/* cost me a day....because the default phone encoding is unicode...FUCK!
    		 * mUrlArgsEntity = new UrlEncodedFormEntity(args);
    		 */
    		mUrlArgsEntity = new UrlEncodedFormEntity(args, HTTP.UTF_8);
    	}catch(Exception e) {
    		LSAppLog.e(TAG, "UrlEncodedFormEntity :" + e.toString());
    	}
    	
    	//defaultHdrs.add(new BasicHeader("Content-length", Integer.toString(13)));
    	mURLString = url.toString();
    	return mURLString;
    }
    
    /**
     * createResponse() - creates a specific WSResponse based on this WSRequest
     */
    public WSResponse createResponse(int statusCode, byte[] data, String encoding, String datastring) { 
    	mResp = new WSResponse(statusCode, data, encoding, datastring); 
    	return mResp;
    }
    
    /**
     * getBodySize() - returns the size of the body so we can put that in the query string params
     * 
     * @return int - size of body in bytes
     */
    public int getBodySize() { return mData != null ? mData.length : 0; }
    
    /**
     * signBody() - Returns the Base64 encoded SHA-1 hash of the body
     * @return
     */
    public String signBody()
    {
    	String ret = "";
    	
    	if (mData != null && mData.length > 0) {
			//try {
				//MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				//messageDigest.update(mData);
				//byte[] bytes = messageDigest.digest();
				//ret = SRPUtil.tob64(bytes);
			//} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
			//	e.printStackTrace();
			//}
    	}
    	return ret;
    }
    
    /**
     * hasData() - used to determine if this request is a GET or a POST.
     *  
     * @return true 	- request will be treated as a POST
     * @return false 	- request will be treated as a GET
     */
    public boolean hasData() { return ((mData != null && mData.length > 0) || defaultHdrs.size() > 0); }
    public String getAction() { return ACTION; }
    public ErrorCodes getError() 
    { 
    	if (null == mError) {
    		mError = _getError();
    	}
    	return mError; 
    }
    public String getErrorMsg() 
    { 
    	if (null == mErrorMsg) {
    		mErrorMsg = _getErrorMsg();
    	}
    	return mErrorMsg; 
    }
    
    /**
     * getHeaders() - returns the list of headers to add to the request
     * 
     * @return List<Header>		- the header list
     */
    public List<Header> getHeaders() { return defaultHdrs; }
    public void setHeaders(String name, String value){
    	Header hdr = new BasicHeader(name, value);
    	defaultHdrs.add(hdr);
    }
    
    /**
     * shouldRetry() - should we retry this request
     *
     * @return true		- retry request
     * @return false	- don't retry request
     */
    public boolean shouldRetry() { return mMaxRetries < 0 || mRetryCount < mMaxRetries; }
    public void upRetryCount() { mRetryCount++; }
    
    /**
     * isSecure() - whether this request needs to be done over SSL
     * 
     * @return true 	- request will use SSL
     * @return false 	- request will not use SSL 
     */
    public boolean isSecure() { return false; }
    
    /**
     * id() - 	returns the id of this request which will be used when trying to determine
     * 			if the server has promoted this request to secure (when previously unsecure).
     * 			Whatever returned is the value the server will use when changing the security
     * 			of this request via the Configuration.SECURE_REQUESTS setting
     * 
     * @return	- id of this request
     */
    public String id() { return null; }
    
    /**
     * setOffset - sets the offset for the request.  By default it's 0 but if you're trying to
     * recover from an error then it can be different.
     * 
     * @param offset	- the offset of this request
     */
    public void setOffset(int offset) { mOffset = offset; }
    public int getOffset() { return mOffset; }
    
    /**
     * setReqKey - sets the request key for the request.
     * @param reqKey
     */
    public void setReqKey(String reqKey) { mReqKey = reqKey; }
    public String getReqKey() { return mReqKey; }
    
    /**
     * getUseAuth - on new account/session calls we can't use the chunk token
     * secret even if the client has one since it'll be the old one and the chunk signing will fail.
     * 
     * @return - whether or not to use the chunk token secret when reading the response for this request.
     */
    public boolean useAuth() { return mUseAuth; }
    
    /**
     * canUseMasterCloud - whether this request can go against the master cloud.
     * 
     * @return true	 - can (and will only) use the master cloud
     * @return false - can't use the master cloud (which as you can see is the default for requests) 
     */
    public boolean canUseMasterCloud() { return true; }
}