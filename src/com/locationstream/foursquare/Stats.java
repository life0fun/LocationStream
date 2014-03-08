package com.locationstream.foursquare;

import android.os.Parcel;
import android.os.Parcelable;

public class Stats implements Parcelable {

    //private Beenhere mBeenhere;
    public String mCheckins;
    public String mHereNow;
    //private Mayor mMayor;

    public Stats() {
    }
    
    public Stats(String checkins, String herenow){
    	mCheckins = checkins;
    	mHereNow = herenow;
    }
    
    private Stats(Parcel in) {
        mCheckins = in.readString();
        mHereNow = in.readString();
        //mBeenhere = in.readParcelable(Beenhere.class.getClassLoader());
        //mMayor = in.readParcelable(Mayor.class.getClassLoader());
    }

    public String toString() {
    	return  mHereNow;
    }
    
    public static final Parcelable.Creator<Stats> CREATOR = new Parcelable.Creator<Stats>() {
        public Stats createFromParcel(Parcel in) {
            return new Stats(in);
        }

        public Stats[] newArray(int size) {
            return new Stats[size];
        }
    };

    
    public void writeToParcel(Parcel out, int flags) {
        //out.writeParcelable(mBeenhere, flags);
        out.writeString(mCheckins);
        out.writeString(mHereNow);
        //out.writeParcelable(mMayor, flags);
    }

    public int describeContents() {
        return 0;
    }
}
