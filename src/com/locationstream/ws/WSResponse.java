
package com.locationstream.ws;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.os.Parcel;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.ws.LSWSBase.ErrorCodes;

public class WSResponse
{
	public static final String TAG = "WSResponse";
	public static final String KEY_RESPONSE_DATA = "com.motorola.locationsensor.ws.key.responsedata";
	private static final String LOG_TAG = "WSResponse";
	
	protected int mStatusCode;
	protected byte[] mData;
	protected String mEncoding;     // used for converting to java String.
	protected String mDataString;
	protected Exception mException = null;
	protected ErrorCodes mError = null;
	protected String mErrorMsg = null;
	protected int mOffset = 0; // in case we want to try this request again, where to start the response from
	protected String mReqKey = null;  // in case we want to try this request again, which key did we use for the response
	protected String mIntentCategory;
	
	// construct response with data and status code
	protected WSResponse(int statusCode, byte[] data, String encoding, String datastring)
	{
		mStatusCode = statusCode;
		mData = data;
		mEncoding = encoding;
		mDataString = datastring;
	}
	
	protected WSResponse()
	{
		mStatusCode = 0; //HTTP_STATUS_CODES.NONE;
		mData = null;
	}

    protected void _writeToParcel(Parcel out, int flags) 
    {
    	out.writeInt(mStatusCode);
    	if (mData != null) {
    		out.writeInt(mData.length);
        	out.writeByteArray(mData);
    	} else {
    		out.writeInt(0);
    	}
    	out.writeInt(getError().toValue());
    	out.writeString(getErrorMsg());
    	out.writeInt(mOffset);
    	out.writeString(mReqKey);
    	out.writeString(mIntentCategory);
    }

    protected void _readFromParcel(Parcel in) 
    {
    	mStatusCode = in.readInt();
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
    	mOffset = in.readInt();
    	mReqKey = in.readString();
    	mIntentCategory = in.readString();
    }
    
    protected ErrorCodes _getError() 
    {
    	// Status code values override any exceptions we may get
    	if (mStatusCode != 0){  // HTTP_STATUS_CODES.NONE && mStatusCode != ErrorTranslator.HTTP_STATUS_CODES.OK) {
    		return ErrorCodes.E_NONE;
    	} else if (mException != null) {
    		return ErrorCodes.E_FAIL;
    	} else {
    		return ErrorCodes.E_NONE;
    	}	
    }
    
    protected String _getErrorMsg()
    {
    	if (mStatusCode != 0 ) { //HTTP_STATUS_CODES.NONE && ErrorTranslator.HTTP_STATUS_CODES.FORBIDDEN == mStatusCode) {
    		return _parseErrorFromService();
    	} else if (mData != null) {
    		// Something barfed on the server, return what we got back
    		return new String(mData);
    	} else if (mException != null) {
    		return mException.toString();
    	} else {
    		return getError().toString();
    	}
    }
    
    protected String _parseErrorFromService()
    {
    	String ret = null;
    	
    	if (null == mData) { return ret; }
    	
    	return new String(mData);
    	/*
    	try {
    		WebserviceProtocol.Error err = WebserviceProtocol.Error.parseFrom(mData);
    		ret = err.getType().toString();
    	} catch (InvalidProtocolBufferException ex) {
    		LSAppLog.e(LOG_TAG, "_parseErrorFromService(): got exception trying to parse error, ex: " + ex);
    	}
    	return ret;
    	*/
    }
    
    public String getAction() { return mReqKey; }
    
    /**
     * checkBodyHash - checks to make sure the passed in body hash matches
     * 
     * @param payloadHash
     * @return true - matches
     * @return false - doesn't match
     */
    public boolean checkBodyHash(String payloadHash) 
    {
    	boolean ret = true;
    	
    	if (payloadHash != null && payloadHash.length() > 0) {
	    	// check the body hash
	    	if (mData != null) {
				MessageDigest messageDigest;
				try {
					messageDigest = MessageDigest.getInstance("SHA-1");
					messageDigest.update(mData);
					byte[] bytes = messageDigest.digest();
					String hash = SRPUtil.tob64(bytes);      
					if (!hash.equals(payloadHash)) {
						LSAppLog.e(LOG_TAG, "body hash doesn't match!!!");
						return false;
					}
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    	}
    	
    	return ret;
    }

    public int getStatusCode() { return mStatusCode; }
    
    // convert raw byte data to java Unicode String
    public String toString() {
    	String s = null;
    	try{
    		s = new String(mData, mEncoding);
    	}catch(UnsupportedEncodingException e){
    		LSAppLog.e(TAG, "WSResponse data unsupported encoding ::" + mEncoding);
    	}
    	
    	return s;
    }
    
    public void setException(Exception ex) 
    { 
    	mException = ex;
    	if (mException != null) {
	    	// Clear the error if we had one
	    	mError = null;
	    	mErrorMsg = null;
    	}
    }
    
    public void setError(ErrorCodes error)
    {
    	mError = error;
    }
    
    /**
     * getError() - returns any error for this response.  The below is a list of the "interesting" errors you may get.  The
     * full list depends on the specific response you're getting but the below are common to all responses.
     * 
     * @return ErrorCodes.None				- nothing to see here, move along...
     * @return ErrorCodes.CancelledError	- the request was cancelled.  You'll only get this when ResponseLevel != ALL
     * @return ErrorCodes.ResponseFinishedError - okay, not really an error, but it does indicate that there's no more data for you. You'll only get this when ResponseLevel != ALL
     * @return ErrorCodes.ShutdownError		- we're shutting down and couldn't do your request.  Sucks to be you.
     * @return ErrorCodes.RadioDownError	- the radio is down when we tried doing the request
     * @return ErrorCodes.ForbiddenError	- this is HTTP_STATUS_CODES.FORBIDDEN
     * @return ErrorCodes.BadRequestError	- this is HTTP_STATUS_CODES.BAD_REQUEST
     * @return ErrorCodes.InternalServerError - this is HTTP_STATUS_CODES.INTERNAL_SERVER_ERROR
     * @return ErrorCodes.ServiceDownError	- the blur remote blur service appears to be down at the moment
     * @return ErrorCodes.OAuthError		- something went bad with the OAuth check
     * @return ErrorCodes.SignatureMismatch	- something went bad with the signing check
     * @return ErrorCodes.ResponseResetError - the request wanted to start from a non-zero offset but the server couldn't, so it started from scratch.  The data is still valid though
     * @return ErrorCodes.NotInitializedError - the request needed user creds but we don't have any yet
     * @return ErrorCodes.NotAllowedError	- the request isn't allowed at this time.  Can happen if request can't use master cloud and that's all we have at the moment.
     * @return ErrorCodes.SocketTimeoutError - the socket timed oot (that's the Canadian way of saying out)
     * @return ErrorCodes.NullPointerError	- uh, oh, something was null that wasn't supposed to be.
     * @return ErrorCodes.InternalError		- something else went bad...
     * @return ErrorCodes.SSLError			- an SSL error occurred.
     * @return ErrorCodes.ServiceUnavailableError - the server isn't available for requests at the moment.
     * 
     */
    public ErrorCodes getError() 
    { 
    	if (null == mError) {
    		setError(_getError());
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
     * getIntentCategory - returns the category to add to the intent when it's broadcast
     * 
     * @return
     */
    public String getIntentCategory() { return mIntentCategory; }
    
}