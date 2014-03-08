package com.locationstream.http;

import java.io.IOException;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

public class OAuthHttp extends HttpBase {
    private int mOAuthVersion = 1;
    private OAuthConsumer mConsumer;

    public OAuthHttp(DefaultHttpClient httpClient, String key, String secret, int ver) {
        super(httpClient);
        mOAuthVersion = ver;
        setOAuthConsumerCredentials(key, secret);  // when construct, set the consumer key and secret
    }

    public String handleHttpRequest(HttpRequestBase httpRequest) 
    	throws WSHttpException, IOException {
    	if(mOAuthVersion == 2){
    		return super.handleHttpRequest(httpRequest);
    	}else{
    		// only OAuth ver 1 you need to sign the http request.
    	    try {
                mConsumer.sign(httpRequest);
            } catch (OAuthMessageSignerException e) {
                throw new RuntimeException(e);
            } catch (OAuthExpectationFailedException e) {
                throw new RuntimeException(e);
            } catch(Exception e){
            	throw new RuntimeException(e);
            }
            return super.handleHttpRequest(httpRequest);
    	}
    }

    /**
     * OAuth 1.0 needs to sign each http request.
     */
    public void setOAuthConsumerCredentials(String key, String secret) {
        mConsumer = new CommonsHttpOAuthConsumer(key, secret);
    }

    public void setOAuthTokenWithSecret(String token, String tokenSecret) {
        verifyConsumer();
        if (token == null && tokenSecret == null) {
            String consumerKey = mConsumer.getConsumerKey();
            String consumerSecret = mConsumer.getConsumerSecret();
            mConsumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
        } else {
            mConsumer.setTokenWithSecret(token, tokenSecret);
        }
    }

    public boolean hasOAuthTokenWithSecret() {
        verifyConsumer();
        return (mConsumer.getToken() != null) && (mConsumer.getTokenSecret() != null);
    }

    private void verifyConsumer() {
        if (mConsumer == null) {
            throw new IllegalStateException(
                    "Cannot call method without setting consumer credentials.");
        }
    }
}
