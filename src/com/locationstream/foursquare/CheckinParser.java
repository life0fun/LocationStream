package com.locationstream.foursquare;

import android.os.Parcel;
import android.os.Parcelable;

import com.locationstream.foursquare.VenueParser.Venue;

public class CheckinParser {
	
	public static final String TAG = "Checkin";
	
	private FoursquareHttpApi mFSApi;  // reference to global FourSquare API
	private String mId;
	
	public CheckinParser(FoursquareHttpApi fsapi, String uid){
		mFSApi = fsapi;
		mId = uid;
	}
	
	public static class Checkin implements Parcelable {
	    public String mCreated;
	    public String mDisplay;
	    public String mDistance;
	    public String mId;
	    public boolean mIsmayor;
	    public boolean mPing;
	    public String mShout;
	    //private User mUser;
	    public Venue mVenue;
	
	    public Checkin() {
	        mPing = false;
	    }
	    
	    private Checkin(Parcel in) {
	        mCreated = in.readString();
	        mDisplay = in.readString();
	        mDistance = in.readString();
	        mId = in.readString();
	        mIsmayor = in.readInt() == 1;
	        mPing = in.readInt() == 1;
	        mShout = in.readString();
	        
	        if (in.readInt() == 1) {
	            //mUser = in.readParcelable(User.class.getClassLoader());
	        }
	        
	        if (in.readInt() == 1) {
	            mVenue = in.readParcelable(Venue.class.getClassLoader());
	        }
	    }
	    
	    public static final Parcelable.Creator<Checkin> CREATOR = new Parcelable.Creator<Checkin>() {
	        public Checkin createFromParcel(Parcel in) {
	            return new Checkin(in);
	        }
	
	        
	        public Checkin[] newArray(int size) {
	            return new Checkin[size];
	        }
	    };
	
	        public void writeToParcel(Parcel out, int flags) {
	        out.writeString(mCreated);
	        out.writeString(mCreated);
	        out.writeInt(mIsmayor ? 1 : 0);
	        out.writeInt(mPing ? 1 : 0);
	        out.writeString(mCreated);
	        
	        /*
	        if (mUser != null) {
	            out.writeInt(1);
	            out.writeParcelable(mUser, flags);
	        } else {
	            out.writeInt(0);
	        }
	        */
	        
	        if (mVenue != null) {
	            out.writeInt(1);
	            out.writeParcelable(mVenue, flags);
	        } else {
	            out.writeInt(0);
	        }
	    }
	    
	    public int describeContents() {
	        return 0;
	    }
	}
}