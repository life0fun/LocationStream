package com.locationstream.foursquare;

import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

import com.locationstream.foursquare.CheckinParser.Checkin;
import com.locationstream.foursquare.VenueParser.Venue;

public class UserParser {
	
	public static final String TAG = "User";
	private FoursquareHttpApi mFSApi;  // reference to global FourSquare API
	private String mId;
	private String mJsonData;
	protected List<Venue> mVenues;
	
	public UserParser(FoursquareHttpApi fsapi, String id){
		mFSApi = fsapi;
		mId = id;
	}
	
	public String getUser() {
		return mJsonData;
	}

	public static class User implements Parcelable {
		public String mId;
	    public String mLastname;
	    public String mPhone;
	    public String mEmail;
	    public String mFacebook;
	    public String mFirstname;
	    public Checkin mCheckin;
	    public String mCreated;
	    public String mFriendstatus;
	    public String mGender;
	    public int mMayorCount;
	    public String mPhoto;
	    //private Settings mSettings;
	    //private Types mTypes;
	    public String mTwitter;
	    //private Group<Venue> mMayorships;
	    //private Group<Badge> mBadges;
		
	    public User() {
	    }
	
	    private User(Parcel in) {
	        mCreated = in.readString();
	        mEmail = in.readString();
	        mFacebook = in.readString();
	        mId = in.readString();
	        mLastname = in.readString();
	        mPhone = in.readString();
	        mPhoto = in.readString();
	        mTwitter = in.readString();
	        
	        if (in.readInt() == 1) {
	            mCheckin = in.readParcelable(Checkin.class.getClassLoader());
	        }
	        
	    }
	    
	    public static final User.Creator<User> CREATOR = new Parcelable.Creator<User>() {
	        public User createFromParcel(Parcel in) {
	            return new User(in);
	        }
	
	        public User[] newArray(int size) {
	            return new User[size];
	        }
	    };
	
	    public void writeToParcel(Parcel out, int flags) {
	    	out.writeString(mCreated);
	    	out.writeString(mEmail);
	    	
	        if (mCheckin != null) {
	            out.writeInt(1);
	            out.writeParcelable(mCheckin, flags);
	        } else {
	            out.writeInt(0);
	        }
	    }
	
	    public int describeContents() {
	        return 0;
	    }
	}
}
