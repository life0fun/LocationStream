package com.locationstream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.dbhelper.LocationDatabase;
import com.locationstream.dbhelper.LocationDatabase.PoiTable.Tuple;


/**
 *<code><pre>
 * CLASS:
 *  This adapter is the only route to access POI information and
 *  implements content observer on Poi configuration changes as well as
 *
 * RESPONSIBILITIES:
 *   The looking up, and update of poi metadata all thru this adapter.
 *
 * COLABORATORS:
 *	POI
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
public class PoiAdapter {
    public static final String TAG = "LSAPP_POI";

    private final Handler mHandler;
    private final ContentResolver mResolver;
    private final PoiObserver mPoiObserver;

    public static final Uri URI = LocationSensorProvider.POI_CONTENT_URI;

    private List<Tuple> mPoiList;  // poi list is looked up constantly....populate it here.

    private static String[] POI_DB_COLUMNS = LocationDatabase.PoiTable.Columns.getNames();

    public PoiAdapter(Context ctx, Handler hdl) {
        mHandler = hdl;
        mResolver = ctx.getContentResolver();
        mPoiObserver = new PoiObserver(mHandler);
        // poi list is published to outside, to ensure thread-safe, wrap it with synchronized collection. Note: it only conditional thread-safe for individual ops, not a batch of ops.
        // http://stackoverflow.com/questions/561671/best-way-to-control-concurrent-access-to-java-collections
        mPoiList = Collections.synchronizedList(new ArrayList<Tuple>());   // never null

        registerPoiObserver();

        refreshPoiList();  // prepare poi list upon start.
    }

    public class PoiObserver extends ContentObserver {
        public PoiObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfUpdate) {
            if (refreshPoiList()) {
                notifyDetectionPoiChanged();
            }
        }
    }

    private void registerPoiObserver() {
        mResolver.registerContentObserver(URI, true, mPoiObserver);
    }
    public void unregisterPoiObserver() {
        mResolver.unregisterContentObserver(mPoiObserver);
    }

    /**
     * callback of content observer whenever user tag a poi in database.
     * return true if number of row has been added or deleted
     */
    private boolean refreshPoiList() {
        int nrows = mPoiList.size();
        Cursor c = mResolver.query(URI, POI_DB_COLUMNS, null, null, null);  // query the entire table
        if (c != null) {
            mPoiList.clear();
            try {
                if (c.moveToFirst()) {
                    do {
                        mPoiList.add(LocationDatabase.PoiTable.toTuple(c));
                        LSAppLog.d(TAG, "refreshPoiList :: " + mPoiList.get(mPoiList.size()-1).toString());
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                LSAppLog.e(TAG, "refreshPoiList Exception: " + e.toString());
            } finally {
                c.close();
            }
        } else {
            LSAppLog.e(TAG, "refreshPoiList :: null cursor from POI content provider");
        }

        LSAppLog.d(TAG, "refreshPoiList :: exist_rows: " +  nrows + " added_rows: " + mPoiList.size());
        
        if (nrows == mPoiList.size()) {
            // no poi add/del, only update of cell and wifi, which caused by detection already. Hence no need to cyclicly inform detection again.
            return false; 
        } else {
            return true;
        }
    }

    /**
     * @return a list of Poi
     */
    public List<Tuple> getPoiList() {
        //return Collections.unmodifiableList(mPoiList);  // outside, read-only
        //return Collections.synchronizedList(mPoiList);
        return mPoiList;
    }

    /**
     * return all poi data[poi, lat, lgt, radius, addr, name, cell] with the poi tage
     * @param poitag
     * @return
     */
    public LocationDatabase.PoiTable.Tuple getPoiEntry(String poitag) {
        if (mPoiList.size() == 0 || poitag == null) {
            return null;
        }
        for (Tuple t : mPoiList) {
            if (t.getPoiName().equals(poitag)) {
                return t;
            }
        }
        return null;
    }

    /**
     * LocationDetection calls with upmerged celljson.
     */
    public boolean updatePoi(LocationDatabase.PoiTable.Tuple poituple) {
        if ( poituple != null) {
            ContentValues value = LocationDatabase.PoiTable.toContentValues(poituple);
            String where = "( " +  LocationDatabase.PoiTable.Columns._ID + " = " + poituple.get_id() +" )";
            mResolver.update(LocationSensorProvider.POI_CONTENT_URI, value, where, null);
            LSAppLog.d(TAG, "updatePoi : entry exist, update value:" + value.toString());
            return true;
        } else {
            LSAppLog.e(TAG, "updatePoi : empty entry : ");
            return false;
        }
    }

    public void notifyDetectionPoiChanged() {
        Message msg = mHandler.obtainMessage();
        msg.what = LocationDetection.Msg.POI_REFRESH;
        LSAppLog.i(TAG, "notifyDetectionPoiChanged...after refresh from POI observer.");
        mHandler.sendMessage(msg);
    }
}
