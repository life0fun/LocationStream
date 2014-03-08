package com.locationstream.ws.yls;

import java.util.ArrayList;
import java.util.List;

import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Xml;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.ws.BaseFeedParser;

public class YLSSaxParser extends BaseFeedParser {
	
	static final String ATOM_NAMESPACE = "urn:yahoo:lcl";

	static final String TAG = "YLSSax";
	
	// names of the XML tags
	static final String RSS = "ResultSet";
	static final String CHANNEL = "Result";
	static final  String ITEM = "Result";
	static final  String TITLE = "Title";
	static final  String ADDRESS = "Address";
	static final String CITY = "City";
	static final  String STATE = "State";
	static final String DISTANCE = "Distance";
	static final String PHONE = "Phone";
	static final String URL = "Url";
	static final  String MAPURL = "MapUrl";
	
	
	public YLSSaxParser(String feedUrl) {
		super(feedUrl);
	}

	public List<YLSInfo> parse() {
		final YLSInfo currentYLSInfo = new YLSInfo();
		RootElement root = new RootElement(ATOM_NAMESPACE, RSS);
		final List<YLSInfo> YLSInfos = new ArrayList<YLSInfo>();
		//Element channel = root.getChild(CHANNEL);
		Element item = root.getChild(ATOM_NAMESPACE, ITEM);
		LSAppLog.e(TAG, "parse() : " + item.toString());
		item.setEndElementListener(new EndElementListener(){
			public void end() {
				LSAppLog.e(TAG, "Parse() : End ITEM : " + currentYLSInfo.toString());
				YLSInfos.add(currentYLSInfo.copy());
			}
		});
		item.getChild(ATOM_NAMESPACE, TITLE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.title = body;
				LSAppLog.e(TAG, "Parse() : End TITLE : " + body);
			}
		});
		item.getChild(ATOM_NAMESPACE,ADDRESS).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body){
				currentYLSInfo.address = body;
				LSAppLog.e(TAG, "Parse() : End Address : " + body);
			}
		});
		item.getChild(ATOM_NAMESPACE,CITY).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.city = body;
				LSAppLog.e(TAG, "Parse() : End city : " + body);
			}
		});
		item.getChild(ATOM_NAMESPACE,STATE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.state = body;
			}
		});
		item.getChild(ATOM_NAMESPACE,DISTANCE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.distance = body;
			}
		});
		item.getChild(ATOM_NAMESPACE,PHONE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.phone = body;
				LSAppLog.e(TAG, "Parse() : End Phone : " + body);
			}
		});
		item.getChild(ATOM_NAMESPACE,URL).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.url = body;
			}
		});
		item.getChild(ATOM_NAMESPACE,MAPURL).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentYLSInfo.mapurl = body;
			}
		});
		
		try {
			Xml.parse(this.getInputStream(), Xml.Encoding.UTF_8, root.getContentHandler());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return YLSInfos;
	}
}
