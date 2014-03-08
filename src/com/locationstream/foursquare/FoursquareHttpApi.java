package com.locationstream.foursquare;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.SharedPreferences.Editor;
import android.util.Base64;

import com.locationstream.AppPreferences;
import com.locationstream.LocationSensorApp;
import com.locationstream.WebServiceApi;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.http.HttpApi;
import com.locationstream.http.HttpBase;
import com.locationstream.http.OAuthHttp;
import com.locationstream.http.WSHttpException;

public class FoursquareHttpApi implements WebServiceApi {
    private static final String TAG = "API_FSQ"; 
    
    public static final String FOURSQUARE_API2_DOMAIN = "https://api.foursquare.com/v2/";

    /**
     * CLIENT ID and secret, and callback url, no sign http request(1.0), just append name value pair in the request.
     */
    private static final String mClientId = "TI05WPK23W2P3YZGGU1YCJMAGJSSU1PAJQH4ZE3TDSN4221V";
    private static final String mClientSecret = "0FDPA4TBAM3TY1VGWI5KRKDIBI4XGPHKQBOWWE3AIWVJGW3H";
    private static final String mCallbackUrl = "location.no.de/oauth";
    
    public static final String EP_VENUE_TIP = "/venues/id/tips";
        
    private LocationSensorApp mLSApp;
    // encapsulate a http client in http api
    private final DefaultHttpClient mHttpClient = HttpBase.createHttpClient();
    private HttpApi mHttpApi;   // ref to OAuthHttp, access
    private String mAccessToken; // access token
    private final AuthScope mAuthScope;
    
    private String mLogin;
    private String mPassword;

    public FoursquareHttpApi(LocationSensorApp lsapp) {
    	mLSApp = lsapp;
        mAuthScope = new AuthScope(FOURSQUARE_API_DOMAIN, 80);
        mHttpApi = new OAuthHttp(mHttpClient, mClientId, mClientSecret, 2);  // version 2
        //mHttpApi = new BasicAuthHttp(mHttpClient);
        LSAppLog.d(TAG, "FoursquareHttpApi constructor");
    }
    
    /**
     * once we have access token, per OAuth 2.0, we never need to sign the http requests with secrete.
     * @param token
     */
    public void setOAuthToken(String token) {
    	mAccessToken = token;
    }
    
    public boolean loginUser(String login, String password, Editor editor) {
		setCredentials(login, password);
		editor.putString(AppPreferences.PREFERENCE_FS_LOGIN, login);
		editor.putString(AppPreferences.PREFERENCE_FS_PASSWORD, password);
        LSAppLog.e(TAG, "loginUser:" + login + "::" + password);
        
        if (!editor.commit()) {
            return false;
        }
        return true;
	}

	public boolean logoutUser(String login, String passwd, Editor editor) {
		setCredentials(null, null);
		return editor.clear().commit();
	}
	
	public boolean hasValidSession() {
		return hasCredentials();
	}
    
    public void clearAllCredentials() {
        setCredentials(null, null);
        Exception e = new Exception();
        e.printStackTrace();
    }
    
    public void setCredentials(String phone, String password) {
    	LSAppLog.e(TAG, "setCredentials:" + phone + ":" + password);
        if (phone == null || phone.length() == 0 || password == null || password.length() == 0) {
            mHttpClient.getCredentialsProvider().clear();
        } else {
            mHttpClient.getCredentialsProvider().setCredentials(mAuthScope,
                    new UsernamePasswordCredentials(phone, password));
        }
    }
       
    public boolean hasCredentials() {  // log the credential also
    	UsernamePasswordCredentials cred = (UsernamePasswordCredentials)mHttpClient.getCredentialsProvider().getCredentials(mAuthScope);
    	if(cred != null){
    		LSAppLog.e(TAG, "hasCredentials::" + cred.getUserName() + "::" + cred.getPassword());
    		return true;
    	}
        //return mHttpClient.getCredentialsProvider().getCredentials(mAuthScope) != null;
    	return false;
    }

    public void setOAuthConsumerCredentials(String oAuthConsumerKey, String oAuthConsumerSecret) {
        ((OAuthHttp) mHttpApi).setOAuthConsumerCredentials(oAuthConsumerKey,
                oAuthConsumerSecret);
    }
    
    public void setOAuthToken(String token, String secret) {
        ((OAuthHttp) mHttpApi).setOAuthTokenWithSecret(token, secret);
    }

    public boolean hasOAuthTokenWithSecret() {
        return ((OAuthHttp) mHttpApi).hasOAuthTokenWithSecret();
    }
    
    public void loadFoursquare() {
        // Try logging in and setting up foursquare oauth, then user credentials.
        mLogin = AppPreferences.getString(AppPreferences.PREFERENCE_FS_LOGIN);
        mPassword = AppPreferences.getString(AppPreferences.PREFERENCE_FS_PASSWORD);
        LSAppLog.e(TAG, "loadFoursquare: " + mLogin + "::" + mPassword);
        setCredentials(mLogin, mPassword);
    }

    /*
     * ==============================================
     *  v2 api with OAuth 2.0, access token
     * ==============================================
     */
    boolean ensureAccessToken(){
    	if(mAccessToken == null){
    		LSAppLog.e(TAG, "AccessToken: not valid");
    		return false;
    	}else{
    		LSAppLog.e(TAG, "AccessToken: valid");
    		return true;
    	}
    }
    
    public String venues(String ll){
    	if(!ensureAccessToken()){return null;}
    	try {
	    	LSAppLog.d(TAG, "API tips:" + mAccessToken);
	        HttpGet httpGet = mHttpApi.createHttpGet(FOURSQUARE_API2_DOMAIN + "venues/search",  // ? will be appeneded
	        		new BasicNameValuePair("ll", ll),
	                new BasicNameValuePair("client_id", mClientId),
	                new BasicNameValuePair("client_secret", mClientSecret),
	                new BasicNameValuePair("v", "20110704"));   // add the version flag to prevent the default return of oldest data
	        return mHttpApi.handleHttpRequest(httpGet);  // json result.
    	}catch(Exception e){
    		LSAppLog.e(TAG, "FSAPI exception:" + e.toString());
    	}
    	return null;
    }
    
    /**
     * https://api.foursquare.com/v2/venues/VENUE_ID/tips
     * 1. how to inform caller the access token expired ? network error, or API endpoint server error ?
     */
    String tips(String venue_id) { // throws WSHttpException, IOException {  No Exception throw. Handle it within boundary!!!
    	if(!ensureAccessToken()){return null;}
    	try {
	    	LSAppLog.d(TAG, "API tips:" + mAccessToken);
	        HttpGet httpGet = mHttpApi.createHttpGet(FOURSQUARE_API2_DOMAIN + "venues/"+venue_id+"/tips",  // ? will be appeneded 
	                new BasicNameValuePair("oauth_token", mAccessToken));
	        return mHttpApi.handleHttpRequest(httpGet);  // json result.
    	}catch(Exception e){
    		LSAppLog.e(TAG, "FSAPI exception:" + e.toString());
    	}
    	return null;
    }
    
    /*
     * ==============================================
     *  below code is for deprecated v1 api
     * ==============================================
     */
    public static final String FOURSQUARE_API_DOMAIN = "api.foursquare.com";
    public static final String FOURSQUARE_MOBILE_ADDFRIENDS = "http://m.foursquare.com/addfriends";
    public static final String FOURSQUARE_MOBILE_FRIENDS = "http://m.foursquare.com/friends";
    public static final String FOURSQUARE_MOBILE_SIGNUP = "http://m.foursquare.com/signup";
    public static final String FOURSQUARE_PREFERENCES = "http://foursquare.com/settings";
    
    public static final String URL_API_AUTHEXCHANGE = "/authexchange";
    public static final String URL_API_ADDVENUE = "/addvenue";
    private static final String URL_API_ADDTIP = "/addtip";
    private static final String URL_API_CITIES = "/cities";
    private static final String URL_API_CHECKINS = "/checkins";
    private static final String URL_API_CHECKIN = "/checkin";
    private static final String URL_API_USER = "/user";
    private static final String URL_API_VENUE = "/venue";
    private static final String URL_API_VENUES = "/venues";
    private static final String URL_API_TIPS = "/tips";
    private static final String URL_API_FRIEND_REQUESTS = "/friend/requests";
    private static final String URL_API_FRIEND_APPROVE = "/friend/approve";
    private static final String URL_API_FRIEND_DENY = "/friend/deny";
    private static final String URL_API_FRIEND_SENDREQUEST = "/friend/sendrequest";
    private static final String URL_API_FRIENDS = "/friends";
    private static final String URL_API_FIND_FRIENDS_BY_NAME = "/findfriends/byname";
    private static final String URL_API_FIND_FRIENDS_BY_PHONE = "/findfriends/byphone";
    private static final String URL_API_FIND_FRIENDS_BY_FACEBOOK = "/findfriends/byfacebook";
    private static final String URL_API_FIND_FRIENDS_BY_TWITTER = "/findfriends/bytwitter";
    private static final String URL_API_CATEGORIES = "/categories";
    private static final String URL_API_HISTORY = "/history";
    private static final String URL_API_TIP_TODO = "/tip/marktodo";
    private static final String URL_API_TIP_DONE = "/tip/markdone";
    private static final String URL_API_FIND_FRIENDS_BY_PHONE_OR_EMAIL = "/findfriends/byphoneoremail";
    private static final String URL_API_INVITE_BY_EMAIL = "/invite/byemail";
    private static final String URL_API_SETPINGS = "/settings/setpings";
    private static final String URL_API_VENUE_FLAG_CLOSED = "/venue/flagclosed";
    private static final String URL_API_VENUE_FLAG_MISLOCATED = "/venue/flagmislocated";
    private static final String URL_API_VENUE_FLAG_DUPLICATE = "/venue/flagduplicate";
    private static final String URL_API_VENUE_PROPOSE_EDIT = "/venue/proposeedit";
    private static final String URL_API_USER_UPDATE = "/user/update";
  
    public static final String MALE = "male";
    public static final String FEMALE = "female";

    /*
     * /authexchange?oauth_consumer_key=d123...a1bffb5&oauth_consumer_secret=fec...
     */
     String authExchange(String phone, String password) throws WSHttpException, IOException {
        if (((OAuthHttp) mHttpApi).hasOAuthTokenWithSecret()) {
            throw new IllegalStateException("Cannot do authExchange with OAuthToken already set");
        }
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_AUTHEXCHANGE), //
                new BasicNameValuePair("fs_username", phone), //
                new BasicNameValuePair("fs_password", password));
        return  mHttpApi.handleHttpRequest(httpPost);
    }

    /*
     * /addtip?vid=1234&text=I%20added%20a%20tip&type=todo (type defaults "tip")
     */
    String addtip(String vid, String text, String type, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_ADDTIP), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("text", text), //
                new BasicNameValuePair("type", type), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt));
        return (String) mHttpApi.handleHttpRequest(httpPost);
    }

    /**
     * @param name the name of the venue
     * @param address the address of the venue (e.g., "202 1st Avenue")
     * @param crossstreet the cross streets (e.g., "btw Grand & Broome")
     * @param city the city name where this venue is
     * @param state the state where the city is
     * @param zip (optional) the ZIP code for the venue
     * @param phone (optional) the phone number for the venue
     * @return
     * @throws FoursquareException
     * @throws FoursquareCredentialsException
     * @throws FoursquareError
     * @throws IOException
     */
    String addvenue(String name, String address, String crossstreet, String city, String state,
            String zip, String phone, String categoryId, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_ADDVENUE), //
                new BasicNameValuePair("name", name), //
                new BasicNameValuePair("address", address), //
                new BasicNameValuePair("crossstreet", crossstreet), //
                new BasicNameValuePair("city", city), //
                new BasicNameValuePair("state", state), //
                new BasicNameValuePair("zip", zip), //
                new BasicNameValuePair("phone", phone), //
                new BasicNameValuePair("primarycategoryid", categoryId), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return mHttpApi.handleHttpRequest(httpPost);
    }

    /*
     * /cities
     */
    @SuppressWarnings("unchecked")
    String cities() throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CITIES));
        return mHttpApi.handleHttpRequest(httpGet);
    }

    /*
     * /checkins?
     */
    @SuppressWarnings("unchecked")
    String checkins(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CHECKINS), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt));
        return mHttpApi.handleHttpRequest(httpGet);
    }

    /*
     * /checkin?vid=1234&venue=Noc%20Noc&shout=Come%20here&private=0&twitter=1
     */
    public String checkin(String vid, String venue, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt, String shout, boolean isPrivate, boolean tellFollowers,
            boolean twitter, boolean facebook) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_CHECKIN), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("venue", venue), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("shout", shout), //
                new BasicNameValuePair("private", (isPrivate) ? "1" : "0"), //
                new BasicNameValuePair("followers", (tellFollowers) ? "1" : "0"), //
                new BasicNameValuePair("twitter", (twitter) ? "1" : "0"), //
                new BasicNameValuePair("facebook", (facebook) ? "1" : "0"), //
                new BasicNameValuePair("markup", "android")); // used only by android for checkin result 'extras'.
        return (String) mHttpApi.handleHttpRequest(httpPost);
    }

    /**
     * /user?uid=9937
     */
    String user(String uid, boolean mayor, boolean badges, String geolat, String geolong,
            String geohacc, String geovacc, String geoalt) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_USER), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("mayor", (mayor) ? "1" : "0"), //
                new BasicNameValuePair("badges", (badges) ? "1" : "0"), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (String) mHttpApi.handleHttpRequest(httpGet);
    }

    /**
     * /venues?geolat=37.770900&geolong=-122.43698
     */
    @SuppressWarnings("unchecked")
    public String venues(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt, String query, int limit) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_VENUES + ".json"), // always use json format.
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("q", query), //
                new BasicNameValuePair("l", String.valueOf(limit)));
        return mHttpApi.handleHttpRequest(httpGet); 
    }

    /**
     * /venue?vid=1234
     */
    public String venue(String vid, String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_VENUE), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (String) mHttpApi.handleHttpRequest(httpGet); // new VenueParser());
    }

    /**
     * /tips?geolat=37.770900&geolong=-122.436987&l=1
     */
    @SuppressWarnings("unchecked")
    public String tips(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt, int limit) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_TIPS), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("l", String.valueOf(limit)) //
                );
        return mHttpApi.handleHttpRequest(httpGet); 
    }

    /*
     * /friends?uid=9937
     */
    @SuppressWarnings("unchecked")
    String friends(String uid, String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FRIENDS), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return mHttpApi.handleHttpRequest(httpGet); // new GroupParser(new UserParser()));
    }

    /*
     * /friend/requests
     */
    @SuppressWarnings("unchecked")
    String friendRequests() throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FRIEND_REQUESTS));
        return mHttpApi.handleHttpRequest(httpGet); // new GroupParser(new UserParser()));
    }

    /*
     * /friend/approve?uid=9937
     */
    String friendApprove(String uid) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_APPROVE), //
                new BasicNameValuePair("uid", uid));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new UserParser());
    }

    /*
     * /friend/deny?uid=9937
     */
    String friendDeny(String uid) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_DENY), //
                new BasicNameValuePair("uid", uid));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new UserParser());
    }

    /*
     * /friend/sendrequest?uid=9937
     */
    String friendSendrequest(String uid) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_SENDREQUEST), //
                new BasicNameValuePair("uid", uid));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new UserParser());
    }

    /**
     * /findfriends/byname?q=john doe, mary smith
     */
    @SuppressWarnings("unchecked")
    public String findFriendsByName(String text) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FIND_FRIENDS_BY_NAME), //
                new BasicNameValuePair("q", text));
        return mHttpApi.handleHttpRequest(httpGet); // new GroupParser(new UserParser()));
    }

    /**
     * /findfriends/byphone?q=555-5555,555-5556
     */
    @SuppressWarnings("unchecked")
    public String findFriendsByPhone(String text) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_PHONE), //
                new BasicNameValuePair("q", text));
        return mHttpApi.handleHttpRequest(httpPost); //new GroupParser(new UserParser()));
    }

    /**
     * /findfriends/byfacebook?q=friendid,friendid,friendid
     */
    @SuppressWarnings("unchecked")
    public String findFriendsByFacebook(String text) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_FACEBOOK), //
                new BasicNameValuePair("q", text));
        return mHttpApi.handleHttpRequest(httpPost); //new GroupParser(new UserParser()));
    }
    
    /**
     * /findfriends/bytwitter?q=yourtwittername
     */
    @SuppressWarnings("unchecked")
    public String findFriendsByTwitter(String text) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FIND_FRIENDS_BY_TWITTER), //
                new BasicNameValuePair("q", text));
        return mHttpApi.handleHttpRequest(httpGet); // new GroupParser(new UserParser()));
    }
    
    /**
     * /categories
     */
    @SuppressWarnings("unchecked")
    public String categories() throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CATEGORIES));
        return mHttpApi.handleHttpRequest(httpGet); //new GroupParser(new CategoryParser()));
    }

    /**
     * /history
     */
    @SuppressWarnings("unchecked")
    public String history(String limit, String sinceid) throws WSHttpException, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_HISTORY),
            new BasicNameValuePair("l", limit),
            new BasicNameValuePair("sinceid", sinceid));
        return mHttpApi.handleHttpRequest(httpGet); //new GroupParser(new CheckinParser()));
    }
    
    /**
     * /tip/marktodo
     */
    public String tipMarkTodo(String tipId) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_TIP_TODO), //
                new BasicNameValuePair("tid", tipId));
        return (String) mHttpApi.handleHttpRequest(httpPost); //new TipParser());
    }
    
    /**
     * /tip/markdone
     */
    public String tipMarkDone(String tipId) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_TIP_DONE), //
                new BasicNameValuePair("tid", tipId));
        return (String) mHttpApi.handleHttpRequest(httpPost); //new TipParser());
    }
    
    /**
     * /findfriends/byphoneoremail?p=comma-sep-list-of-phones&e=comma-sep-list-of-emails
     */
    public String findFriendsByPhoneOrEmail(String phones, String emails) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_PHONE_OR_EMAIL), //
                new BasicNameValuePair("p", phones),
                new BasicNameValuePair("e", emails));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new FriendInvitesResultParser());
    }
    
    /**
     * /invite/byemail?q=comma-sep-list-of-emails
     */
    public String inviteByEmail(String emails) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_INVITE_BY_EMAIL), //
                new BasicNameValuePair("q", emails));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new ResponseParser());
    }
    
    /**
     * /settings/setpings?self=[on|off]
     */
    public String setpings(boolean on)throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_SETPINGS), //
                new BasicNameValuePair("self", on ? "on" : "off"));
        return (String) mHttpApi.handleHttpRequest(httpPost); //new SettingsParser());
    }
    
    /**
     * /settings/setpings?uid=userid
     */
    public String setpings(String userid, boolean on) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_SETPINGS), //
                new BasicNameValuePair(userid, on ? "on" : "off"));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new SettingsParser());
    }
    
    /**
     * /venue/flagclosed?vid=venueid
     */
    public String flagclosed(String venueId) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_CLOSED), //
                new BasicNameValuePair("vid", venueId));
        return (String) mHttpApi.handleHttpRequest(httpPost); //new ResponseParser());
    }

    /**
     * /venue/flagmislocated?vid=venueid
     */
    public String flagmislocated(String venueId) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_MISLOCATED), //
                new BasicNameValuePair("vid", venueId));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new ResponseParser());
    }

    /**
     * /venue/flagduplicate?vid=venueid
     */
    public String flagduplicate(String venueId) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_DUPLICATE), //
                new BasicNameValuePair("vid", venueId));
        return (String) mHttpApi.handleHttpRequest(httpPost); // new ResponseParser());
    }
    
    /**
     * /venue/prposeedit?vid=venueid&name=...
     */
    public String proposeedit(String venueId, String name, String address, String crossstreet, 
            String city, String state, String zip, String phone, String categoryId, String geolat, 
            String geolong, String geohacc, String geovacc, String geoalt) throws WSHttpException, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_PROPOSE_EDIT), //
                new BasicNameValuePair("vid", venueId), //
                new BasicNameValuePair("name", name), //
                new BasicNameValuePair("address", address), //
                new BasicNameValuePair("crossstreet", crossstreet), //
                new BasicNameValuePair("city", city), //
                new BasicNameValuePair("state", state), //
                new BasicNameValuePair("zip", zip), //
                new BasicNameValuePair("phone", phone), //
                new BasicNameValuePair("primarycategoryid", categoryId), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (String) mHttpApi.handleHttpRequest(httpPost); //new ResponseParser());
    }
    
    private String fullUrl(String url) {
        return "http://" + FOURSQUARE_API_DOMAIN + "/v1/" + url;
    }
    
    /**
     * /user/update
     * Need to bring this method under control like the rest of the api methods. Leaving it 
     * in this state as authorization will probably switch from basic auth in the near future
     * anyway, will have to be updated. Also unlike the other methods, we're sending up data
     * which aren't basic name/value pairs.
     */
    public String userUpdate(String imagePathToJpg, String username, String password) 
    	throws WSHttpException, IOException {
        String BOUNDARY = "------------------319831265358979362846";
        String lineEnd = "\r\n"; 
        String twoHyphens = "--";
        int maxBufferSize = 8192;
        String cred = username + ":" + password;
        
        File file = new File(imagePathToJpg);
        FileInputStream fileInputStream = new FileInputStream(file);
        
        HttpURLConnection conn = mHttpApi.createHttpURLConnectionPost(new URL(fullUrl(URL_API_USER_UPDATE)), BOUNDARY);
        conn.setRequestProperty("Authorization", "Basic " +  Base64.encodeToString(cred.getBytes(), 0));
        
        // We are always saving the image to a jpg so we can use .jpg as the extension below.
        DataOutputStream dos = new DataOutputStream(conn.getOutputStream()); 
        dos.writeBytes(twoHyphens + BOUNDARY + lineEnd); 
        dos.writeBytes("Content-Disposition: form-data; name=\"image,jpeg\";filename=\"" + "image.jpeg" +"\"" + lineEnd); 
        dos.writeBytes("Content-Type: " + "image/jpeg" + lineEnd);
        dos.writeBytes(lineEnd); 
        
        int bytesAvailable = fileInputStream.available(); 
        int bufferSize = Math.min(bytesAvailable, maxBufferSize); 
        byte[] buffer = new byte[bufferSize]; 
        
        int bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
        int totalBytesRead = bytesRead;
        while (bytesRead > 0) {
            dos.write(buffer, 0, bufferSize); 
            bytesAvailable = fileInputStream.available(); 
            bufferSize = Math.min(bytesAvailable, maxBufferSize); 
            bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
            totalBytesRead = totalBytesRead  + bytesRead;
        }
        dos.writeBytes(lineEnd); 
        dos.writeBytes(twoHyphens + BOUNDARY + twoHyphens + lineEnd); 
        
        fileInputStream.close(); 
        dos.flush(); 
        dos.close(); 
        
        //UserParser parser = new UserParser();
        InputStream is = conn.getInputStream();
        try {
            //return parser.parse(AbstractParser.createXmlPullParser(is));
        	byte[] b = new byte[8192];
        	is.read(b);
        	return new String(b);
        } finally {
            is.close();
        }
    }

}
