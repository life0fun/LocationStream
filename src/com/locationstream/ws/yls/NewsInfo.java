package com.locationstream.ws.yls;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Parcel;
import android.os.Parcelable;

import com.locationstream.LocationSensorApp.LSAppLog;

/* App == ContactsContract == Contact Provider 
 * Contact information is stored in a three-tier data model
 * The Data table contains all kinds of personal data: phone numbers,There is a predefined set of common kinds.
 * Data is a generic table that can hold all kinds of data. The kind of data stored in a row is defined by mime type in the row.
 * Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredName.CONTENT_ITEM_TYPE.
 * A row in the RawContacts table represents a set of Data describing a person with a single account.
 * A row in the Contacts table represents an aggregate of one or more RawContacts describing the same person.
 * A Contact cannot be created explicitly. Raw contact inserted first and a row in contact is created or linked.
 * Query: CONTENT_LOOKUP_URI, PhoneLookup.CONTENT_FILTER_URI, CONTENT_FILTER_URI, ContactsContract.Data
 */
public class NewsInfo implements Parcelable, Comparable<NewsInfo>
{
	public static String TAG = NewsInfo.class.getSimpleName();
	
	public static final Parcelable.Creator<NewsInfo> CREATOR = new Parcelable.Creator<NewsInfo>() {
		public NewsInfo createFromParcel(Parcel p) {
			return new NewsInfo(p);
        }

        public NewsInfo[] newArray(int size) {
        	return new NewsInfo[size];
        }
	};
	
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel p, int flags) {
		p.writeString(item_id);
		p.writeString(author);
		p.writeString(title);
		p.writeString(body);
		p.writeString(url);
	}
        
	public NewsInfo(Parcel p) {
		item_id = p.readString();
		author = p.readString();
		title = p.readString();
		body = p.readString();
		url = p.readString();
	}
	
	public NewsInfo copy(){
		NewsInfo copy = new NewsInfo();
		copy.title = title;
		copy.url = url;
		copy.body = body;
		copy.published_at = published_at;
		copy.author = author;
		return copy;
	}
	
	public int compareTo(NewsInfo another) {
        if (another == null) return 1;
        // sort descending, most recent first
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, Locale.US);
        Date another_date = null;
        Date this_date = null;
        try{
        	another_date = df.parse(another.published_at);
        	this_date = df.parse(published_at);
        }catch(Throwable e) {
        	LSAppLog.e(TAG, "compareTo() Date Exception : " + e.toString());
        }
        
        if(another_date != null && this_date != null){
        	return another_date.compareTo(this_date);
        }else{
        	return 1;
        }
	}
		
	public NewsInfo() {
		item_id = null;
		icon_path = null;
		author = null;
		author_url = null;
		published_at = null;
		title = null;
		body = null;
		url = null;
	}
	
	public static byte[] marshall(NewsInfo obj) {
		Parcel p = Parcel.obtain();
		obj.writeToParcel(p, 0);
		return p.marshall();
	}
	
	public static NewsInfo unmarshall(byte[] buffer) {
		if(buffer == null){
			return null;
		}
		Parcel p = Parcel.obtain();
		p.unmarshall(buffer, 0, buffer.length);
		p.setDataPosition(0);
		return NewsInfo.CREATOR.createFromParcel(p);
	}
		
	public String   item_id;
	public String	icon_path;
	public String	author;
	public String 	author_url;
	public String  	published_at;
	public String	title;
	public String	body;
	public String	url;

	public Parcel  mParce;
	public boolean sent;
}
