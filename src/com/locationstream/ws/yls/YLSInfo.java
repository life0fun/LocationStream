package com.locationstream.ws.yls;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;


public class YLSInfo implements Parcelable, Comparable<YLSInfo>
{
	public static String TAG = "YLSInfo"; 
	
	public static final Parcelable.Creator<YLSInfo> CREATOR = new Parcelable.Creator<YLSInfo>() {
		public YLSInfo createFromParcel(Parcel p) {
			return new YLSInfo(p);
        }

        public YLSInfo[] newArray(int size) {
        	return new YLSInfo[size];
        }
	};
	
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel p, int flags) {
		p.writeString(title);
		p.writeString(address);
		p.writeString(city);
		p.writeString(state);
		p.writeString(distance);
		p.writeString(phone);
		p.writeString(rating);
		p.writeString(url);
		p.writeString(mapurl);
	}
        
	public YLSInfo(Parcel p) {
		title = p.readString();
		address = p.readString();
		city = p.readString();
		state = p.readString();
		distance = p.readString();
		phone = p.readString();
		rating = p.readString();
		url = p.readString();
		mapurl = p.readString();
	}
	
	public YLSInfo copy(){
		YLSInfo copy = new YLSInfo();
		copy.title = title;
		copy.address = address;
		copy.city = city;
		copy.state = state;
		copy.distance = distance;
		copy.phone = phone;
		copy.rating = rating;
		copy.url = url;
		copy.mapurl = mapurl;
		return copy;
	}
	
	public int compareTo(YLSInfo another) {
        if (another == null) return 1;
        // sort descending, most recent first
        
        return distance.compareTo(another.distance);       
	}
		
	public YLSInfo() {
		title = null;
		address = null;
		city = null;
		state = null;
		distance = null;
		phone = null;
		rating = null;
		mapurl = null;
		url = null;
	}
	
	public static byte[] marshall(YLSInfo obj) {
		Parcel p = Parcel.obtain();
		obj.writeToParcel(p, 0);
		return p.marshall();
	}
	
	public static YLSInfo unmarshall(byte[] buffer) {
		if(buffer == null){
			return null;
		}
		Parcel p = Parcel.obtain();
		p.unmarshall(buffer, 0, buffer.length);
		p.setDataPosition(0);
		return YLSInfo.CREATOR.createFromParcel(p);
	}
	
	public String	title;
	public String   address;
	public String	city;
	public String	state;
	public String   distance;
	public String 	phone;
	public String 	rating;
	public String 	url;
	public String 	mapurl;
}
