package com.locationstream.dbhelper;

import android.database.Cursor;
import android.util.Log;

public class DbUtil implements DbSyntax {

    private static final String TAG = "db.Util";

    private static final String COMMA = ",";

    /** creates a comma-separated list of values from one column of a cursor.
     *
     * @param cursor - passed cursor
     * @param colName - name of the column containing the value
     * @return - string of comma separated values. If there are no
     * records in the cursor, then an empty string is returned.
     */
    public static String getCommaSeparatedValues(Cursor cursor, final String colName) {

        int colNumber = cursor.getColumnIndex(colName);
        StringBuilder sb = new StringBuilder();

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                sb.append(cursor.getString(colNumber));
                cursor.moveToNext();
                if (!cursor.isAfterLast())
                    sb.append(COMMA);
            }
        }
        return sb.toString();
    }

    /** dumps a cursor, either a subset (when colNames provided) or the full
     * set of colums (when colNames is null).
     *
     * @param prefix - message prefix
     * @param cursor - cursor to dump
     * @param colNames - the column names to be dumped, or null if all to be dumped.
     * If supplied, the cursor will be dumped in the colNames column order.
     * BaseColumns._ID is the _id column
     */
    public static void dumpCursor(final String prefix, Cursor cursor, String[] colNames) {

        int c = 0;
        int row = 0;

        int[] colIx = null;
        // dump col names
        if (colNames != null) {
            // just a subset
            colIx = new int[colNames.length];
            for (int i=1; i<colIx.length; i++) {
                colIx[i] = cursor.getColumnIndex(colNames[i]);
                c++;
            }
        } else {
            // get all
            colIx = new int[cursor.getColumnCount()];
            colNames = new String[colIx.length];
            for (int i=0; i<colIx.length; i++) {
                colNames[i] = cursor.getColumnName(i);
                colIx[i] = cursor.getColumnIndex(colNames[i]);
                c++;
            }
        }

        // dump column names
        for (int i=0; i<colIx.length; i++) {
            Log.i(TAG, prefix+" c("+i+"):"+colNames[i]);
        }

        // dump all the rows
        if (cursor.moveToFirst()) {
            StringBuilder sb = new StringBuilder();
            while (!cursor.isAfterLast()) {
                sb.append("r("+row+"):");
                // dump each column in the column index table
                for (int i=0; i<colIx.length; i++) {
                    sb.append("c("+i+"):"+cursor.getString(colIx[i])+", ");
                }
                Log.i(TAG, prefix+sb.toString());
                cursor.moveToNext();
                row++;
            }
        }
        // control totals
        Log.i(TAG, prefix+" dumped "+c+" cols, "+row+" rows");
    }
}
