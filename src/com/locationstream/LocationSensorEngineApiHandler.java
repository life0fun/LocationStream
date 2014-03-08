package com.locationstream;

import java.util.List;

import android.os.RemoteException;

/**
 *<code><pre>
 * CLASS:
 *  implements service API handler Interface. For now, service stub is empty.
 *  This is reserved here for future usage. The Intent filter to bind to this service will be defined later.
 *
 * RESPONSIBILITIES:
 *  Expose Location manager service binder APIs so other components can bind to us.
 *
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public class LocationSensorEngineApiHandler extends ILocationSensorEngine.Stub {

    @SuppressWarnings("unused")
    private LocationSensorManager mManager;


    public LocationSensorEngineApiHandler(LocationSensorManager manager) {
        mManager = manager;    // dependency injection, reserved for future extension.
    }

    public List<LocationSensor> getVirtualSensorList() throws RemoteException {
        return null;
    }

    public LocationSensorState getVirtualSensorState(LocationSensor sensor)
    throws RemoteException {
        return null;
    }

    public int registerListener(LocationSensor sensor, String intentActionString)
    throws RemoteException {
        return 0;
    }

    public int unregisterListener(LocationSensor sensor) throws RemoteException {
        return 0;
    }

}
