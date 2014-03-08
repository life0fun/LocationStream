package com.locationstream.ws.googleplaces;

import com.locationstream.LocationSensorManager;
import com.locationstream.ws.LSWSBase;

public class PlacesAPI extends LSWSBase {

	public static final String TAG = "PlacesAPI";
    //http://maps.google.com/maps/api/place/search/json?location=40.717859,-73.957790&radius=1600&client=clientId&sensor=true_or_false&signature=SIGNATURE
	public static final String PLACEAPI_URL = "http://maps.google.com/maps/api/place/search/json?";
    public static final String CLIEND_ID = "RLzeVWXV34HBlkEdaZgirL5kPZeDdBw6QMMs7PeCqYA7FHc566CwSKFsuShVODZD";
    public static final String SIGNATURE = "&result=15";
    
    public PlacesAPI(LocationSensorManager lsman) {
    	super(lsman, false);
    }
   
}
