/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sjtu.omnilab.omniperf.core;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * The system defaults.
 */
public interface Config {
	/** Constants for controlling software global behaviors */
	public static final boolean DEFAULT_START_ON_BOOT = false;
	public static final boolean MLABNS_SERVICE_ENABLED = false;
	
	//**********************************************
	// Active measurement settings
	//**********************************************

	/** Constants used in various measurement tasks */
	public static final float RESOURCE_UNREACHABLE = Float.MAX_VALUE;
	public static final int PING_COUNT_PER_MEASUREMENT = 10;
	public static final int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;

	// Default interval in seconds between system measurements of a given
	// measurement type
	public static final double DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC = 15 * 60;
	// Default interval in seconds between user measurements of a given
	// measurement type
	public static final double DEFAULT_USER_MEASUREMENT_INTERVAL_SEC = 5;
	// Default value for the '-i' option in the ping command
	public static final double DEFAULT_INTERVAL_BETWEEN_ICMP_PACKET_SEC = 0.5;

	public static final float PING_FILTER_THRES = (float) 1.4;
	public static final int MAX_CONCURRENT_PING = 3;
	// Default # of pings per hop for traceroute
	public static final int DEFAULT_PING_CNT_PER_HOP = 3;
	public static final int HTTP_STATUS_OK = 200;
	public static final int THREAD_POOL_SIZE = 1;
	public static final int MAX_TASK_QUEUE_SIZE = 100;
	public static final long MARGIN_TIME_BEFORE_TASK_SCHEDULE = 500;
	public static final long SCHEDULE_POLLING_INTERVAL = 500;
	public static final String INVALID_IP = "";

	/** Constants used in MeasurementScheduler.java */
	// The default checkin interval in seconds
	public static final long DEFAULT_CHECKIN_INTERVAL_SEC = 60 * 60L;
	public static final long MIN_CHECKIN_RETRY_INTERVAL_SEC = 20L;
	public static final long MAX_CHECKIN_RETRY_INTERVAL_SEC = 600L;		// slower auto checkin (chen)
	public static final int MAX_CHECKIN_RETRY_COUNT = 3;
	public static final long PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC = 2 * 1000L;
	// default minimum battery percentage to run measurements
	public static final int DEFAULT_BATTERY_THRESH_PRECENT = 80;
	public static final boolean DEFAULT_MEASURE_WHEN_CHARGE = true;
	public static final long MIN_TIME_BETWEEN_MEASUREMENT_ALARM_MSEC = 3 * 1000L;

	/** Constants used in BatteryCapPowerManager.java */
	/** The default battery level if we cannot read it from the system */
	public static final int DEFAULT_BATTERY_LEVEL = 0;
	/** The default maximum battery level if we cannot read it from the system */
	public static final int DEFAULT_BATTERY_SCALE = 100;
	/**
	 * Tasks expire in one day. Expired tasks will be removed from the scheduler
	 */
	public static final long TASK_EXPIRATION_MSEC = 24 * 3600 * 1000;
	
	/* Preferences keys */
	/** Running state of measure schedular service */
    public static final String PREF_KEY_IS_SCHEDULER_STARTED = "PREF_KEY_IS_SCHEDULER_STARTED";
	public static final String PREF_KEY_SYSTEM_CONSOLE = "PREF_KEY_SYSTEM_CONSOLE";
	public static final String PREF_KEY_STATUS_BAR = "PREF_KEY_STATUS_BAR";
	public static final String PREF_KEY_SYSTEM_RESULTS = "PREF_KEY_SYSTEM_RESULTS";
	public static final String PREF_KEY_USER_RESULTS = "PREF_KEY_USER_RESULTS";
	public static final String PREF_KEY_COMPLETED_MEASUREMENTS = "PREF_KEY_COMPLETED_MEASUREMENTS";
	public static final String PREF_KEY_FAILED_MEASUREMENTS = "PREF_KEY_FAILED_MEASUREMENTS";
	public static final String PREF_KEY_CONSENTED = "PREF_KEY_CONSENTED";
	public static final String PREF_KEY_ACCOUNT = "PREF_KEY_ACCOUNT";
	public static final String PREF_KEY_SELECTED_ACCOUNT = "PREF_KEY_SELECTED_ACCOUNT";
	
	/** Constants used in MeasurementMonitorActivity.java */
	public static final int MAX_LIST_ITEMS = 128;

	public static final int INVALID_PROGRESS = -1;
	public static final int MAX_PROGRESS_BAR_VALUE = 100;
	
	/** A progress greater than MAX_PROGRESS_BAR_VALUE indicates the end of the measurement */
	public static final int MEASUREMENT_END_PROGRESS = MAX_PROGRESS_BAR_VALUE + 1;
	public static final int DEFAULT_USER_MEASUREMENT_COUNT = 1;

	public static final int MAX_USER_MEASUREMENT_COUNT = 10;

	public static final long MIN_CHECKIN_INTERVAL_SEC = 3600;
	

	//**********************************************
	// Data collector settings
	//**********************************************
	
	/** Not Applicable */
	public static String NOT_APPLICABLE = "NA";
	
	/** Empty String */
	public static String EMPTY_STRING = "";
	
	/** tcpdump instance settings */
	public static final String TCPDUMP_EXE = "tcpdump";
	public static final int TCPDUMP_SERVER_PORT = 40999;
	public static final String TCPDUMP_PROCESS_NAME = "tcpdump";
	public static final String TCPDUMP_OUTPUT_FILE = "traffic.cap";
	public static final String TCPDUMP_PNAME = "sjtu.omnilab.omniperf";
	public static final String OMNIPERF_PACKAGE_NAME = "sjtu.omnilab.omniperf";
	
	/** Minimum SD card space required (2 MB) before start of the trace */
	public static final int SDCARD_MIN_SPACEKBYTES = 2048;
	/**
	 * Omniperf passive data collector output files.
	 * We make them comparable with ARO data format to leverage ARO analyzer utility.
	 * http://developer.att.com/developer/forward.jsp?passedItemId=9700312
	 * Available on 2013-11-10 
	 */
	/** User touch screen trace file name */
	public static final String OutTouchScreenFileName = "user_touch_screen";
	/** Wifi trace file name */
	public static final String OutWifiFileName = "wifi_events";
	/** battery trace file name */
	public static final String OutBatteryFileName = "battery_events";
	/** Gps event trace file name */
	public static final String OutGPSFileName = "gps_events";
	/** Radio event trace file name */
	public static final String OutRadioFileName = "radio_events";
	/** camera event trace file name */
	public static final String OutCameraFileName = "camera_events";
	/** Bluetooth event trace file name */
	public static final String OutBluetoothFileName = "bluetooth_events";
	/** screen event trace file name */
	public static final String OutScreenFileName = "screen_events";	
	/** network bearer event trace file name */
	public static final String OutNetworkDetailsFileName = "network_details";
	/** device ip address trace file name */
	public static final String OutDeviceInfoFileName = "device_info";
	/** device ip address,make ,model trace file name */
	public static final String OutDeviceDetailsFileName = "device_details";
	/** active running process trace file name */
	public static final String OutActiveProcessFileName = "active_process";
	/** screen event trace file name */
	public static final String OutScreenRotationFileName = "screen_rotations";	
	/** cpu event file name */
	public static final String OutCpuFileName = "cpu";
	
	/* Constants for timers */
	/** Constants for the splash screen */
	public static final long SPLASH_SCREEN_DURATION_MSEC = 1500;
	
	/** Constants for data collector watch timer */
	public static final long DATA_COLLECTOR_START_WATCH_TIME = 10000;	// 10 seconds
	public static final long DATA_COLLECTOR_START_TICK_TIME = 1000;		// 1 seconds
	
	/** Constants for Camera/GPS/Screen trace timer repeat time value to capture camera events. */
	public static int HALF_SECOND_TARCE_TIMER_REPATE_TIME = 1000;
	
	/** Timer value to check SD Card space during trace cycle every 5 seconds */
	public static int SDCARD_TARCE_TIMER_REPATE_TIME = 5000;
	
	/** Timer value to check Airplane mode enabled during trace cycle every 1 second */
	public static int AIRPLANE_TARCE_TIMER_REPATE_TIME = 1000;
	
	/* Running states maybe utilized in the future */
	/** Pause state of measurements in preference. */
    public static final String PREF_KEY_IS_PAUSE_REQUESTED = "PREF_KEY_IS_PAUSE_REQUESTED";
    /** Stop state of measures */
    public static final String PREF_KEY_IS_STOP_REQUESTED = "PREF_KEY_IS_STOP_REQUESTED";    
    /** Running state of data collector service */
    //public static final String PREF_KEY_COLLECTOR_START_TIME = "PREF_KEY_COLLECTOR_START_TIME";  
    /** The working folder for current data collector task.  */
    public static final String PREF_KEY_COLLECTOR_WORK_PATH = "PREF_KEY_COLLECTOR_WORK_PATH";  
    public static final String PREF_KEY_COLLECTOR_WORK_FOLDER = "PREF_KEY_COLLECTOR_WORK_FOLDER"; 
}
