package com.locationstream.http;

public class WSHttpException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private String mExtra;

    public WSHttpException(String message) {
        super(message);
    }

    public WSHttpException(String message, String extra) {
        super(message);
        mExtra = extra;
    }
    
    public String getExtra() {
        return mExtra;
    }
}
