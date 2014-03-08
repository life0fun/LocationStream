package com.locationstream;

import static com.locationstream.Constants.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.format.DateUtils;

import com.locationstream.LocationGraph.LocGraphTable.Tuple;
import com.locationstream.LocationSensorApp.LSAppLog;
import com.locationstream.dbhelper.DbSyntax;
import com.locationstream.dbhelper.LocationDatabase;

/**
*<code><pre>
* CLASS:
* 	This class store and represent location connectivity graph by connecting locations.
* 	Further data mining can reveal interesting insights on space-time correlations:
* 	  1. why people go to the location
* 	  2. when people go the the location
*
* RESPONSIBILITIES:
*   Store and represent location graph with JSON store.
*
* COLABORATORS:
*
* USAGE:
* 	See each method.
*
*</pre></code>
*/

/*
 * The concept and the class is still under experimental, do not use it for now.
 */
public class LocationGraph {
    private static final String TAG = "LSAPP_Graph";
    private static final Double BOX = 0.002;

    /* JSON keys */
    public static final String LOC_GRAPH_NODE = "GraphNode";
    public static final String LOC_GRAPH_SRC = "GraphSource";
    public static final String LOC_GRAPH_DST = "GraphDest";
    public static final String LOC_GRAPH_WEIGHT = "GraphWeight";
    public static final String LOC_LAT = "Latitude";
    public static final String LOC_LNG = "Longitude";
    public static final String LOC_SRCNAME = "SrcName";
    public static final String LOC_DSTNAME = "DstName";
    public static final String LOC_DISTANCE = "Distance";
    public static final String LOC_LEFTTIME = "LeftTime";
    public static final String LOC_ARRIVALTIME = "ArrivalTime";

    private Context mContext;
    private GraphDatabase mGDb;
    public double mCurLat, mCurLng;

    public LocationGraph(Context c) {
        mContext = c;
        mGDb = new GraphDatabase(c);
    }

    public static class Destination {
        public String mSrcName;
        public String mDstName;
        public int mDistance;
        public int mCount;
        public int mTraveltime[]; // best worst, avg

        public Destination(String src, String dst, int distance, int[] times) {
            mSrcName = src;
            mDstName = dst;
            mDistance = distance;
            mCount = 1;
            mTraveltime = times;
        }

        public void updateTravelTime(int time) {
            int besttime = mTraveltime[0];
            int worsttime = mTraveltime[1];
            int avgtime = mTraveltime[2];
            if (time < besttime)
                besttime = time;
            if (time > worsttime)
                worsttime = time;
            avgtime = (avgtime*mCount + time) / (mCount+1);
            mTraveltime[0] = besttime;
            mTraveltime[1] = worsttime;
            mTraveltime[2] = avgtime;
            mCount += 1;
        }
    }

    public void displayDestinations() {
        //mContext.startActivity(new Intent(mContext, LocationGraphActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * loop thru location discovery table chronologically, and dig out sequential relationship in time between two locations,
     * this means the user left first location, and reached the second location, at time X. the second location can be
     * viewed as the destination of the first location at time X.
     */
    public void addLastEdge() {
        List<LocationDatabase.LocTimeTable.Tuple> loclist = new ArrayList<LocationDatabase.LocTimeTable.Tuple>();
        int locs = findGraphLocations(loclist, System.currentTimeMillis() - (30*DateUtils.DAY_IN_MILLIS), "_ID DESC ", 2);
        if (locs >= 2) {
            LocationDatabase.LocTimeTable.Tuple dstt = loclist.get(0);
            LocationDatabase.LocTimeTable.Tuple srct = loclist.get(1);
            mCurLat = dstt.getLat();
            mCurLng = dstt.getLgt();
            mGDb.addGraphEdge(srct.getLat(), srct.getLgt(), srct.getAccuName(), dstt.getLat(), dstt.getLgt(), dstt.getAccuName(), srct.getEndTime(), dstt.getStartTime());
        }
    }

    // not used for now
    private void addDestination(double srclat, double srclng, String srcname, double dstlat, double dstlng, String dstname, long lefttime, long arrivaltime) {
        mGDb.addGraphEdge(srclat,srclng, srcname, dstlat, dstlng, dstname, lefttime, arrivaltime);
    }

    /**
     * predict what are the possible destinations from current location based on users location history.
     */
    public String predictNextDestinations() {
        Map<String, Destination> destmap = getDestinations(mCurLat, mCurLng);
        StringBuilder sb = new StringBuilder();
        sb.append("Are you going to any of the following place ?\n");
        for (Entry<String, Destination> entry : destmap.entrySet()) {
            sb.append(entry.getValue().mDstName);
            sb.append(" traveling time about ");
            sb.append(entry.getValue().mTraveltime[0]);
            sb.append("\n");
        }
        return sb.toString();
    }

    private Map<String, Destination> getDestinations(double lat, double lng) {
        Map<String, Destination> destmap = new HashMap<String, Destination>();
        JSONArray jsonarr = null;
        JSONObject entry = null;
        List<LocGraphTable.Tuple> edges = mGDb.findAllEdges(lat, lng);
        for (Tuple t: edges) {
            try {
                String jsonstr = t.getValJson();
                jsonarr = new JSONArray(jsonstr);

                int size = jsonarr.length();
                String srcname, dstname;
                int time, distance;
                for (int i=0; i<size; i++) {
                    entry = jsonarr.getJSONObject(i);
                    srcname = entry.getString(LOC_SRCNAME);
                    dstname = entry.getString(LOC_DSTNAME);
                    distance = (int)entry.getLong(LOC_DISTANCE);
                    time = (int)(entry.getLong(LOC_LEFTTIME) - entry.getLong(LOC_ARRIVALTIME))/(60*(int)1E6);
                    Destination d = null;
                    if (!destmap.containsKey(dstname)) {
                        int[] times = new int[3];
                        times[0] = times[1] = times[2] = time;
                        d = new Destination(srcname, dstname, distance, times);
                        destmap.put(dstname, d);
                    } else {
                        d = destmap.get(dstname);
                        d.updateTravelTime(time);
                    }
                }
            } catch (JSONException e) {
                LSAppLog.e(TAG, "getDestinations:" + e.toString());
                return destmap;
            }
        }
        return destmap;
    }

    /**
     * From cloud, fetch the real-time traffic information  between two locations.
     */
    private JSONObject trafficInfo(JSONObject currentLocation, JSONObject nextLocation) {
        URL trafficUrl;
        byte[] b = new byte[2048];

        try {
            trafficUrl = new URL("http://http://maps.google.com/fake_traffic_feed");
            //trafficUrl.openConnection().getInputStream().read(b);
        } catch (MalformedURLException e) {
            LSAppLog.e(TAG, e.toString());
        } catch (IOException ioe) {
            LSAppLog.e(TAG, ioe.toString());
        }
        try {
            return new JSONObject(new String(b));
        } catch (JSONException e) {
            LSAppLog.e(TAG, e.toString());
            return null;
        }
    }

    /**
     * Aggregate all of the recommendation of the location, (coupons, events, activities...)
     */
    public JSONObject getRecommendations(JSONObject location) {
        URL LocalUrl;
        return null;
    }

    /**
     *  Document oriented store for graph
     */
    public static class GraphDatabase {
        protected final Context mContext;
        protected GraphDBHelper mHelper;
        protected SQLiteDatabase mDb;

        public GraphDatabase(Context c) {
            mContext = c;
            mHelper = new GraphDBHelper(c);
            mDb = mHelper.getWritableDatabase();   // this one will create database.
            LSAppLog.d(TAG, "GraphDatabase constructor:");
        }


        public LocGraphTable.Tuple findEdge(LocGraphTable.Tuple t) {
            LocGraphTable.Tuple edge = null;
            String where = "( " +  LocGraphTable.Columns.SRCNAME + " = ? and " + LocGraphTable.Columns.DSTNAME + " = ? )";
            Cursor c = mDb.query(LocGraphTable.TABLE_NAME, null, where, new String[] {t.getSrcName(), t.getDstName()}, null, null, null);
            try {
                if (c.moveToFirst()) {
                    edge = LocGraphTable.toTuple(c);
                }
            } catch (Exception e) {
                LSAppLog.d(TAG, e.toString());
            } finally {
                c.close();
            }
            return edge;
        }

        /**
         * find all the graph edges from the location. i.e., all the dest from the locations.
         * @param t
         * @return
         */
        public List<Tuple> findAllEdges(double lat, double lng) {
            LSAppLog.d(TAG, "findAllEdges: " + lat + " " + lng);
            Tuple edge = null;
            List<LocGraphTable.Tuple> edges = new ArrayList<LocGraphTable.Tuple>();
            // the box with +- offset in both lat and lng
            String where = "( " +  LocGraphTable.Columns.SRCLAT + " >= " + (lat - BOX) + " and " +
                           LocGraphTable.Columns.SRCLAT + " <= " + (lat + BOX) + " and " +
                           LocGraphTable.Columns.SRCLGT + " >= " + (lng - BOX) + " and " +
                           LocGraphTable.Columns.SRCLGT + " <= " + (lng + BOX) +
                           ")";

            Cursor c = mDb.query(LocGraphTable.TABLE_NAME, null, where, null, null, null, null);
            try {
                if (c.moveToFirst()) {
                    do {
                        edge = LocGraphTable.toTuple(c);
                        edges.add(edge);
                        LSAppLog.d(TAG, "findAllEdges: "+edge.toString());
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                LSAppLog.d(TAG, e.toString());
            } finally {
                c.close();
            }
            LSAppLog.d(TAG, "findAllEdges: " + edges.toString());
            return edges;
        }

        private void insertEdge(Tuple t) {
            ContentValues values = new ContentValues();
            values.put(LocGraphTable.Columns.SRCLAT, t.getSrcLat());
            values.put(LocGraphTable.Columns.SRCLGT, t.getSrcLgt());
            values.put(LocGraphTable.Columns.SRCNAME, t.getSrcName());

            values.put(LocGraphTable.Columns.DSTLAT, t.getDstLat());
            values.put(LocGraphTable.Columns.DSTLGT, t.getDstLgt());
            values.put(LocGraphTable.Columns.DSTNAME, t.getDstName());
            values.put(LocGraphTable.Columns.VALJSON, t.getValJson());  // json array's tostring

            LSAppLog.d(TAG, "insertEdge: " + values.toString());
            mDb.insertWithOnConflict(LocGraphTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        private void updateEdge(Tuple edges, JSONObject valjson) {
            JSONArray edgesjson = null;
            try {
                edgesjson = new JSONArray(edges.getValJson());  // convert string back to json array
            } catch (JSONException e) {
                LSAppLog.d(TAG, "updateEdge:" + e.toString());
                return;   // return upon failed to create object.
            }

            if (edgesjson != null && valjson != null) {
                edgesjson.put(valjson);
            }

            ContentValues values = new ContentValues();
            String where = "( " +  LocGraphTable.Columns.SRCNAME + " = ? and " + LocGraphTable.Columns.DSTNAME + " = ? )";
            values.put(LocGraphTable.Columns.VALJSON, edgesjson.toString());
            mDb.update(LocGraphTable.TABLE_NAME, values, where, new String[] {edges.getSrcName(), edges.getDstName()});
        }

        public void addGraphEdge(double srclat, double srclgt, String srcname, double dstlat, double dstlgt, String dstname, long lefttime, long arrivaltime) {
            if (Utils.distanceTo(srclat, srclgt, dstlat, dstlgt) < 200 || dstname.equals(srcname)) {
                return;   // the two locations are close to each other, measurement offset.
            }
            Tuple t = new Tuple(srclat, srclgt, srcname, dstlat, dstlgt, dstname);
            JSONObject valjson = t.setValJson(lefttime, arrivaltime);  // val json string set
            Tuple edge = findEdge(t);
            if (edge == null) {
                insertEdge(t);
            } else {
                updateEdge(edge, valjson);
            }
        }
    }

    public static class GraphDBHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "locgraph.db";
        private static final int DATABASE_VERSION = 1;

        protected GraphDBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            LSAppLog.d(TAG, "GraphDBHelper constructor:");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            LSAppLog.d(TAG, "creating table:" + LocGraphTable.CREATE_TABLE_SQL);
            // create tables upon creation
            db.execSQL(LocGraphTable.CREATE_TABLE_SQL);
            db.setMaximumSize(DATABASE_SIZE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            return;
        }
    }

    public static class LocGraphTable implements com.locationstream.dbhelper.DbSyntax { 
        public static final String TABLE_NAME 	= "locgraph";
        /** SQL statement to create the Friend Table */
        public static final String CREATE_TABLE_SQL =
            CREATE_TABLE +
            TABLE_NAME + " (" +
            Columns._ID					+ PKEY_TYPE			+ CONT +
            Columns.SRCLAT					+ REAL_TYPE			+ CONT+
            Columns.SRCLGT					+ REAL_TYPE			+ CONT+
            Columns.SRCNAME					+ TEXT_TYPE			+ CONT+
            Columns.DSTLAT					+ REAL_TYPE			+ CONT+
            Columns.DSTLGT					+ REAL_TYPE			+ CONT+
            Columns.DSTNAME					+ TEXT_TYPE			+ CONT+
            Columns.VALJSON     			+ TEXT_TYPE			+
            ")";

        public static class Columns {
            public final static String _ID      		= "_id";
            public final static String SRCLAT			= "SrcLat";
            public final static String SRCLGT			= "SrcLgt";
            public final static String SRCNAME 			= "SrcName";
            public final static String DSTLAT			= "DstLat";
            public final static String DSTLGT			= "DstLgt";
            public final static String DSTNAME 			= "DstName";
            public final static String VALJSON 			= "ValJson";
        }

        public static Tuple toTuple(Cursor cursor) {
            int ix = 0;

            Tuple tuple = new Tuple(
                cursor.getLong(ix++), 			//_id
                cursor.getDouble(ix++),  		// lat
                cursor.getDouble(ix++),  		// lgt
                cursor.getString(ix++),  		// srcname
                cursor.getDouble(ix++),  		// lat
                cursor.getDouble(ix++),  		// lgt
                cursor.getString(ix++),  		// dstname
                cursor.getString(ix++)  		// valjson
            );

            return tuple;
        }

        public static class Tuple implements Comparable<Tuple> {
            private   long	    id;
            private   double	srclat;
            private	  double	srclgt;
            private   String 	srcname;
            private   double	dstlat;
            private	  double	dstlgt;
            private   String 	dstname;
            private   String	valjson;

            private Tuple() {}

            public Tuple(final long _id, final double srclat, final double srclgt, final String srcname,
                         final double dstlat, final double dstlgt, final String dstname, final String valjson) {
                this.id = _id;
                this.srclat = srclat;
                this.srclgt = srclgt;
                this.srcname = srcname;
                this.dstlat = dstlat;
                this.dstlgt = dstlgt;
                this.dstname = dstname;
                this.valjson = valjson;
            }
            public Tuple(final double srclat, final double srclgt, final String srcname,
                         final double dstlat, final double dstlgt, final String dstname) {
                this.srclat = srclat;
                this.srclgt = srclgt;
                this.srcname = srcname;
                this.dstlat = dstlat;
                this.dstlgt = dstlgt;
                this.dstname = dstname;
            }

            public double getSrcLat() {
                return srclat;
            }
            public double getSrcLgt() {
                return srclgt;
            }
            public String getSrcName() {
                return srcname;
            }
            public double getDstLat() {
                return dstlat;
            }
            public double getDstLgt() {
                return dstlgt;
            }
            public String getDstName() {
                return dstname;
            }
            public String getValJson() {
                return valjson;
            }
            public void setValJson(String s) {
                this.valjson = s;
            }

            /**
             * set the current edge's json value and return the json object
             */
            public JSONObject setValJson(long lefttime, long arrivaltime) {
                JSONArray edges = new JSONArray();
                JSONObject jsonobj = new JSONObject();
                try {
                    jsonobj.put(LOC_SRCNAME, this.getSrcName());
                    jsonobj.put(LOC_DSTNAME, this.getDstName());
                    jsonobj.put(LOC_LEFTTIME, lefttime);
                    jsonobj.put(LOC_ARRIVALTIME, arrivaltime);
                    jsonobj.put(LOC_DISTANCE, Utils.distanceTo(this.getSrcLat(), this.getSrcLgt(), this.getDstLat(), this.getDstLgt()));
                } catch (JSONException e) {
                    LSAppLog.e(TAG, "setValJson() Error: " + e.toString());
                }
                edges.put(jsonobj);  // append this json obj to the end of json array
                this.valjson = edges.toString();
                return jsonobj;
            }

            @Override
            public int hashCode() {
                return (int)(this.srclat * 1E7 + this.srclgt * 1E6 + this.dstlat*1E7 + this.dstlgt*1E6);
            }

            @Override  // override equal and hashcode together
            public boolean equals(Object o) {
                if ( this == o ) return true;
                if ( !(o instanceof Tuple) ) return false;

                if (1 == compareTo((Tuple)o)) // 1 means equals
                    return true;
                else
                    return false;
            }
            public int compareTo(Tuple another) {
                return ! Utils.compareDouble(this.srclat, another.getSrcLat()) && ! Utils.compareDouble(this.srclgt, another.getSrcLgt()) &&
                       ! Utils.compareDouble(this.dstlat, another.getDstLat()) && ! Utils.compareDouble(this.dstlgt, another.getDstLgt()) ? 1 : 0;
            }
        }
    }

    private int findGraphLocations(List<LocationDatabase.LocTimeTable.Tuple> loclist, long since, String orderby, int limit) {
        String where = "( " +  LocationDatabase.LocTimeTable.Columns.STARTTIME + " >= " + since + " )";
        int count = 0;

        Cursor c = mContext.getContentResolver().query(LocationSensorProvider.LOCTIME_CONTENT_URI, LocationDatabase.LocTimeTable.Columns.getNames(), where, null, orderby);
        try {
            if (c.moveToFirst()) {
                do {
                    LocationDatabase.LocTimeTable.Tuple t = LocationDatabase.LocTimeTable.toTuple(c);
                    loclist.add(t);
                    LSAppLog.d(TAG, "findGraphLocations :" + t.getLat() + " ::" + t.getLgt() + " ::" + t.toString());
                    count ++;
                } while (c.moveToNext() && (limit == 0 || count < limit));
            } else {
                LSAppLog.d(TAG, "findGraphLocations : Empty Loc talbe : " + where);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
        return loclist.size();
    }


    /**
     * build location graph if first time use.
     */
    public void buildGraphIfEmpty() {
        Cursor c = mGDb.mDb.query(LocGraphTable.TABLE_NAME, null, null, null, null, null, null);
        boolean empty = false;
        try {
            if (!c.moveToFirst()) {
                empty = true;
            }
        } catch (Exception e) {
            LSAppLog.d(TAG, e.toString());
        } finally {
            c.close();
        }

        if (empty) {
            buildGraph();
        }
    }

    public void buildGraph() {
        List<LocationDatabase.LocTimeTable.Tuple> loclist = new ArrayList<LocationDatabase.LocTimeTable.Tuple>();
        int locs = findGraphLocations(loclist, System.currentTimeMillis() - (30*DateUtils.DAY_IN_MILLIS), "_ID ASC ", 0);

        for (int i=0,j=i+1; i<locs-1 && j<locs; i+=2) {
            LocationDatabase.LocTimeTable.Tuple srct, dstt;
            srct = loclist.get(i);
            dstt = loclist.get(j);
            mGDb.addGraphEdge(srct.getLat(), srct.getLgt(), srct.getAccuName(), dstt.getLat(), dstt.getLgt(), dstt.getAccuName(), srct.getEndTime(), dstt.getStartTime());
        }
    }
}
