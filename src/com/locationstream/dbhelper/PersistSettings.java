package com.locationstream.dbhelper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;

import com.locationstream.LocationSensorApp.LSAppLog;


public class PersistSettings {

    private static final String TAG 			= PersistSettings.class.getSimpleName();
    private static final char 	SEP_CHAR 		= 0x01;
    private static final String SEPARATOR 		= SEP_CHAR+"";
    private static final String F_NAME 			= "preferences.txt";


    public enum Column { USER_RULES;

    public static Column convert(int value) {
        return Column.class.getEnumConstants()[value];
    }
    public static String toString(int value) {
        return convert(value).toString();
    }

    public String value(final String[] record, Column col) {
        return record[col.ordinal()];
    }
                       };



    public static int getValueAsInt(Context context, final Column column, int defaultValue) {

        int result = defaultValue;
        try {
            result = Integer.parseInt(readSettings(context)[column.ordinal()]);
        } catch (Exception e) {
            // ignore, just use default value
        }
        return result;
    }


    public static String getValue(Context context, final Column column) {
        return readSettings(context)[column.ordinal()];
    }


    public static void setValue(Context context, final Column column, long value) {

        String[] s = readSettings(context);
        s[column.ordinal()] = value+"";
        saveSettings(context, s);
    }


    /** this is temporary until we start writing the current location to the contacts book
     *
     * @param context
     * @result = lat^long^address - where lat/long are stored as Double
     */
    public static String[] readSettings(final Context context) {


        // this is temporary until we start writing the current location to the contacts book
        String[] result = null;
        FileInputStream f = null;

        try {
            f = context.openFileInput(F_NAME);

            byte[] buffer = new byte[1000];
            int len = f.read(buffer, 0, buffer.length);

            if (len > 0) {
                String s = new String(buffer, 0, len);
                result = s.split(SEPARATOR);
            }

        } catch (FileNotFoundException e) {

            LSAppLog.e(TAG,"File uninitialized");
            result = new String[Column.class.getEnumConstants().length];
            for (int i=0; i<result.length; i++) {
                result[i] = "";
            }

        } catch (IOException e) {

            e.printStackTrace();
        }

        try {
            if (f != null)
                f.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }


    /** this writes the current location to the contacts book. - temporary until we move over the database
     *
     * @param context
     * @param tuple - the Tuple of values to write
     */
    public static void saveSettings(final Context context, final String[] settings) {

        // this is temporary until we start writing the current location to the contacts book

        FileOutputStream f = null;

        try {
            f = context.openFileOutput(F_NAME, Context.MODE_WORLD_WRITEABLE);

            StringBuffer buffer = new StringBuffer();

            for (int i=0; i<settings.length; i++) {
                buffer.append(settings[i]);
            }
            f.write(buffer.toString().getBytes());

            f.flush();

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (f != null)
                f.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
