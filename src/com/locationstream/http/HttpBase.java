package com.locationstream.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.net.Proxy;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.ws.IOUtils;

abstract public class HttpBase implements HttpApi {
    protected static final String TAG = "HTTPBase"; 
  
    private static final String UA_NAME = "User-Agent";
    private static final String UA_VAL = "AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1C10 Safari/419.3";
    
    private static final int TIMEOUT = 60*2;   // 60 seconds timeout ?

    private final DefaultHttpClient mHttpClient;
    //private final String mClientVersion;

    public HttpBase(DefaultHttpClient httpClient) {
        mHttpClient = httpClient;
    }

    public String handleHttpRequest(HttpRequestBase httpRequest) throws WSHttpException, IOException {
    	HttpResponse response = null;
    	HttpEntity respEntity = null;
    	
    	response = executeHttpRequest(httpRequest);
    	respEntity = response.getEntity();
    	
    	switch (response.getStatusLine().getStatusCode()) {
          case 200:   
              try {
              	String encoding;
              	Header encodingheader = respEntity.getContentEncoding();
	    			if(encodingheader != null){
	    				encoding = encodingheader.getValue();
	    			}else{
	    				encoding = "UTF_8";   // the default encoding
	    			}
                  return EntityUtils.toString(respEntity);
              } catch (ParseException e) {
                  throw new WSHttpException(e.getMessage());
              }

          case 401:
              response.getEntity().consumeContent();
              throw new WSHttpException(response.getStatusLine().toString());

          case 404:
              response.getEntity().consumeContent();
              throw new WSHttpException(response.getStatusLine().toString());

          default:
              response.getEntity().consumeContent();
              throw new WSHttpException(response.getStatusLine().toString());
      }    	
    }
    
    // return converted java string from raw data
    public String handleHttpRequest(String url, boolean bpost, NameValuePair... nameValuePairs) throws WSHttpException, IOException {
    	HttpResponse response = null;
    	HttpEntity respEntity = null;
    	
    	if(bpost == true){
    		HttpPost httpPost = createHttpPost(url, nameValuePairs);
    		response = executeHttpRequest(httpPost);
    	}else{
    		HttpGet httpget = createHttpGet(url, nameValuePairs);
    		response = executeHttpRequest(httpget);
    	}
    	
    	respEntity = response.getEntity();
          
        switch (response.getStatusLine().getStatusCode()) {
            case 200:
                try {
                	String encoding;
                	Header encodingheader = respEntity.getContentEncoding();
	    			if(encodingheader != null){
	    				encoding = encodingheader.getValue();
	    			}else{
	    				encoding = "UTF_8";   // the default encoding
	    			}
                    return EntityUtils.toString(respEntity);
                } catch (ParseException e) {
                    throw new WSHttpException(e.getMessage());
                }

            case 401:
                response.getEntity().consumeContent();
                throw new WSHttpException(response.getStatusLine().toString());

            case 404:
                response.getEntity().consumeContent();
                throw new WSHttpException(response.getStatusLine().toString());

            default:
                response.getEntity().consumeContent();
                throw new WSHttpException(response.getStatusLine().toString());
        }
    }

    /**
     * execute() an httpRequest catching exceptions and returning null instead.
     * @param httpRequest
     * @return
     * @throws IOException
     */
    public HttpResponse executeHttpRequest(HttpRequestBase httpRequest) throws IOException {
        try {
            mHttpClient.getConnectionManager().closeExpiredConnections();
            return mHttpClient.execute(httpRequest);
        } catch (IOException e) {
            httpRequest.abort();
            throw e;
        }
    }

    // use GET with passed in headers 
    public HttpGet createHttpGet(String url, NameValuePair... nameValuePairs) {
    	String query = URLEncodedUtils.format(stripNulls(nameValuePairs), HTTP.UTF_8);
        HttpGet httpGet = new HttpGet(url + "?" + query);
        httpGet.addHeader(UA_NAME, UA_VAL);
        return httpGet;
    }

    // use POST with passed in headers
    public HttpPost createHttpPost(String url, NameValuePair... nameValuePairs) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader(UA_NAME, UA_VAL);
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(stripNulls(nameValuePairs), HTTP.UTF_8));
        } catch (UnsupportedEncodingException e1) {
            throw new IllegalArgumentException("Unable to encode http parameters.");
        }
        return httpPost;
    }
    
    // URLConnection.openConnection().getInputStream(), use multipart/form-data encoding for submit form data
    public HttpURLConnection createHttpURLConnectionPost(URL url, String boundary) throws IOException 
    {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(); 
        conn.setDoInput(true);        
        conn.setDoOutput(true); 
        conn.setUseCaches(false); 
        conn.setConnectTimeout(TIMEOUT * 1000);
        conn.setRequestMethod("POST");

        conn.setRequestProperty(UA_NAME, UA_VAL);
        conn.setRequestProperty("Connection", "Keep-Alive"); 
        conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        
        return conn;
    }

    private List<NameValuePair> stripNulls(NameValuePair... nameValuePairs) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        for (int i = 0; i < nameValuePairs.length; i++) {
            NameValuePair param = nameValuePairs[i];
            if (param.getValue() != null) {
                params.add(param);
            }
        }
        return params;
    }

    /**
     * Factory Create a thread-safe client. This client does not do redirecting, to allow us to capture
     * correct "error" codes.
     *
     * @return HttpClient
     */
    public static final DefaultHttpClient createHttpClient() {
        // Sets up the http part of the service.
        final SchemeRegistry supportedSchemes = new SchemeRegistry();

        // Register the "http" protocol scheme, it is required
        // by the default operator to look up socket factories.
        final SocketFactory sf = PlainSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("http", sf, 80));
        
        // register ssl socket
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
            supportedSchemes.register(new Scheme("https", new SSLSocketFactory(keyStore), 443));
		} catch (Exception e) {
			LSAppLog.e(TAG, "exception creating SSL socket factory " +  e.toString());
        } finally {
            IOUtils.closeQuietly(is);
        }
		
        // Set some client http client parameter defaults.
        final HttpParams httpParams = createHttpParams();
        HttpClientParams.setRedirecting(httpParams, false);

        final ClientConnectionManager ccm = new ThreadSafeClientConnManager(httpParams,supportedSchemes);
        DefaultHttpClient httpclient = new DefaultHttpClient(ccm, httpParams);
        httpclient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        return httpclient;
    }

    /**
     * Create the default HTTP protocol parameters.
     */
    private static final HttpParams createHttpParams() {
        final HttpParams params = new BasicHttpParams();

        // Turn off stale checking. Our connections break all the time anyway,
        // and it's not worth it to pay the penalty of checking every time.
        HttpConnectionParams.setStaleCheckingEnabled(params, false);

        HttpConnectionParams.setConnectionTimeout(params, TIMEOUT * 1000);
        HttpConnectionParams.setSoTimeout(params, TIMEOUT * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);

        String proxy = Proxy.getDefaultHost();
		int port = Proxy.getDefaultPort();
		if(proxy != null){
			HttpHost proxyhost = new HttpHost(proxy, port);
			params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyhost);
		}
		
        return params;
    }

}
