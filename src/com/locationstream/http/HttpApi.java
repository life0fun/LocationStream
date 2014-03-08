package com.locationstream.http;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public interface HttpApi {

	abstract public String handleHttpRequest(HttpRequestBase httpRequest) 
			throws WSHttpException, IOException ;
    
	abstract public String handleHttpRequest(String url, boolean bpost, NameValuePair... nameValuePairs)
            throws WSHttpException,IOException;

    abstract public HttpGet createHttpGet(String url, NameValuePair... nameValuePairs);

    abstract public HttpPost createHttpPost(String url, NameValuePair... nameValuePairs);
    
    abstract public HttpURLConnection createHttpURLConnectionPost(URL url, String boundary)
            throws IOException; 
}
