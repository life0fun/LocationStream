package com.locationstream;

import com.locationstream.LocationSensor;
import com.locationstream.LocationSensorState;

interface ILocationSensorEngine {
	List<LocationSensor> getVirtualSensorList();
	LocationSensorState getVirtualSensorState(in LocationSensor sensor);
	int registerListener(in LocationSensor sensor, in String intent_action_string);
	int unregisterListener(in LocationSensor sensor);
}