package com.locationstream.foursquare;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.foursquare.CheckinParser.Checkin;

public class VenueParser {
	
	public static final String TAG = "API_VENUES";
	protected FoursquareHttpApi mFSApi;  // reference to global FourSquare API
	private String mLat, mLgt;
	private String mJsonData;
	public List<Venue> mVenues;   // to avoid bloated code
	
	public VenueParser(FoursquareHttpApi fsapi, String lat, String lgt){
		mFSApi = fsapi;
		mLat = lat;
		mLgt = lgt;
		mVenues = new ArrayList<Venue>();
	}
	
	public String getVenues(){
		int i,j;
		String venuesstring = null;
		JSONObject venuesjson = null;
		
		try{
			LSAppLog.d(TAG, "getVenues:" + mLat + ":" + mLgt);
			venuesstring = mFSApi.venues(mLat, mLgt, null, null, null, null, 20);
			venuesjson = new JSONObject(venuesstring);
			mJsonData = venuesstring;
			LSAppLog.e(TAG, venuesstring);
			
			JSONArray groups = venuesjson.getJSONArray("groups");
			LSAppLog.e(TAG, groups.toString());
			
			for(i=0;i<groups.length();i++){
				JSONObject venues = groups.getJSONObject(i);
				JSONArray venuesarray = venues.getJSONArray("venues");
				for(j=0;j<venuesarray.length();j++){
					JSONObject vidnames = venuesarray.getJSONObject(j);
					LSAppLog.e(TAG, "VID=" + vidnames.getString("id") + "::name=" + vidnames.getString("name"));
					
					Venue e = new Venue();
					e.mId = vidnames.getString("id");
					e.mName = vidnames.getString("name");
					e.mAddress = vidnames.getString("address");
					
					JSONObject stats = vidnames.getJSONObject("stats");
					e.mStats = new Stats(null, stats.getString("herenow"));
					mVenues.add(e);
				}
			}
		}catch(Exception e){
			LSAppLog.e(TAG, "venues exception " + e.toString());
		}
		
		return venuesstring;
	}
	
	public static class Venue implements Parcelable {
		// to avoid bloated code...publish all fields for now.
		public String mId;
		public String mName;
		public String mAddress;
	    public List<Checkin> mCheckins;
	    public Stats mStats;
	    /* Not used for now
	    //public final String mCity;
	    //public final String mCityid;
	    public final String mCrossstreet;
	    public final String mDistance;
	    public final String mGeolat;
	    public final String mGeolong;
	    public final String mPhone;
	    //private Group<Special> mSpecials;
	    public final String mState;
	    //private Tags mTags;
	    //private Group<Tip> mTips;
	    //private Group<Tip> mTodos;
	    //public final String mTwitter;
	    //public final String mZip;
	    //private Category mCategory;
	     */
	
	    public Venue() {}
	    
	    public Venue(Parcel in) {
	        mId = in.readString();
	        mName = in.readString();
	        mAddress = in.readString();
	       
	        mCheckins = new ArrayList<Checkin>();
	        int numCheckins = in.readInt();
	        for (int i = 0; i < numCheckins; i++) {
	            Checkin checkin = in.readParcelable(Checkin.class.getClassLoader());
	            mCheckins.add(checkin); 
	        }
	       
	        if (in.readInt() == 1) {
	            mStats = in.readParcelable(Stats.class.getClassLoader());
	        }else{
	        	mStats = null;
	        }
	    }
	    
	    public String toString() {
	    	return mId + "::" + mName + "::" + mAddress + "::" + mStats.toString();
	    }
	    
	    public static final Parcelable.Creator<Venue> CREATOR = new Parcelable.Creator<Venue>() {
	        public Venue createFromParcel(Parcel in) {
	            return new Venue(in);
	        }
	
	        public Venue[] newArray(int size) {
	            return new Venue[size];
	        }
	    };
	    
	   
	    public void writeToParcel(Parcel out, int flags) {
	    	out.writeString(mAddress);
	    }

		public int describeContents() {
			return 0;
		}
		
	}
}
