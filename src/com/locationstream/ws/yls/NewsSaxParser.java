package com.locationstream.ws.yls;

import java.util.ArrayList;
import java.util.List;

import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Xml;

import com.locationstream.ws.BaseFeedParser;

public class NewsSaxParser extends BaseFeedParser {

	// names of the XML tags
	static final String CHANNEL = "radars";
	static final String PUB_DATE = "published_at";
	static final  String DESCRIPTION = "body";
	static final  String LINK = "url";
	static final  String TITLE = "title";
	static final  String AUTHOR = "author";
	static final  String ITEM = "radar";
	
	static final String RSS = "radars";
	public NewsSaxParser(String feedUrl) {
		super(feedUrl);
	}

	public List<NewsInfo> parse() {
		final NewsInfo currentNewsInfo = new NewsInfo();
		RootElement root = new RootElement(RSS);
		final List<NewsInfo> NewsInfos = new ArrayList<NewsInfo>();
		//Element channel = root.getChild(CHANNEL);
		Element item = root.getChild(ITEM);
		item.setEndElementListener(new EndElementListener(){
			public void end() {
				NewsInfos.add(currentNewsInfo.copy());
			}
		});
		item.getChild(AUTHOR).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body){
				currentNewsInfo.author = body;
			}
		});
		item.getChild(TITLE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentNewsInfo.title = body;
			}
		});
		item.getChild(LINK).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentNewsInfo.url = body;
			}
		});
		item.getChild(DESCRIPTION).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentNewsInfo.body = body;
			}
		});
		item.getChild(PUB_DATE).setEndTextElementListener(new EndTextElementListener(){
			public void end(String body) {
				currentNewsInfo.published_at = body;
			}
		});
		
		
		try {
			Xml.parse(this.getInputStream(), Xml.Encoding.UTF_8, root.getContentHandler());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return NewsInfos;
	}
}
