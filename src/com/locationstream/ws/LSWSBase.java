package com.locationstream.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Proxy;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import com.locationstream.LocationSensorManager;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.ws.WSRequest.ResponseLevel;

public class LSWSBase implements IWSRequestHandler  
{
	private static final String TAG = "WSBase";
	private static final String SECURE_PREFIX = "https://";
	private static final String CLEAR_PREFIX = "http://";
	private static final String SECURE_PORT = "443";
	private static final String CLEAR_PORT = "80";
	private static final String SECURE_REQS_DELIM = ",";
	private static final String FREEBIE_REQS_DELIM = ",";
	public static final String BACKOFF_APPNAME = "WSBase";
	public static final String DOMAIN = "api.foursquare.com";
	public static final String SERVER_URL_MOBILE_SSL = "blur.service.ws.serverUrlMobileSSL";
	public static final String SERVER_URL_MOBILE_CLEAR = "blur.service.ws.serverUrlMobileClear";
	public static final String SERVER_URL_WIFI_SSL = "blur.service.ws.serverUrlWifiSSL";
	public static final String SERVER_URL_WIFI_CLEAR = "blur.service.ws.serverUrlWifiClear";
	private static final int DEFAULT_TIMESKEW = 0;
	private static final int MAX_CHUNK_SIZE = 256000;
	private static final int BIND_TIMEOUT = 30000; // 30 seconds is all that we'll wait...
	private static final long FREEBIE_TIME_TO_MS = 60*1000; // minutes to ms
	private static final String FREEBIE_REPLACEE = "/ws/";
	private static final String FREEBIE_REPLACER = "/wsf/";
	private static final String SSL_ON_PARAM = "&ssl=1";
	private static final String SSL_OFF_PARAM = "&ssl=0";
	
	private final Context mCtx;
	private ExecutorService mExecutor;
	
	private String mServerUrlBaseSSL; 
	private String mAccountId = null;
	private String mDeviceId = null;

	private DefaultHttpClient mHttpClient;
	private AuthScope mAuth;
	private int mSocketTimeout;
	
	private AtomicBoolean mShutdown;
	private Object mConfigLock = new Object();
	
	private AtomicReference<IWSRequestHandler> mWSRequestHandler;  // ref to doRequest() WS request handler.
	private PowerManager mPowerManager; 
	private long mWakeLockTime;
	private MyIntentReceiver recv = new MyIntentReceiver();
	
	// This'll hold the requests Mother wants us to try later
	private ConcurrentLinkedQueue<WSTransaction> mWaitingRequests; 
	
	public static enum ErrorCodes {
		E_NONE,
		E_FAIL,
		E_MAX;

		public int toValue() {
			// TODO Auto-generated method stub
			return 0;
		}

		public static ErrorCodes fromValue(int readInt) {
			// TODO Auto-generated method stub
			return null;
		}
	};
	
	public interface WSRequestCallback   // client wraps result processing logic into this passing-in object.
	{
		/**
		 * Depending on the ResponseLevel of the request this can be called either,
		 *	1. when the entire response is finished (when ResponseLevel.ALL) or
		 *  2. when a item/chunk is read off the wire (when ResponseLevel.ITEM/CHUNK).
		 *  
		 * When called in the #2 case the boolean value returned indicates whether to continue
		 * processing the request.  Return true to continue, false to cancel the request.  If 
		 * cancelled, you'll get a response with the error ErrorCodes.CancelledError.
		 * 
		 */
		boolean handleResponse(WSResponse resp);
	}
	
	/**
	 * setWSRequestHandler() - sets the web service request handler that will perform doRequest().
	 * You can have multiple request handlers for doing WS.
	 * @param handler	- the new handler to set
	 */
	public void setWSRequestHandler(IWSRequestHandler handler) { mWSRequestHandler.set(handler); }
	
	/**
	 * getDefaultWSRequestHandler() - gets the default web service request handler
	 * 
	 * @return - the default ws request handler
	 */
	public IWSRequestHandler getDefaultWSRequestHandler() { return this; }
	
	
	
	// Notify the WS result by synch calling callback notify type object directly. 
	private static class WSCallbackNotifyType implements IWSRequestHandler.WSRequestNotifyType
	{
		private final WSRequestCallback mCallback;
		
		public WSCallbackNotifyType(WSRequestCallback cb) 
		{
			mCallback = cb;
		}
		
		public boolean notifyOfResponse(WSResponse resp) 
		{
			if (mCallback != null) {
				return mCallback.handleResponse(resp);  // call the client passed processing data callback directly
			}
			return false;
		}
	}
	
	//Notify the WS result by wrap result into parcelable and send asynch thru intent 
	private static class WSIntentNotifyType implements IWSRequestHandler.WSRequestNotifyType
	{
		private final String mResponseDataKey;
		
		public WSIntentNotifyType(String responseDataKey)
		{
			mResponseDataKey = responseDataKey;
		}
		
		public boolean notifyOfResponse(WSResponse resp) 
		{
			if (!(resp instanceof Parcelable)) {
				// I need the response to be parcelable so I can stick it in the intent, if not I'll drop it on the floor
				LSAppLog.e(TAG, "response not parcelable, ignoring...responseDataKey: " + mResponseDataKey + " resp: " + resp);
				return false;
			} 
			
			Intent myIntent =  new Intent(resp.getAction());
			String cat = resp.getIntentCategory();
			if (cat != null) {
				myIntent.addCategory(cat);
			}
			myIntent.putExtra(WSResponse.KEY_RESPONSE_DATA, mResponseDataKey);
			myIntent.putExtra(mResponseDataKey, (Parcelable)resp);
			//BSUtils.sendBroadcast(BlurServiceMother.getInstance(), myIntent);
			return true;
		}
	}
	
	/*  
	 * expose WSBase as remote service, should WSBase be a lib, or a service ?
	 * begin of wsbase as a service
	 */
	private static class WSAIDLNotifyType implements IWSRequestHandler.WSRequestNotifyType
	{
		private Context mCtx = null;
		private Object mBindingLock = new Object();
	    private IWSRequestCallback mProxy = null;
	    private volatile boolean m_isBound;
	    
	    public interface IWSRequestCallback {
	    	boolean handleResponse(WSResponse respParcel) throws RemoteException;
	    }
	    
	    public WSAIDLNotifyType(Context ctx) { mCtx = ctx; }
	    
	    // when ws result available, bind to other service to get callback reference and execute callback.
		public boolean notifyOfResponse(WSResponse resp) 
		{
			if (!(resp instanceof Parcelable)) {
				// I need the response to be parcelable so I can stick it in the intent, if not I'll drop it on the floor
				LSAppLog.e(TAG, "response not parcelable, ignoring..."  + " resp: " + resp);
				return false;
			} 

			Parcelable par = (Parcelable)resp;
			IWSRequestCallback p = _bindProxyService(resp.getAction());
			boolean ret = true;
			
			if (null == p) {
				Log.i(TAG, "WSAIDLNotifyType.notifyOfResponse(): timed out waiting to bind to service.  Response going to ground");
				return false;
			}
			
			try {
				//ret = p.handleResponse(new WSResponseParcel(par));
				ret = p.handleResponse(resp);
			} catch (RemoteException e) {
				LSAppLog.e(TAG, "WSAIDLNotifyType.notifyOfResponse(): got exception: " + e);
			} finally {
				_unbindService();
			}
			
			return ret;
		}
		
		// used by wsbase to bind to caller service to get ref to callback object.
		private ServiceConnection m_serviceConnection = new ServiceConnection() {
	        public void onServiceConnected(ComponentName name, IBinder service) {
	            synchronized(mBindingLock) {
	            	if (mProxy == null){
	                	//mProxy = IWSRequestCallback.Stub.asInterface(service);
	                    m_isBound = true;
	                    mBindingLock.notifyAll();
	                }
	            }
	        }

	        public void onServiceDisconnected(ComponentName arg0) {
	            synchronized(mBindingLock) {
	            	mProxy = null;
	                m_isBound = false;
	            }
	        }		
	    };
	    
	    private IWSRequestCallback _bindProxyService(String action)
	    {
	    	synchronized(mBindingLock) 
	    	{
		        if (mProxy == null)
		        {   
		            mCtx.bindService(new Intent(action), m_serviceConnection, Context.BIND_AUTO_CREATE);
		            try
		            {
		            	if (!m_isBound) {
		            		mBindingLock.wait(BIND_TIMEOUT);
		            	}
		            }
		            catch (InterruptedException ex)
		            {
		                Log.w(TAG, ex);
		            }
		        }
		        
		        return mProxy;
	    	}
	    }
	    
	    private void _unbindService()
	    {
	    	synchronized(mBindingLock) 
	    	{
		    	if (m_isBound) {
		    		mCtx.unbindService(m_serviceConnection);
		    		mProxy = null;
		    		m_isBound = false;
		    	}
	    	}
	    }
	}
	
	private class MyIntentReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(WSRequest.ACTION)) {
				mExecutor.execute(new WSTransaction(intent, false));
			} else if (intent.getAction().equals(WSRequest.ACTION_AIDL)) {
				mExecutor.submit(new WSTransaction(intent, true));
			} else {
				Log.w(TAG, "ignoring intent with action: " + intent.getAction());
			}
		}
	}
	
	//// end of wsbase as a service ////
	
	public LSWSBase(LocationSensorManager lsman, boolean secureOverride)
	{
		mWSRequestHandler = new AtomicReference<IWSRequestHandler>(this); // request handler is WSBase
		mShutdown = new AtomicBoolean(false);
		mWaitingRequests = new ConcurrentLinkedQueue<WSTransaction>();
		mCtx = lsman;
		
		mAccountId = null;
		mDeviceId = null;
		
		mSocketTimeout = 200*1000;  // millsec
		
		constructServiceUrl();
		
		createHttpClient();
			
		mPowerManager = (PowerManager)mCtx.getSystemService(Context.POWER_SERVICE);
		mExecutor = Executors.newSingleThreadExecutor();
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(WSRequest.ACTION);
		filter.addAction(WSRequest.ACTION_AIDL);
		mCtx.registerReceiver(recv, filter);
		
		//setupAPNInfo();
	}
	
	public void shutdown()
	{
		mCtx.unregisterReceiver(recv); // this'll stop us getting any new intent requests
		mShutdown.set(true); // This'll stop us doing any new requests, now get rid of any we have waiting...
	}
	
	public void createHttpClient() {
	   	// config the http client parameters
		BasicHttpParams params = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(params, mSocketTimeout);
		HttpConnectionParams.setSoTimeout(params, mSocketTimeout);

		String proxy = Proxy.getDefaultHost();
		int port = Proxy.getDefaultPort();
		//LSAppLog.e(TAG, "HttpClient Proxy=" + proxy + port);
		if(proxy != null){
			HttpHost proxyhost = new HttpHost(proxy, port);
			params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyhost);
		}
		
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        FileInputStream is = null;
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			String keyStoreName = System.getProperty("javax.net.ssl.trustStore");
			String keyStorePwd = System.getProperty("javax.net.ssl.trustStorePassword");
			char[] pwd;
		    if (keyStorePwd == null) {
		    	pwd = new char[0];
		    } else {
		        pwd = keyStorePwd.toCharArray();
		    }
            is = new FileInputStream(new File(keyStoreName));
            keyStore.load(is, pwd);
			schemeRegistry.register(new Scheme("https", new SSLSocketFactory(keyStore), 443));
		} catch (Exception e) {
			LSAppLog.e(TAG, "exception creating SSL socket factory " + e.toString());
        } finally {
            IOUtils.closeQuietly(is);
        }
		ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
		
	    HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {

	        //@Override
	        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
	            AuthState authState = (AuthState)context.getAttribute(ClientContext.TARGET_AUTH_STATE);
	            CredentialsProvider credsProvider = (CredentialsProvider)context.getAttribute(ClientContext.CREDS_PROVIDER);
	            HttpHost targetHost = (HttpHost)context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);

	            // If not auth scheme has been initialized yet
	            if (authState.getAuthScheme() == null) {
	                AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
	                // Obtain credentials matching the target host
	                org.apache.http.auth.Credentials creds = credsProvider.getCredentials(authScope);
	                // If found, generate BasicScheme preemptively
	                if (creds != null) {
	                    authState.setAuthScheme(new BasicScheme());
	                    authState.setCredentials(creds);
	                }
	            }
	        }

	    };

		mHttpClient = new DefaultHttpClient(cm, params);
		mHttpClient.addRequestInterceptor(preemptiveAuth, 0);
		mHttpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
	
    }		
	
	public void setCredentials(String phone, String password) {
		String BaseUrl = "http://" + DOMAIN + "/v1";
	    AuthScope mAuthScope = new AuthScope(DOMAIN, 80);

		if (phone == null || phone.length() == 0 || password == null || password.length() == 0) {
            mHttpClient.getCredentialsProvider().clear();
        } else {
            mHttpClient.getCredentialsProvider().setCredentials(mAuthScope,
                    new UsernamePasswordCredentials(phone, password));
        }
    }
	
	/**
	 * initiateRequest - starts a web service request, when finished the callback will be called
	 * 
	 * @param req		- the request to start
	 * @param callback	- callback which will be called when response is available
	 */
	public void initiateRequest(WSRequest req, WSRequestCallback callback)
	{
		if (!mExecutor.isShutdown()) {
			mExecutor.execute(new WSTransaction(req, callback));
        } else {
            LSAppLog.e(TAG, "_runTask(): trying to execute task after I've been killed: ");
        }
	}
	
	// per function programming, this should be a function closure!!!
	private class WSTransaction implements Runnable
	{
		// when created, specify what is the request and how to handle response.
		private final WSRequest mRequest;
		private final IWSRequestHandler.WSRequestNotifyType mReqNotifyType;
		
		// constructor -- notify by callback, pass in callback object
		WSTransaction(WSRequest request, WSRequestCallback callback) 
		{
			mRequest = request;
			mReqNotifyType = new WSCallbackNotifyType(callback);
		}
		
		// constructor -- notify by intent 
		WSTransaction(Intent intent, boolean aidlResponse)
		{
			String requestDataKey = intent.getStringExtra(WSRequest.KEY_REQUEST_DATA);
			String responseDataKey = intent.getStringExtra(WSResponse.KEY_RESPONSE_DATA);
			Parcelable p = intent.getParcelableExtra(requestDataKey);
			if (!(p instanceof WSRequest)) {
				// WTF, somebody is sending us an improper request, drop to the floor...
				LSAppLog.e(TAG, "received improper request, ignoring...requestDataKey: " + requestDataKey + " responseDataKey: " + responseDataKey);
				mRequest = null;
				mReqNotifyType = null;
				return;
			}
			
			mRequest = (WSRequest)p;
			mReqNotifyType = aidlResponse ? new WSAIDLNotifyType(mCtx) : new WSIntentNotifyType(responseDataKey);
		}
		
		public WSRequest getRequest() { return mRequest; }
		
		public String toString()
		{
			return "WSTransaction(" + mRequest.toString() + ")";
		}
		
		public void run() 
		{
			// Can't run a bad transaction...
			if (null == mRequest) {
				LSAppLog.e(TAG, "WSTransaction - no request to run!");
				return;
			}
			
			WSResponse resp;
			
			Log.d(TAG, "processing request: " + mRequest.id());
			
			// Create the coffee (because it keeps us awake) wake lock for the duration of the web request
			PowerManager.WakeLock hotCoffee = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			try {
				hotCoffee.acquire();
				
				resp = mWSRequestHandler.get().doRequest(mRequest, mReqNotifyType);
				
				// Put the offset/key from the response in the request in case we need to do this sucker again
				mRequest.setOffset(resp.getOffset());
				mRequest.setReqKey(resp.getReqKey());
					
			} finally {
				// Before we release our wake lock grab another one for mWakeLockTime to give the recipient of the response time to do something
				PowerManager.WakeLock lastChanceWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ".lastChance");
				lastChanceWakeLock.acquire(mWakeLockTime);
				hotCoffee.release();
			}
		}
		
		public boolean requestIsDone(ErrorCodes err)
		{
			if (!mRequest.shouldRetry()) {
				WSResponse resp = mRequest.createResponse(200, null, null, null);  // HTTP STATUS CODE
				resp.setError(err);
				mReqNotifyType.notifyOfResponse(resp);
				return true;
			}
			return false;
		}
	}
	
	
	private void parseSecureReqs(String values)
	{
		Map<String, String> mSecureReqs = new HashMap<String, String>();
		
		if (null == values || values.length() == 0) {
			return;
		}
		
		StringTokenizer tokenizer = new StringTokenizer(values, SECURE_REQS_DELIM);
		while (tokenizer.hasMoreTokens()) {
    		mSecureReqs.put(tokenizer.nextToken(), "1");
    	}
	}
	
	private void parseFreebieReqs(Set<String> theSet, String values) 
	{
		theSet.clear();
		
		if (null == values || values.length() == 0) {
			return;
		}
		
		StringTokenizer tokenizer = new StringTokenizer(values, FREEBIE_REQS_DELIM);
		while (tokenizer.hasMoreTokens()) {
			theSet.add(tokenizer.nextToken());
    	}
	}
	
	private void constructServiceUrl()
	{
		synchronized (mConfigLock) {
			int connectionType = ConnectivityManager.TYPE_MOBILE; //mPollingMgr.getConnectionType();
			String serverHostnameSSL = null;
			String serverHostnameClear = null;
			
			switch (connectionType) {
				case ConnectivityManager.TYPE_MOBILE:
					serverHostnameSSL = LSWSBase.SERVER_URL_MOBILE_SSL;
					serverHostnameClear = LSWSBase.SERVER_URL_MOBILE_CLEAR;
					break;
				case ConnectivityManager.TYPE_WIFI:
					serverHostnameSSL = LSWSBase.SERVER_URL_WIFI_SSL;
					serverHostnameClear = LSWSBase.SERVER_URL_WIFI_CLEAR;
					break;
				case -1: // not connected
					break;
				default:
					LSAppLog.e(TAG, "constructServiceUrl(): unknown connectivity type: " + connectionType);
			}		
		}
	}
	
	private void setupAPNInfo()
	{
		int connectionType = ConnectivityManager.TYPE_MOBILE;
		
		if (ConnectivityManager.TYPE_MOBILE == connectionType || ConnectivityManager.TYPE_WIFI == connectionType) {
			// First clear the proxy settings in case this apn doesn't use them
			mHttpClient.getParams().removeParameter(ConnRoutePNames.DEFAULT_PROXY);
			mHttpClient.getCredentialsProvider().clear();
			
			if (ConnectivityManager.TYPE_MOBILE == connectionType) {
				String apnName = "apn"; //mPollingMgr.getActiveNetworkInfo().getExtraInfo();
				Log.d(TAG, "setupAPNInfo(): looking up " + apnName);
			} 
		}
	}
	
	/**
	 * cloudReconfiguring - called by mother when she's about to get a new cloud list.  This invalidates the cloud
	 * info we currently know about (and works around a race when mom updates the config on disk) so that all requests
	 * will see the cloud not configured.
	 */
	public void cloudReconfiguring()
	{
		synchronized (mConfigLock) {
			mServerUrlBaseSSL = null;
		}
	}
	
	
	public WSRequest canAMotherGetARequest(String desiredClassName)
	{
		Object[] reqs = mWaitingRequests.toArray();
		for (int i = reqs.length-1; i >=0; i--) {
			WSRequest req = ((WSTransaction)reqs[i]).getRequest();
			if (desiredClassName.equals(req.getClass().getName())) {
				return req;
			}
		}
		return null;
	}
	
	public boolean hasWaitingReqs()
	{
		return !mWaitingRequests.isEmpty();
	}
	
	private boolean cloudConfigured() {
		return mServerUrlBaseSSL != null;
	}
	
	
	public WSResponse doRequest(WSRequest req, IWSRequestHandler.WSRequestNotifyType rnt)
	{
		Exception ex = null;
		WSResponse resp = null;
		HttpEntity respEntity = null;
		HttpRequestBase httpReq = null;
		HttpPost postReq = null;
		ChunkedInputStream chunky = null;
		ResponseLevel rl = req.getResponseLevel();
		int offsetThisTime = req.getOffset();
		int responseLength = 0;
		
		req.upRetryCount();
		
		if (mShutdown.get()) {
			Log.i(TAG, "doRequest(): shutting down, not doing request: " + req.id());
			resp = req.createResponse(0, null, null, null);
			resp.setError(ErrorCodes.E_NONE);
			return resp;
		}
		
		try
	    {
			String url = null;
			String chunkTokenSecret = null;
			int chunkSize = 0;
			
			synchronized (mConfigLock) {
				if (!req.canUseMasterCloud() && !cloudConfigured()) {
					// Now there's a race between when the polling manager says we're connected (the above isConnected() check)
					// and us getting the connectivity intent sent by polling manager to tell is which network we're on and such.
					// In such a case, we'll get here and we won't have our cloud configured (especially if the device starts
					// without a radio connection), but we really could if we just tried.  So, we'll try configuring our cloud
					// to see if we can indeed do this request
					Log.i(TAG, "doRequest(): device is connected but we don't seem to have a cloud configured, trying now...");
					constructServiceUrl();
					setupAPNInfo();
					if (!cloudConfigured()) {
						// Well we tried our best to get the cloud configured but it didn't work, we've gotta return an error now
						resp = req.createResponse(0, null, null, null);
						resp.setError(ErrorCodes.E_NONE);
						return resp;
					}
				}
				
				//String serverUrl = mServerUrlBaseSSL; 
				//url = req.getUrl(serverUrl, mAccountId, mDeviceId, null);    
		        url = req.getUrl();
		        LSAppLog.e(TAG, "url=" + url);
		    }
	    	
	    	if (req.hasData()) {
	    		postReq = new HttpPost(url);
	    		postReq.setEntity(req.getEntity());  // get UrlEncodedFormEntity(vid=xx&twitter=1)
	    		httpReq = postReq;
	    		//httpReq = new HttpGet(url); //postReq;
	    	} else {
	    		httpReq = new HttpGet(url);
	    	}
	    	
	    	//The following is a hack because it appears that even though
	    	//StaleCheckingEnabled is set to true, that the socket still goes
	    	//stale and does not get removed from the pool
	    	//httpReq.addHeader("Connection", "close");
	    	
	    	// Ask the request for any headers it wishes to add
	    	List<Header> hdrs = req.getHeaders();
	    	for (Header hdr : hdrs) {
	    		postReq.addHeader(hdr);
	    		//LSAppLog.e(TAG, "HttpHeader:" + hdr.toString());
	    	}
	    	
	    	LSAppLog.e(TAG, "doRequest(): url: " + httpReq.getURI());
	    	LSAppLog.e(TAG, "doRequest(): http_proxy:" + mHttpClient.getParams().getParameter(ConnRoutePNames.DEFAULT_PROXY));
	    	for (Header h : httpReq.getAllHeaders()){
	    		LSAppLog.e(TAG, "Header=="+ h.toString());
	    	}
	    	
	    	long startTime = System.currentTimeMillis();
	    	
	    	//LSAppLog.e(TAG, "doRequest(): " + httpReq.toString());
	    	HttpResponse response = mHttpClient.execute(postReq);
	    	
	    	respEntity = response.getEntity();
	    	int statusCode = response.getStatusLine().getStatusCode();
	
	    	byte[] respData = null;
	    	String encoding = null;   // what raw data byte array is encoded ?
	    	String respString = null;
	    	boolean cancelled = false;
	    	ErrorCodes respError = ErrorCodes.E_NONE;
	    	
	    	LSAppLog.e(TAG, "status=" + statusCode + " len=" + respEntity.getContentLength() + " ischunky=" + respEntity.isChunked());
	    	
	    	if (respEntity != null) {
	    		if (statusCode != 200) {
	    			Log.i(TAG, "doRequest(): overriding request response level of " + rl + " since error in response: " + statusCode);
	    			respData = EntityUtils.toByteArray(respEntity);  // get the raw byte array, which was encoded by the server.
	    			responseLength += respData.length;
	    			respData = respData.length != 0 ? respData : null;
	    		} else if(respEntity.isChunked() == false) {			
	    			Header encodingheader = respEntity.getContentEncoding();
	    			if(encodingheader != null){
	    				encoding = encodingheader.getValue();
	    			}else{
	    				encoding = "UTF-8";   // the default encoding
	    			}
	    			
	    			respData = EntityUtils.toByteArray(respEntity);
	    			//respString = EntityUtils.toString(respEntity);   // content has benn consumed if converted to byte array first!!!
	    			resp = req.createResponse(statusCode, respData, encoding, respString);
	    			rnt.notifyOfResponse(resp);
	    			LSAppLog.e(TAG, "respEntity encoding=" + encoding + "Data=" + respString);
	    		} else {
			    	// Everything is always chunked coming to the client so create that stream
			    	chunky = new ChunkedInputStream(respEntity.getContent()); 
			    	boolean firstResponse = true; 
			    	
			    	if (ResponseLevel.ALL == rl || ResponseLevel.CHUNK == rl) {
			    		ByteArrayBuffer bab = new ByteArrayBuffer(chunkSize);
			    		byte[] tData = new byte[chunkSize];
			    		int readThisTime = 0;
			    		
			    		do {
			    			if (mShutdown.get()) {
			    				break;
			    			}
			    			readThisTime = chunky.read(tData);
			    			if (readThisTime != -1) {
			    				// Nobody is actually using the CHUNK level response but they could so here it is...
			    				if (ResponseLevel.CHUNK == rl) {
			    					if (firstResponse) {
				    					// Reset our back off since we've read some data
				    					//BlurServiceMother.getInstance().connectStatus(WSBase.BACKOFF_APPNAME, true);
				    				}
			    					ByteArrayBuffer chunk = new ByteArrayBuffer(readThisTime);
			    					chunk.append(tData, 0, readThisTime);
			    					responseLength += readThisTime;
			    					resp = req.createResponse(statusCode, chunk.toByteArray(), null, null);
			    					resp.setOffset(offsetThisTime);
			    					
			    					firstResponse = false;
			    					cancelled = !rnt.notifyOfResponse(resp);
			    					//offsetThisTime = chunky.getInitialOffset() + chunky.getConsumed();
			    					offsetThisTime += readThisTime;
			    					if (cancelled) {
			    						break;
			    					}
			    				} else {
			    					firstResponse = false;
			    					bab.append(tData, 0, readThisTime);
			    				}
			    			} else {
			    				if (ResponseLevel.CHUNK == rl) {
			    					respError = ErrorCodes.E_FAIL;
			    				}
			    			}
			    		} while (readThisTime != -1);
			    		
			    		if (!cancelled && !mShutdown.get()) {
				    		if (ResponseLevel.CHUNK == rl) { 
				    			respData = null;
				    		} else {
				    			respData = bab.toByteArray();
				    			responseLength += respData.length;
				    			respData = respData.length != 0 ? respData : null;
				    		}
			    		}
			    	}
	    		}
	    	} 

	    	if (cancelled) {
	    		Log.i(TAG, "doRequest(): request: " + req.id() + " was cancelled.");
	    		resp = req.createResponse(statusCode, null, null, null);
	    		resp.setError(ErrorCodes.E_FAIL);
	    	} else if (mShutdown.get()) {
	    		Log.i(TAG, "doRequest(): stopping request: " + req.id() + " since we're shutting down.");
	    		resp = req.createResponse(statusCode, null, null, null);
	    		resp.setError(ErrorCodes.E_FAIL);
	    	}
	    	
	    	if (ErrorCodes.E_NONE != respError) {
	    		resp.setError(respError);
	    	}
	    }
		catch (RuntimeException e) 
	    {
	    	// In case of an unexpected exception abort the HTTP request 
	    	// in order to shut down the underlying connection and 
	    	// release it back to the connection manager.
	    	if (httpReq != null) {
	    		httpReq.abort();
	    	}
	    	ex = e;
	    }
		catch (Exception e)
	    {
	    	ex = e;
	    }
	    finally
	    {
	    	try {
		    	if (respEntity != null) {
		    		//respEntity.consumeContent(); //this entity is no longer required. GC
		    	}
	    	} catch (Exception e) {
	    		ex = e;
	    	}
	    	
	    	if (null == resp) {
				resp = req.createResponse(200, null, null, null);
			}
	    	
	    	//resp.setOffset(chunky != null ? offsetThisTime : req.getOffset());
	    
	    	// In case we want to try this request again (since we may have gotten an error)
			// keep track of the key and offset we were using.
			//resp.setReqKey(chunky != null ? chunky.getKey() : req.getReqKey());
			
			Log.d(TAG, "doRequest(): " + req.id() + " resp length: " + responseLength);
	    }
		
	    if (ex != null) {
	    	LSAppLog.e(TAG, "doRequest(): got exception: " + Log.getStackTraceString(ex));
	    	resp.setException(ex);
	    }
		
		return resp;
	}
}
