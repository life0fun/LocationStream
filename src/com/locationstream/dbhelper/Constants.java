package com.locationstream.dbhelper;



public interface Constants {


    /**
      *  SensorManager mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
      *  List<Sensor> listSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
      *
      *  For the Sholes phone:
      *
      *	has sensor=KXTF9 3-axis Accelerometer 			type=1, 	Sensor.TYPE_ACCELEROMETER,  max=40, res=0.0098
      *	has sensor=AK8973 3-axis Magnetic field sensor 	type=2, 	Sensor.TYPE_MAGNETIC_FIELD, max=255
      *	has sensor=Orientation sensor 					type=3, 	Sensor.TYPE_ORIENTATION, 	max 360
      *	has sensor=Ambient Light sensor 				type=5, 	Sensor.TYPE_LIGHT, 			max=27000
      *   has sensor=AK8973 Temperature sensor 			type=7, 	Sensor.TYPE_TEMPERATURE, 	max 115
      *	has sensor=SFH7743 Proximity sensor 			type=8, 	Sensor.TYPE_PROXIMITY, 		max=255
      *	has sensor=Rotation sensor 						type=9, 	SensorM.TYPE_ROTATION, 		max=40
      *	has sensor=Gesture sensor 						type=10, 	SensorM.TYPE_GESTURE, 		max=40
      *	has sensor=IR sensor 							type=16, 	SensorM.TYPE_IR,			max=40
      *
      *  For the Ruth phone:
      *    has sensor=KXTF9 3-axis Accelerometer 			type=1 	maxR=40.0 	res=0.00981 power=1.0 ver=1
      *    has sensor=AK8973 3-axis Magnetic field sensor type=2	maxR=255.0 	res=1.0 power=1.0 ver=1
      *	 has sensor=Orientation sensor 					type=3 	maxR=360.0 	res=1.0 power=1.0 ver=1
      *    has sensor=Ambient Light sensor 				type=5 	maxR=27000.0 res=1.0 power=0.0 ver=1
       * 	 has sensor=AK8973 Temperature sensor 			type=7 	maxR=115.0 	res=1.0 power=1.0 ver=1
     	*	 has sensor=SFH7743 Proximity sensor 			type=8 	maxR=255.0 	res=1.0 power=1.0 ver=1
      */


    public static final String DEGREE_SYMBOL = (char)176+"";


    public static final boolean PRODUCTION_MODE = false;
    public static final boolean LOG_INFO = !PRODUCTION_MODE;
    public static final boolean LOG_DEBUG = !PRODUCTION_MODE;
    public static final boolean LOG_VERBOSE = false;


    public static final String DATABASE_NAME = "locationsensor.db";

    public static final String CONTEXT_NULL 		= "Context cannot be null";
    public static final String UNKNOWN_MESSAGE 		= " unknown message:";
    public static final String TYPE_OUT_OF_RANGE 	= "type is out of range:";
    public static final String INDEX_OUT_OF_RANGE 	= "Index out of range:";
    public static final String CANNOT_BE_NULL 		= "cannot be null";

}
