package sjtu.omnilab.omniperf.core;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import sjtu.omnilab.omniperf.utils.Logger;
import sjtu.omnilab.omniperf.utils.PhoneUtils;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;


/**
 * Main service to collect mobile data passively including tcpdump, user input etc.
 * @author chenxm
 *
 */
public class DataCollectorService extends Service implements View.OnTouchListener  {
	/** Global objects */
	private OmniperfApp mApp;
	private PhoneUtils mPhoneUtils;
	private SharedPreferences mPreferences;
	private IBinder mBinder;
	
	/** workspace */
	private String currentWorkPath;
	private String currentWorkFolder;
	
	/** tcpdump running state */
	private long tcpdumpStartTime = -1;
	private boolean isTcpdumpRunning = false;
	/** tcpdump instance */
	private Process tcpdumpShellProcess = null;
	/** tcpdump shell output stream */
	private DataOutputStream tcpdumpShellOS = null;
	
	/** Output stream and Buffer Writer for peripherals traces files */
	private OutputStream mTouchScreenTraceOutputFile;
	private BufferedWriter mTouchScreenTracewriter;
	private OutputStream mWifiTraceOutputFile;
	private BufferedWriter mWifiTracewriter;
	private OutputStream mBatteryTraceOutputFile;
	private BufferedWriter mBatteryTracewriter;
	private OutputStream mGPSTraceOutputFile;
	private BufferedWriter mGPSTracewriter;
	private OutputStream mRadioTraceOutputFile;
	private BufferedWriter mRadioTracewriter;
	private OutputStream mCameraTraceOutputFile;
	private BufferedWriter mCameraTracewriter;
	private OutputStream mBluetoohTraceOutputFile;
	private BufferedWriter mBluetoothTracewriter;
	private OutputStream mScreenOutputFile;
	private BufferedWriter mScreenTracewriter;
	private OutputStream mScreenRotationOutputFile;
	private BufferedWriter mScreenRotationTracewriter;
	private OutputStream mActiveProcessOutputFile;
	private BufferedWriter mActiveProcessTracewriter;
	private OutputStream mDeviceInfoOutputFile;
	private OutputStream mDeviceDetailsOutputFile;
	private BufferedWriter mDeviceInfoWriter;
	private BufferedWriter mDeviceDetailsWriter;
	private OutputStream mNetworkDetailsOutputFile;
	private BufferedWriter mNetworkTracewriter;
	
	/** String constants used in Omniperf/ARO trace files */
	private static class TraceFileConstants {
		static final String OFF = "OFF";
		static final String ON = "ON";
		static final String STANDBY = "STANDBY";
		static final String CONNECTED = "CONNECTED";
		static final String DISCONNCTED = "DISCONNECTED";
		static final String CONNECTED_NETWORK = "CONNECTED";
		static final String DISCONNECTING_NETWORK = "DISCONNECTING";
		static final String CONNECTING_NETWORK = "CONNECTING";
		static final String DISCONNECTED_NETWORK = "DISCONNECTED";
		static final String SUSPENDED_NETWORK = "SUSPENDED";
		static final String UNKNOWN_NETWORK = "UNKNOWN";
		static String IMPORTANCE_BACKGROUND = "Background";
		static String IMPORTANCE_FOREGROUND = "Foreground";
		static final String NOT_ASSIGNED_NETWORK = "NOTASSIGNED";
	}
	
	/** Intent filter to adding action for broadcast receivers **/
	private IntentFilter mIntentFilter;
	
	/* touch screen trace colletor */
	/** For recording user touch screen actions. */
	private View mView=null;
	private WindowManager mWindowManager=null;
	
	/* screen brightness collector */
	/** Timer to check screen brightness */
	private Timer checkScreenBrightness = new Timer();
	/** Screen brightness value from 0-255 */
	private float mScreencurBrightness = 0;
	/** Previous Screen brightness value */
	private float mPrevScreencurBrightness = 1;
	/** Screen timeout (Device sleep) value in seconds */
	private int mScreenTimeout = 0;
	/** Previous Screen timeout (Device sleep) value in seconds */
	private int mPrevScreenTimeout = 0;
	
	/* GPS collector */
	/** GPS active boolean flag */
	private Boolean mGPSActive = false;
	/** Location Manager class object */
	private LocationManager mGPSStatesManager;
	/** GPS State listener */
	private GpsStatus.Listener mGPSStatesListner;
	/** Previous GPS enabled state */
	private boolean prevGpsEnabledState = false;
	/** Timer to run every 500 milliseconds to check GPS states */
	private Timer checkLocationService = new Timer();
	
	/* Battery level collector */
	/** Broadcast receiver for Batter events */
	private BroadcastReceiver mBatteryLevelReceiver;
	
	/* Radio trace collector */
	/** Telephony manager class object **/
	private TelephonyManager mTelphoneManager;
	/** Phone state listener listener to get RSSI value **/
	private PhoneStateListener mPhoneStateListener;
	/** baearer change state */
	private boolean isFirstBearerChange = true;
	/** connectivity manager */
	private ConnectivityManager mConnectivityManager;
	/**indicates whether WIFI, MOBILE, or UNKNOWN **/
	private String mPrevBearer = TraceFileConstants.NOT_ASSIGNED_NETWORK;
	/** network types */
	private int mPrevNetworkType;
	
	/* bluetooth collector */
	/** Intent filter to adding action for bluetooth broadcast receivers **/
	private IntentFilter mBluetoothIntentFilter;
	
	/* wifi trace collector */
	private WifiManager mWifiManager;
	private String mWifiMacAddress;
	private String mWifiNetworkSSID;
	private int mWifiRssi;
	
	/* active process collector */
	/** Active running processes package list */
	private List<RunningAppProcessInfo> mActiveProcessprocess;
	
	/* camera collector */
	/** Current Camera state on/off boolean flag */
	private Boolean mCameraOn = false;
	/** Previous Camera state on/off boolean flag */
	private Boolean mPrevCameraOn = true;
	/** Timer to run every 500 milliseconds to check Camera states */
	private Timer checkCameraLaunch = new Timer();
	
	/* SD card state monitor */
	/** Timer to run every 5 seconds to check SD card space is always greater than 5MB to continue trace */
	private Timer checkSDCardSpace = new Timer();
	
	/* airplane mode monitor */
	/** Timer to run every 1 second to check Airplane mode has not been enabled */
	private Timer checkAirplaneModeEnabled = new Timer();
	
	/* Screen rotation collector */
	/** The broadcast receiver that listens for screen rotation. */
	private BroadcastReceiver mScreenRotationReceiver;
	/** LandScape Screen orientation */
	private static final String LANDSCAPE_MODE = "landscape";
	private static final String PORTRAIT_MODE = "portrait";
	
	/**
     * The Binder class that returns an instance of running collector.
     */
    public class CollectorBinder extends Binder {

        public DataCollectorService getService() {
            return DataCollectorService.this;
        }
    }
    
    /**
     * Get the tcpdump running state of this service instance .
     * @return
     */
    public boolean isTcpdumpRunning(){
    	return this.isTcpdumpRunning;
    }
    
    public long getTcpdumpStartTime(){
    	return this.tcpdumpStartTime;
    }
	
	/**
	 * Gets processing when an DataCollectorService object is created.
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		Logger.d("DataCollectorService.onCreate called");
		super.onCreate();
		this.mApp = (OmniperfApp) getApplication();
		this.mPhoneUtils = mApp.getPhoneUtils();
		this.mPreferences = mApp.getSharedPreferences();
		this.mBinder = new CollectorBinder();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logger.d("DataCollectorService.onStartCommand called.");
		super.onStartCommand(intent, flags, startId);
		this.currentWorkPath = mPreferences.getString(Config.PREF_KEY_COLLECTOR_WORK_PATH, "");
		this.currentWorkFolder = mPreferences.getString(Config.PREF_KEY_COLLECTOR_WORK_FOLDER, "");
		startDataCollectorTcpdumpCapture();	// tcpdump instance
		startDataTraceCollection();	// other traces
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		Logger.d("DataCollectorService.onBind called.");
		return this.mBinder;
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
	}
	
	/**
	 * Starts the dedicated thread for tcpdump network traffic capture in the
	 * native shell
	 */
	private void startDataCollectorTcpdumpCapture() {
		// Starting the tcpdump on separate thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					startTcpDump();
				} catch (IOException e) {
					Logger.e("IOException in startTcpDump ", e);
				} catch (InterruptedException e) {
					Logger.e("InterruptedException in startTcpDump ", e);
				}
			}
		}).start();
	}
	
	/**
	 * This method creates a SU enabled shell Sets the execute permission for
	 * tcpdump and key.db Starts the tcpdump on Completion or abnormal
	 * termination of tcpdump Shell is destroyed
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void startTcpDump() throws IOException, InterruptedException {
		Logger.d("inside startTcpDump at timestamp " + System.currentTimeMillis());
		
		int shExitValue = 0;
		if (! isTcpdumpRunning ){
			//only start tcpdump if it's not already running, to handle the case where the background
			//service was stopped and now restarting
			Logger.i("tcpdump is not running. Starting tcpdump in the shell now");
			
			tcpdumpShellProcess = Runtime.getRuntime().exec("su");
			tcpdumpShellOS = new DataOutputStream(tcpdumpShellProcess.getOutputStream());
			String Command = "chmod 777 " + mApp.INTERNAL_DATA_PATH.getPath() + "/" + Config.TCPDUMP_EXE+ "\n";
			tcpdumpShellOS.writeBytes(Command);
			Command = "chmod 777 " + mApp.INTERNAL_DATA_PATH.getPath() + "/key.db" + "\n";
			tcpdumpShellOS.writeBytes(Command);
			Command = mApp.INTERNAL_DATA_PATH.getPath() + "/" + Config.TCPDUMP_EXE + " -w " + this.currentWorkPath + "\n";
			tcpdumpShellOS.writeBytes(Command);
			Logger.i("tcpdump command: " + Command);
			Command = "exit\n";
			tcpdumpShellOS.writeBytes(Command);
			tcpdumpShellOS.flush();
			
			OmniperfApp.StreamClearer stdoutClearer = mApp.new StreamClearer(tcpdumpShellProcess.getInputStream(), "stdout", true);
			new Thread(stdoutClearer).start();
			OmniperfApp.StreamClearer stderrClearer = mApp.new StreamClearer(tcpdumpShellProcess.getErrorStream(), "stderr", true);
			new Thread(stderrClearer).start();

			// We make a timer to monitor the running state of tcpdump instance
			final Timer tcpdumpMonitor = new Timer();
			tcpdumpMonitor.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					if ( mApp.isTcpdumpRunning() ) {
						isTcpdumpRunning = true; // Update collector running state
						if ( tcpdumpStartTime == -1 ){
							tcpdumpStartTime = System.currentTimeMillis();
						}
					} else {
						Logger.i("Coming out of startTcpDump at " + System.currentTimeMillis());
						try {
							if (tcpdumpShellOS != null) tcpdumpShellOS.close();
							if (tcpdumpShellProcess != null) tcpdumpShellProcess.destroy();
						} catch (Exception e) {
							Logger.e("exception in startTcpDump DataOutputStream close", e);
						}
						tcpdumpMonitor.cancel();
						requestStop();
					}
				}
			}, Config.DATA_COLLECTOR_START_TICK_TIME, Config.DATA_COLLECTOR_START_TICK_TIME);
			
			shExitValue = tcpdumpShellProcess.waitFor();
			Logger.i("tcpdump waitFor returns exit value: " + shExitValue + " at " + System.currentTimeMillis());
			
		} else {
			Logger.i("timestamp " + System.currentTimeMillis() + ": tcpdump is already running");
		}
	}
	
	/**
	 * For dataCollectorService, this method should always be called instead of stopService() method.
	 */
	public void requestStop(){
		Logger.d("Data collector requestStop called. isTcpdumpRunning = " + isTcpdumpRunning);
		stopDataCollectorTcpdumpCapture();
		stopDataTraceCollection();
		this.stopSelf();
	}
	
	private void stopDataCollectorTcpdumpCapture(){
		// in a socket manner. (on modified tcpdump/libpcap)
		if ( this.isTcpdumpRunning ) {
			try {
				Socket tcpdumpsocket;
				tcpdumpsocket = new Socket(InetAddress.getByName("localhost"), Config.TCPDUMP_SERVER_PORT);
				final OutputStream out = tcpdumpsocket.getOutputStream();
				out.write("STOP".getBytes("ASCII"));
				out.flush();
				out.close();
				tcpdumpsocket.close();
				Logger.i("Stop tcpdump normally at " + System.currentTimeMillis());
			} catch (Exception e) {
				Logger.d("exception in requestStopDataCollector " + e.toString());
			}
		}
		this.isTcpdumpRunning = false;
		this.tcpdumpStartTime = -1;
	}
	
	/**
	 * Initializes and starts the Data Collector peripherals trace
	 * collection
	 * 
	 * @throws FileNotFoundException
	 */
	private void startDataTraceCollection() {
		try {
			Logger.i("starting Omniperf peripheral trace at timestamp=" + getDataCollectorEventTimeStamp());
			initTraceFiles();
		} catch (FileNotFoundException e) {
			Logger.e("exception in initTraceFile: Failed to start -Data Collector Trace", e);
		}
		try{
			startTouchScreenCollector();
			startScreenTraceMonitor();
			startGpsTraceMonitor();
			startBatteryLevelMonitor();
			startRadioTraceMonitor();
			startBluetoothTraceMonitor();
			startWifiTraceMonitor();
			startActiveProcessTrace();
			startCameraTrace();
			startDataBearerChangeNotification();
			startDeviceSDCardSpaceMidTrace();
			startAirplaneModeMidTrace();
			startScreenRotationMonitor();
		} catch ( Exception e){
			Logger.e("exception in stop data collection " + e.toString());
		}
	}
	
	/**
	 * Gets the system time value(in seconds).
	 * @return
	 */
	private double getDataCollectorEventTimeStamp() {
		return (double) System.currentTimeMillis() / 1000;
	}
	
	/**
	 * Method will initialize all the trace files Streams and open it for
	 * writing (i.e wifi/ Baterry/Gps trace files etc)
	 * 
	 * @throws FileNotFoundException
	 */
	private void initTraceFiles() throws FileNotFoundException {

		final String currentTraceDatapath = this.currentWorkPath;
		Logger.d("currentTraceDatapath=" + currentTraceDatapath);
		mTouchScreenTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutTouchScreenFileName ,true);
		mTouchScreenTracewriter = new BufferedWriter(new OutputStreamWriter(mTouchScreenTraceOutputFile));
		mWifiTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutWifiFileName ,true);
		mWifiTracewriter = new BufferedWriter(new OutputStreamWriter(mWifiTraceOutputFile));		
		mRadioTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutRadioFileName , true);
		mRadioTracewriter = new BufferedWriter(new OutputStreamWriter(mRadioTraceOutputFile));	
		mCameraTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutCameraFileName , true);
		mCameraTracewriter = new BufferedWriter(new OutputStreamWriter(mCameraTraceOutputFile));	
		mBatteryTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutBatteryFileName , true);
		mBatteryTracewriter = new BufferedWriter(new OutputStreamWriter(mBatteryTraceOutputFile));	
		mGPSTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutGPSFileName ,true);
		mGPSTracewriter = new BufferedWriter(new OutputStreamWriter(mGPSTraceOutputFile));	
		mScreenOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutScreenFileName,true);
		mScreenTracewriter = new BufferedWriter(new OutputStreamWriter(mScreenOutputFile));	
		mNetworkDetailsOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutNetworkDetailsFileName ,true);	
		mNetworkTracewriter = new BufferedWriter(new OutputStreamWriter(mNetworkDetailsOutputFile));
		mScreenRotationOutputFile = new FileOutputStream(currentTraceDatapath+ "/" + Config.OutScreenRotationFileName,true);
		mScreenRotationTracewriter = new BufferedWriter(new OutputStreamWriter(mScreenRotationOutputFile));
		mActiveProcessOutputFile = new FileOutputStream(currentTraceDatapath+ "/" + Config.OutActiveProcessFileName, true);
		mActiveProcessTracewriter = new BufferedWriter(new OutputStreamWriter(mActiveProcessOutputFile));
		mBluetoohTraceOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutBluetoothFileName, true);
		mBluetoothTracewriter = new BufferedWriter(new OutputStreamWriter(mBluetoohTraceOutputFile));
		mDeviceInfoOutputFile = new FileOutputStream(currentTraceDatapath + "/" + Config.OutDeviceInfoFileName, true);
		mDeviceInfoWriter = new BufferedWriter(new OutputStreamWriter(mDeviceInfoOutputFile));
		mDeviceDetailsOutputFile = new FileOutputStream(currentTraceDatapath+ "/" + Config.OutDeviceDetailsFileName, false);	
		mDeviceDetailsWriter = new BufferedWriter(new OutputStreamWriter(mDeviceDetailsOutputFile));
	}
	
	/**
	 * Initialize touch screen collector.
	 */
	private void startTouchScreenCollector(){
		try {
			// touch, only working on android < 4.0.
			mView=new View(this);
		    mView.setOnTouchListener(this);
		    mWindowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
		    WindowManager.LayoutParams params
		      =new WindowManager.LayoutParams(
		        WindowManager.LayoutParams.FILL_PARENT,
		        WindowManager.LayoutParams.FILL_PARENT,
		        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
		        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
		        PixelFormat.TRANSPARENT);
		    params.gravity=Gravity.FILL_HORIZONTAL|Gravity.FILL_VERTICAL;
		    mWindowManager.addView(mView, params);
		    writeLineToTraceFile(mTouchScreenTracewriter, "## User touch screen actions: time, x, y", false);
		} catch (Exception e) {
			Logger.e("Exception in starting touch screen collector " + e.toString());
		}
	}
	
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		String eventString = System.currentTimeMillis() + " " + event.getX() + ":"+event.getY();
		Logger.d(eventString);
		writeLineToTraceFile(mTouchScreenTracewriter, eventString, true);
		return(false);
	}
	
	/**
	 * Starts the device screen trace collection
	 */
	private void startScreenTraceMonitor() {
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
		checkScreenBrightness.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				getScreenBrigthnessTimeout();
				if ((mScreencurBrightness != mPrevScreencurBrightness)
						|| (mScreenTimeout != mPrevScreenTimeout)) {
					writeLineToTraceFile(mScreenTracewriter, TraceFileConstants.ON + " "
							+ mScreenTimeout + " " + mScreencurBrightness, true);
					mPrevScreencurBrightness = mScreencurBrightness;
					mPrevScreenTimeout = mScreenTimeout;

				}
			}
		}, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME);
		registerReceiver(mScreenTraceReceiver, mIntentFilter);
	}
	
	/**
	 * Gets the screen brightness and timeout value from Settings file
	 * @throws SettingNotFoundException
	 */
	private void getScreenBrigthnessTimeout() {
		try {
			mScreencurBrightness = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS);
			if (mScreencurBrightness >= 255)
				mScreencurBrightness = 240;
			// Brightness Min value 15 and Max 255
			mScreencurBrightness = Math.round((mScreencurBrightness / 240) * 100);
			mScreenTimeout = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_OFF_TIMEOUT);
			mScreenTimeout = mScreenTimeout / 1000; // In Seconds
		} catch (SettingNotFoundException e) {
			Logger.e("exception in getScreenBrigthnessTimeout", e);
		}

	}
	
	/**
	 * Screen trace data broadcast receiver
	 */
	private BroadcastReceiver mScreenTraceReceiver = new BroadcastReceiver() {
		// Screen state on-off boolean flag
		Boolean mScreenOn = false;
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				mScreenOn = false;
			} else if (action.equals(Intent.ACTION_SCREEN_ON)) {
				mScreenOn = true;
			}
			getScreenBrigthnessTimeout();
			if (mScreenOn) {
				writeLineToTraceFile(mScreenTracewriter, TraceFileConstants.ON + " "
						+ mScreenTimeout + " " + mScreencurBrightness, true);
				mPrevScreencurBrightness = mScreencurBrightness;
				mPrevScreenTimeout = mScreenTimeout;
			} else {
				writeLineToTraceFile(mScreenTracewriter, TraceFileConstants.OFF, true);
				mPrevScreencurBrightness = mScreencurBrightness;
				mPrevScreenTimeout = mScreenTimeout;
			}
			Logger.d("Screen brightness: " + mScreencurBrightness);
			Logger.d("Screen Timeout: " + mScreenTimeout);
		}
	};
	
	/**
	 * Starts the GPS peripherals trace collection
	 */
	private void startGpsTraceMonitor() {
		mGPSStatesManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mGPSStatesListner = new GPSStatesListener();
		mGPSStatesManager.addGpsStatusListener(mGPSStatesListner);
		
		//write the initial gps state to the trace file
		final boolean initialGpsState = isLocationServiceEnabled();
		writeGpsStateToTraceFile(initialGpsState);
		prevGpsEnabledState = initialGpsState;
		
		checkLocationService.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				// Current GPS enabled state
				final boolean currentGpsEnabledState = isLocationServiceEnabled();
				if (currentGpsEnabledState != prevGpsEnabledState) {
					writeGpsStateToTraceFile(currentGpsEnabledState);
				}
				prevGpsEnabledState = currentGpsEnabledState;
			}
		}, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME);
	}
	
	/**
	 * Captures the GPS trace data during the trace cycle
	 */
	private class GPSStatesListener implements GpsStatus.Listener {

		@Override
		public void onGpsStatusChanged(int event) {
			switch (event) {
			case GpsStatus.GPS_EVENT_STARTED:
				writeLineToTraceFile(mGPSTracewriter, "ACTIVE", true);
				mGPSActive = true;
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				writeLineToTraceFile(mGPSTracewriter, TraceFileConstants.STANDBY, true);
				mGPSActive = false;
				break;
			}
		}
	}
	
	/**
	 * Checks if the GPS radio is turned on and receiving fix
	 * @return boolean value to represent if the location service is enabled or
	 *         not
	 */
	private boolean isLocationServiceEnabled() {
		boolean enabled = false;
		// first, make sure at least one provider actually exists
		final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		final boolean gpsExists = (lm.getProvider(LocationManager.GPS_PROVIDER) != null);
		final boolean networkExists = (lm.getProvider(LocationManager.NETWORK_PROVIDER) != null);
		if (gpsExists || networkExists) {
			enabled = ((!gpsExists || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) && (!networkExists || lm
					.isProviderEnabled(LocationManager.NETWORK_PROVIDER)));
		}
		return enabled;
	}
	
	/**
	 * write the gps state to trace file
	 * @param currentGpsEnabledState
	 */
	private void writeGpsStateToTraceFile(final boolean currentGpsEnabledState) {
		if (currentGpsEnabledState) {
			Logger.d("gps enabled: ");
			if (!mGPSActive) {
				writeLineToTraceFile(mGPSTracewriter, TraceFileConstants.STANDBY, true);
			}
		} else {
			Logger.d("gps Disabled: ");
			writeLineToTraceFile(mGPSTracewriter, TraceFileConstants.OFF, true);
		}
	}
	
	/**
	 * Starts the battery trace
	 */
	private void startBatteryLevelMonitor() {
		if (mBatteryLevelReceiver == null) {
			mBatteryLevelReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					Logger.d("battery receiver called");
					// AC Power Source boolean flag
					Boolean mPowerSource = false;
					/** Battery level */
					int mBatteryLevel = 0;
					int status = -1;
					final String action = intent.getAction();
					// Battery temperature //
					int mBatteryTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
					if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
						final Bundle extras = intent.getExtras();
						if (extras != null) {
							status = extras.getInt(BatteryManager.EXTRA_PLUGGED, -1);
							final int rawlevel = intent.getIntExtra("level", -1);
							final int scale = intent.getIntExtra("scale", -1);
							int level = -1;
							if (rawlevel >= 0 && scale > 0) {
								level = (rawlevel * 100) / scale;
							}
							mBatteryLevel = level;
						}
						if (status != -1) {
							switch (status) {
							case 0: //USB Unplugged
								mPowerSource = false;
								break;
							case BatteryManager.BATTERY_PLUGGED_USB:
								mPowerSource = true;
								break;
							case BatteryManager.BATTERY_PLUGGED_AC:
								mPowerSource = true;
								break;
							case BatteryManager.BATTERY_STATUS_DISCHARGING:
								mPowerSource = false;
							default:
								mPowerSource = false;
								break;
							}
						}
					}
					Logger.d("received battery level: " + mBatteryLevel);
					Logger.d("received battery temp: " + mBatteryTemp / 10 + "C");
					Logger.d("received power source " + mPowerSource);
					writeLineToTraceFile(mBatteryTracewriter, mBatteryLevel + " " + mBatteryTemp / 10 + " " + mPowerSource, true);
				}
			};
		} 
		if (mBatteryLevelReceiver != null) {
			registerReceiver(mBatteryLevelReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		}
	}
	
	/**
	 * Starts the device radio trace collection
	 */
	private void startRadioTraceMonitor() {
		mTelphoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		setRadioSignalListener();
		mTelphoneManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
				| PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
	}
	
	/**
	 * Capture the device radio RSSI(signal strength) during the trace
	 * 
	 */
	private void setRadioSignalListener() {
		mPhoneStateListener = new PhoneStateListener() {
			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				super.onSignalStrengthsChanged(signalStrength);
				// GSM Radio signal strength in integer value which will be
				// converted to dDm (This is default considered network type)
				String mRadioSignalStrength = String.valueOf(0);
				mTelphoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				if (signalStrength.isGsm() || mTelphoneManager.getNetworkType() == 13) {
					int mLteSignalStrength = 0;
					int mLteRsrp = 0;
					int mLteRsrq = 0;
					int mLteRssnr = 0;
					int mLteCqi = 0;
					if (mTelphoneManager.getNetworkType() == 13) {
						try {
							mLteSignalStrength = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteSignalStrength"));
						} catch (NumberFormatException nmb) {
							Logger.e("mLteSignalStrength not found in LTE Signal Strength");
						}
						try {
							mLteRsrp = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRsrp"));
						} catch (NumberFormatException nmb) {
							Logger.e("mLteRsrp not found in LTE Signal Strength");
						}
						try {
							mLteRsrq = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRsrq"));
						} catch (NumberFormatException nmb) {
							Logger.e("mLteRsrq not found in LTE Signal Strength");
						}
						try {
							mLteRssnr = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRssnr"));
						} catch (NumberFormatException nmb) {
							Logger.e("mLteRssnr not found in LTE Signal Strength");
						}
						try {
							mLteCqi = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteCqi"));
						} catch (NumberFormatException nmb) {
							Logger.e("mLteCqi not found in LTE Signal Strength");
						}
					}
					// Check to see if LTE parameters are set
					if ((mLteSignalStrength == 0 && mLteRsrp == 0 && mLteRsrq == 0 && mLteCqi == 0)
							|| (mLteSignalStrength == -1 && mLteRsrp == -1 && mLteRsrq == -1 && mLteCqi == -1)) {
						// No LTE parameters set. Use GSM signal strength
						final int gsmSignalStrength = signalStrength.getGsmSignalStrength();
						if (signalStrength.isGsm() && gsmSignalStrength != 99) {
							mRadioSignalStrength = String.valueOf(-113 + (gsmSignalStrength * 2));
						}
					} else {
						// If hidden LTE parameters were defined and not set to
						// default values, then used them
						mRadioSignalStrength = mLteSignalStrength + " " + mLteRsrp + " " + mLteRsrq + " " + mLteRssnr + " " + mLteCqi;
					}
				}
				/**
				 * If the network type is CDMA then look for CDMA signal strength values.
				 */
				else if ((mTelphoneManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_CDMA)) {
					mRadioSignalStrength = String.valueOf(signalStrength.getCdmaDbm());
				}
				/**
				 * If the network type is EVDO O/A then look for EVDO signal
				 * strength values.
				 */
				else if (mTelphoneManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_EVDO_0
						|| mTelphoneManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_EVDO_A) {
					mRadioSignalStrength = String.valueOf(signalStrength.getEvdoDbm());
				}
				Logger.i("signal strength changed to " + mRadioSignalStrength);
				writeLineToTraceFile(mRadioTracewriter, mRadioSignalStrength, true);
			}
			//added to listen for 4g-3g-2g transitions
			@Override
			public void onDataConnectionStateChanged (int state, int networkType){
				Logger.d("entered onDataConnectionStateChanged ");
				Logger.d("state=" + state + "; networkType=" + networkType);
				final ConnectivityManager mConnectivityMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo mActiveNetworkInfo = mConnectivityMgr.getActiveNetworkInfo();
				final boolean isNetworkConnected = (state == TelephonyManager.DATA_CONNECTED);
				if (!isFirstBearerChange) {
					recordBearerAndNetworkChange(mActiveNetworkInfo, isNetworkConnected);
				}
			}
		};
	}
	
	/**
	 * Retrieves the value of the specified field from an instance of the specified class using
	 * reflection.
	 * @param mClass The name of the class.
	 * @param mInstance The object containing the value to be retrieved.
	 * @param fieldName The name of the field to be retrieved.
	 * @return The value of the specified field from the specified class.
	 */
	private final String getSpecifiedFieldValues(Class<?> mClass, Object mInstance, String fieldName) {
		String fieldValue = "";
		if (mClass == null || mInstance == null || fieldName == null)
			return fieldValue;
		try {
			final Field field = mClass.getDeclaredField(fieldName);
			if (field != null) {
				field.setAccessible(true);
				fieldValue = field.get(mInstance).toString();
			}
		} catch (NoSuchFieldException exp) {
			fieldValue = "";
			Logger.e("Exception in getSpecifiedFieldValues NoSuchFieldException" + exp);
		} catch (IllegalAccessException ile) {
			fieldValue = "";
			Logger.e("Exception in getSpecifiedFieldValues IllegalAccessException" + ile);
		}
		return fieldValue;
	}
	
	/**
	 * called by the mBearerChangeReceiver and mPhoneStateListener to record:
	 * 		1. bearer change between Wifi-Mobile
	 * 		2. network change between 4G-3G-2G
	 * @param mActiveNetworkInfo
	 * @param isNetworkConnected
	 */
	private void recordBearerAndNetworkChange(final NetworkInfo mActiveNetworkInfo, final boolean isNetworkConnected){
		Logger.d("enter recordBearerAndNetworkChange()");
		if (mActiveNetworkInfo != null && isNetworkConnected 
				&& getDeviceNetworkType(mActiveNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN){
			String currentBearer = getCurrentBearer();
			final int currentNetworkType = getDeviceNetworkType(mActiveNetworkInfo);
			Logger.i("mActiveNetworkInfo.state=" + mActiveNetworkInfo.getState());
			Logger.i("mPrevBearer=" + mPrevBearer + "; currentBearer=" + currentBearer);
			Logger.i("mPrevNetworkType=" + mPrevNetworkType + "; currentNetworkType=" + currentNetworkType);
			if(!mPrevBearer.equals(currentBearer)) {
				//bearer change, signaling a failover
				mPrevBearer = currentBearer;
				writeLineToTraceFile(mNetworkTracewriter,Integer.toString(currentNetworkType), true);
				Logger.i("failover, wrote networkType=" + currentNetworkType + " to networkdetails completed at timestamp: " + getDataCollectorEventTimeStamp());
				mPrevNetworkType = currentNetworkType;
			}
			//We need to handle case when we switch between 4G-3G-2G ( This is not as handover)
			//-1 - Wifi (We don't want to check for wifi network for 4G-3G-2G transition)
			else if( currentNetworkType != -1 && mPrevNetworkType != currentNetworkType){
				writeLineToTraceFile(mNetworkTracewriter,Integer.toString(currentNetworkType), true);
				Logger.i("4g-3g-2g switch, wrote networkType=" + currentNetworkType + " to networkdetails completed at timestamp: " + getDataCollectorEventTimeStamp());
				mPrevNetworkType = currentNetworkType;
			}
			// device_details trace file
			if (isFirstBearerChange) {
				captureDeviceInfo(mActiveNetworkInfo);
				isFirstBearerChange = false;
			}
		} else {
			Logger.i("mActiveNetworkInfo is null, network is not CONNECTED, or networkType is unknown...exiting recordBearerAndNetworkChange()");
		}
	}
	
	/**
	 * Captures the device information
	 * @param mCurrentNetworkType network info class object to get current network type 
	 */
	private void captureDeviceInfo(NetworkInfo mCurrentNetworkType) {
		final String ipAddress;
		final String deviceModel = Build.MODEL;
		final String deviceMake = Build.MANUFACTURER;
		final String osVersion = Build.VERSION.RELEASE;
		final String appVersion = mApp.getVersion();
		try {
			ipAddress = mPhoneUtils.getLocalIpAddress();
			if (ipAddress != null) {
				writeLineToTraceFile(mDeviceInfoWriter, ipAddress, false);
			}
		} catch (SocketException e) {
			Logger.e("exception in getLocalIpAddress", e);
		}
		writeLineToTraceFile(mDeviceDetailsWriter,
				getApplicationContext().getPackageName(), false);
		writeLineToTraceFile(mDeviceDetailsWriter, deviceModel, false);
	
		writeLineToTraceFile(mDeviceDetailsWriter, deviceMake, false);
		writeLineToTraceFile(mDeviceDetailsWriter, "android", false);
		writeLineToTraceFile(mDeviceDetailsWriter, osVersion, false);
		writeLineToTraceFile(mDeviceDetailsWriter, appVersion, false);
		writeLineToTraceFile(mDeviceDetailsWriter, Integer.toString(getDeviceNetworkType(mCurrentNetworkType)), false);
		DeviceProperty devProperty = mPhoneUtils.getDeviceProperty();
		writeLineToTraceFile(mDeviceDetailsWriter, Integer.toString(devProperty.displayWidth)+"*"+Integer.toString(devProperty.displayHeight), false);
	}

	/**
	 * Gets the current connected data network type of device i.e 3G/LTE/Wifi
	 * @param mCurrentNetworkType network info class object to get current network type 
	 * @return mCellNetworkType Current network type
	 */
	private int getDeviceNetworkType(NetworkInfo mCurrentNetworkType) {
		Logger.i("getting device network type" + mCurrentNetworkType);
		final TelephonyManager mtelManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		int networkType = mtelManager.getNetworkType();	
		// Check if the current network is WiFi *//
		if (mCurrentNetworkType.getType() == 1) {
			networkType = -1;
		}
		return networkType;	
	}
	
	/**
	 * Gets the current connected bearer
	 * @return boolean value to validate if current bearer is wifi
	 */
	private Boolean getifCurrentBearerWifi() {
		int type = 0;
		if (mConnectivityManager == null)
			return false;
		if (mConnectivityManager.getActiveNetworkInfo() != null) {
			type = mConnectivityManager.getActiveNetworkInfo().getType();
		}
		if (type == ConnectivityManager.TYPE_MOBILE) {
			Logger.i(" Connection Type :  Mobile");
			return false;
		} else {
			Logger.i(" Connection Type :  Wifi");
			return true;
		}
	}
	
	/**
	 * returns the value of the current bearer, either WIFI or MOBILE
	 */
	private String getCurrentBearer(){
		return getifCurrentBearerWifi() ? "WIFI" : "MOBILE";
	}
	
	/**
	 * Starts the bluetooth peripherals trace collection
	 */
	private void startBluetoothTraceMonitor() {
		switch (BluetoothAdapter.getDefaultAdapter().getState()) {
		case BluetoothAdapter.STATE_ON:
			if (BluetoothAdapter.getDefaultAdapter().getBondedDevices().isEmpty()) {
				writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.DISCONNCTED, true);
			} else {
				writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.CONNECTED, true);
			}
			break;
		case BluetoothAdapter.STATE_OFF:
			writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.OFF, true);
			break;
		}
		mBluetoothIntentFilter = new IntentFilter();
		mBluetoothIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		mBluetoothIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		mBluetoothIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		mBluetoothIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mBluetoothTraceReceiver, mBluetoothIntentFilter);
	}
	
	/**
	 * Bluetooth trace data broadcast receiver
	 */
	private BroadcastReceiver mBluetoothTraceReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				switch (BluetoothAdapter.getDefaultAdapter().getState()) {
				case BluetoothAdapter.STATE_ON:
					writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.DISCONNCTED, true);
					break;
				case BluetoothAdapter.STATE_OFF:
					writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.OFF,true);
					break;
				}
			}
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
					|| BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)
					|| BluetoothDevice.ACTION_FOUND.equals(action)) {
				final BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					writeLineToTraceFile(mBluetoothTracewriter, TraceFileConstants.DISCONNCTED, true);
				} else if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
					writeLineToTraceFile(mBluetoothTracewriter,TraceFileConstants.CONNECTED, true);
				}
			}
		}
	};
	
	/**
	 * Starts the wifi trace collection
	 */
	private void startWifiTraceMonitor() {
		IntentFilter mWifiIntentFilter;
		// Setup WiFi
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiIntentFilter = new IntentFilter();
		mWifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		mWifiIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		mWifiIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		mWifiIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
		mWifiIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(mWifiTraceReceiver, mWifiIntentFilter);
	}
	
	/**
	 * Wifi trace data broadcast receiver
	 */
	private BroadcastReceiver mWifiTraceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				Logger.d("entered WIFI_STATE_CHANGED_ACTION");
				if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
					Logger.d("entered WIFI_STATE_CHANGED_ACTION--DISCONNECTED");
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.DISCONNECTED_NETWORK, true);
				} else if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
					Logger.d("entered WIFI_STATE_CHANGED_ACTION--OFF");
					writeLineToTraceFile(mWifiTracewriter, TraceFileConstants.OFF, true);
				}
			}
			if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {

				final NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				final NetworkInfo.State state = info.getState();
				switch (state) {
				case CONNECTING:
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.CONNECTING_NETWORK, true);
					break;
				case CONNECTED:
					recordAndLogConnectedWifiDetails();					
					break;
				case DISCONNECTING:
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.DISCONNECTING_NETWORK, true);			
					break;
				case DISCONNECTED:
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.DISCONNECTED_NETWORK, true);
					break;
				case SUSPENDED:
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.SUSPENDED_NETWORK, true);			
					break;
				case UNKNOWN:
					writeLineToTraceFile(mWifiTracewriter,TraceFileConstants.UNKNOWN_NETWORK, true);				
					break;
				}
			}
		}
	};
	
	/**
	 * record the connected wifi information
	 */
	private void recordAndLogConnectedWifiDetails() {
		/* Get WiFi status
		 * DE9556: removed getifCurrentBearerWifi() call from if condition because when this function is called, 
		 * the wifi is already connected
		 */
		if (mWifiManager != null) {
			mWifiMacAddress = mWifiManager.getConnectionInfo().getBSSID();
			mWifiNetworkSSID = mWifiManager.getConnectionInfo().getSSID();
			mWifiRssi = mWifiManager.getConnectionInfo().getRssi();
			Logger.d("mWifiMac=" + mWifiMacAddress + ", ssid=" + mWifiNetworkSSID + ", rssi:" + mWifiRssi);
		}
		writeLineToTraceFile(mWifiTracewriter, TraceFileConstants.CONNECTED_NETWORK + " " + mWifiMacAddress + " "
				+ mWifiRssi + " " + mWifiNetworkSSID, true);
		Logger.i("connected to " + mWifiNetworkSSID + " write to mWifiTracewriter completed at timestamp: " + getDataCollectorEventTimeStamp());
	}
	
	/**
	 * Starts the active process trace by logging all running process in the
	 * trace file
	 */
	private void startActiveProcessTrace() {
		// mActiveProcessStates //
		String[] mActiveProcessStates;
		final ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		mActiveProcessprocess = mActivityManager.getRunningAppProcesses();
		mActiveProcessStates = new String[mActiveProcessprocess.size()];
		for (final Iterator<RunningAppProcessInfo> iterator = mActiveProcessprocess.iterator(); iterator.hasNext();) {
			final RunningAppProcessInfo runningAppProcessInfo = (RunningAppProcessInfo) iterator.next();
			final int pImportance = runningAppProcessInfo.importance;
			int Index = 0;
			switch (pImportance) {
			case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
				mActiveProcessStates[Index] = "Name:" + runningAppProcessInfo.processName + " State:" + TraceFileConstants.IMPORTANCE_BACKGROUND;
				writeLineToTraceFile(mActiveProcessTracewriter, mActiveProcessStates[Index], true);
				Index++;
				break;
			case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
				mActiveProcessStates[Index] = "Name:" + runningAppProcessInfo.processName + " State:" + TraceFileConstants.IMPORTANCE_FOREGROUND;
				writeLineToTraceFile(mActiveProcessTracewriter, mActiveProcessStates[Index], true);
				Index++;
				break;
			}
		}
	}
	
	/**
	 * Starts the camera trace collection
	 */
	private void startCameraTrace() {
		checkCameraLaunch.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				final String recentTaskName = getRecentTaskInfo().toLowerCase();
				if (recentTaskName.contains("camera")
						|| checkCurrentProcessStateForGround("camera")) {
					mCameraOn = true;
				} else
					mCameraOn = false;
				if (checkCurrentProcessState("camera"))
					mCameraOn = false;
				if (mCameraOn && !mPrevCameraOn) {
					Logger.i("Camera Turned on");
					writeLineToTraceFile(mCameraTracewriter, "ON", true);
					mCameraOn = true;
					mPrevCameraOn = true;
				} else if (!mCameraOn && mPrevCameraOn) {
					Logger.i("Camera Turned Off");
					writeLineToTraceFile(mCameraTracewriter, TraceFileConstants.OFF, true);
					mCameraOn = false;
					mPrevCameraOn = false;
				}
			}
		}, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME, Config.HALF_SECOND_TARCE_TIMER_REPATE_TIME);
	}
	
	/**
	 * Gets the recent opened package name
	 * @return recent launched package name
	 */
	private String getRecentTaskInfo() {
		/** Package name of recent launched application */
		String mLastLaucnhedProcess = " ";
		final ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		final List<?> l = mActivityManager.getRecentTasks(5, ActivityManager.RECENT_WITH_EXCLUDED);
		final RecentTaskInfo rti = (RecentTaskInfo) l.get(0);
		if (!mLastLaucnhedProcess.equalsIgnoreCase(rti.baseIntent.getComponent().getPackageName())
			&& !rti.baseIntent.getComponent().getPackageName().equalsIgnoreCase(Config.OMNIPERF_PACKAGE_NAME)) {
			//Logger.i("New Task=" + rti.baseIntent.getComponent().getPackageName());
			mLastLaucnhedProcess = rti.baseIntent.getComponent().getPackageName();
			return mLastLaucnhedProcess;
		}
		mLastLaucnhedProcess = rti.baseIntent.getComponent().getPackageName();
		return mLastLaucnhedProcess;

	}
	
	/**
	 * Checks the state of process is foreground
	 * 
	 * @param process
	 *            name
	 * 
	 * @return boolean value to represent the if package state is foreground
	 */
	@SuppressLint("DefaultLocale")
	private boolean checkCurrentProcessStateForGround(String processname) {
		final ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		mActiveProcessprocess = mActivityManager.getRunningAppProcesses();
		for (final Iterator<RunningAppProcessInfo> iterator = mActiveProcessprocess.iterator(); iterator.hasNext();) {
			final RunningAppProcessInfo runningAppProcessInfo = (RunningAppProcessInfo) iterator.next();
			final String pSname = runningAppProcessInfo.processName.toLowerCase();
			final int pImportance = runningAppProcessInfo.importance;
			if (pSname.contains(processname.toLowerCase())) {
				switch (pImportance) {
				case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Checks the state of process is background
	 * @param process
	 *            name
	 * @return boolean value to represent the if package state is background
	 */
	@SuppressLint("DefaultLocale")
	private boolean checkCurrentProcessState(String processname) {
		final ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		mActiveProcessprocess = mActivityManager.getRunningAppProcesses();
		for (final Iterator<RunningAppProcessInfo> iterator = mActiveProcessprocess.iterator(); iterator.hasNext();) {
			final RunningAppProcessInfo runningAppProcessInfo = (RunningAppProcessInfo) iterator.next();
			final String pSname = runningAppProcessInfo.processName.toLowerCase();
			final int pImportance = runningAppProcessInfo.importance;
			if (pSname.contains(processname.toLowerCase()) && !pSname.contains(":")) {
				switch (pImportance) {
				case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Starts the bearer change notification broadcast
	 */
	private void startDataBearerChangeNotification() {
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mBearerChangeReceiver, mIntentFilter);
		recordInitialBearerInfo();
	}
	
	/**
	 * Records the data connection bearer change during the life time of
	 * trace collection 
	 */
	private BroadcastReceiver mBearerChangeReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Logger.d("entered mBearerChangeReceiver ");
			final String action = intent.getAction();
			if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				
				final boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY , false);
				final boolean isNetworkConnected = !noConnectivity;
				
				final ConnectivityManager mConnectivityMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo mActiveNetworkInfo = mConnectivityMgr.getActiveNetworkInfo();
				if (!isFirstBearerChange) {
					recordBearerAndNetworkChange(mActiveNetworkInfo, isNetworkConnected);
				}
			}
		}
	};
	
	/**
	 * record the bearer and network info at the start
	 */
	private void recordInitialBearerInfo(){
		
		final ConnectivityManager mConnectivityMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		final NetworkInfo mActiveNetworkInfo = mConnectivityMgr.getActiveNetworkInfo();
		
		boolean isConnected = false;
		if (mActiveNetworkInfo != null){
			isConnected = mActiveNetworkInfo.isConnected();
		}
		Logger.d("recordInitialBearerInfo: isConnected=" + isConnected + "; currentBearerWifi=" + getifCurrentBearerWifi());
		//call to record the initial bearer
		recordBearerAndNetworkChange(mActiveNetworkInfo, isConnected);
		//log the wifi network details if current bearer is wifi
		if (getifCurrentBearerWifi()){
			recordAndLogConnectedWifiDetails();
		}
	}
	
	/**
	 * Checks the device SD Card space every seconds if its less than 1 MB to
	 * write traces
	 */
	private void startDeviceSDCardSpaceMidTrace() {
		checkSDCardSpace.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				if (mPhoneUtils.checkSDCardMemoryAvailable() < Config.SDCARD_MIN_SPACEKBYTES) {
					mApp.isSDCardMounted = mPhoneUtils.checkSDCardMounted();
					checkSDCardSpace.cancel();
					requestStop();
					Logger.i("STOP DATA COLLECTOR because startDeviceSDCardSpaceMidTrace = " + mPhoneUtils.checkSDCardMemoryAvailable());
					return;
				}
			}
		}, Config.SDCARD_TARCE_TIMER_REPATE_TIME, Config.SDCARD_TARCE_TIMER_REPATE_TIME);
	}
	
	/**
	 * Checks if Airplane mode has been turned on ever every second.
	 * Only exists the trace if wifi mode is also off.
	 */
	private void startAirplaneModeMidTrace() {
		final ConnectivityManager mConnectiviyMgr;
		mConnectiviyMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		checkAirplaneModeEnabled.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				final NetworkInfo.State wifiState = mConnectiviyMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
				if( mPhoneUtils.isAirplaneModeOn(getApplicationContext())
					&& (wifiState == NetworkInfo.State.UNKNOWN || wifiState == NetworkInfo.State.DISCONNECTED)){
					//We should cancel the timer here as we detected Air plane mode was turned on during trace cycle.
					// Sends the STOP Command to tcpdump socket 
					try {
						//We will sleep for 10 seconds to give time for tcpdump bearer change before request STOP
						Thread.sleep(10000);
					} catch (InterruptedException e) {
					}
					requestStop();
					checkAirplaneModeEnabled.cancel();
					Logger.i("STOP DATA COLLECTOR because Airplane Mode was turned on Mid Trace");
					return;
				}
			}
		}, Config.AIRPLANE_TARCE_TIMER_REPATE_TIME, Config.AIRPLANE_TARCE_TIMER_REPATE_TIME);
	}
	
	/**
	 * Creates and registers the broad cast receiver that listens for the screen
	 * rotation and and writes the screen rotation time to the
	 * "screen_rotations" file.
	 */
	private void startScreenRotationMonitor() {
		//record the initial screen rotation - uncomment to capture initial state.
		//recordScreenRotation();
		if (mScreenRotationReceiver == null) {
			mScreenRotationReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
						recordScreenRotation();
					}
				}
			};
		}
		registerReceiver(mScreenRotationReceiver, new IntentFilter( Intent.ACTION_CONFIGURATION_CHANGED));

	}
	
	/**
	 * method to record the screen rotation. Called when:
	 * 1. on trace start to record initial rotation
	 * 2. during trace, upon screen rotation changes
	 */
	private void recordScreenRotation() {
		final Configuration newConfig = getResources().getConfiguration();
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			writeLineToTraceFile(mScreenRotationTracewriter, LANDSCAPE_MODE, true);
		} else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			writeLineToTraceFile(mScreenRotationTracewriter, PORTRAIT_MODE, true);
		}
	}
	
	/**
	 * Stops the Omniperf Data Collector peripherals trace collection
	 */
	private void stopDataTraceCollection() {
		Logger.i("stopping trace collection");
		try{
			stopTouchScreenCollector();
			stopScreenTraceMonitor();
			stopGpsTraceMonitor();
			stopBatteryLevelMonitor();
			stopRadioTraceMonitor();
			stopBluetoothTraceMonitor();
			stopWifiTraceMonitor();
			stopCameraTrace();
			stopDataBearerChangeNotification();
			stopDeviceSDCardSpaceMidTrace();
			stopAirplaneModeMidTrace();
			stopScreenRotationMonitor();
		} catch ( Exception e){
			Logger.e("exception in stop data collection " + e.toString());
		}
		try {
			closeTraceFiles();
		} catch (IOException e) {
			Logger.e("exception in closeTraceFile", e);
		}
	}
	
	/**
	 * Stop touch screen collector and close output stream
	 */
	private void stopTouchScreenCollector(){
		if ( this.mWindowManager != null && this.mView != null )
			try{
				this.mWindowManager.removeView(mView);
			} catch (Exception e ){
				Logger.e("Exception in stoping touch screen collector: " + e.toString());
			}
	}
	
	/**
	 * Stop the device screen trace collection
	 */
	private void stopScreenTraceMonitor() {
		try {
			if (mScreenTraceReceiver != null) {
				unregisterReceiver(mScreenTraceReceiver);
				checkScreenBrightness.cancel();
			}
			checkScreenBrightness = null;
			mIntentFilter = null;
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mScreenTraceReceiver");
		}
	}
	
	/**
	 * Stop the GPS peripherals trace collection
	 */
	private void stopGpsTraceMonitor() {
		if (mGPSStatesManager != null && mGPSStatesListner != null) {
			mGPSStatesManager.removeGpsStatusListener(mGPSStatesListner);
			mGPSStatesManager = null;
		}
		checkLocationService.cancel();
	}
	
	/**
	 * Stop the battery trace
	 */
	private void stopBatteryLevelMonitor() {
		try {
			if (mBatteryLevelReceiver != null) {
				unregisterReceiver(mBatteryLevelReceiver);
				mBatteryLevelReceiver = null;
			}
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mBatteryLevelReceiver");
		}
	}
	
	/**
	 * Stops the device radio trace collection
	 */
	private void stopRadioTraceMonitor() {
		if (mPhoneStateListener != null) {
			mTelphoneManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
			mTelphoneManager = null;
			mPhoneStateListener = null;
		}

	}
	
	/**
	 * Stops the bluetooth peripherals trace collection
	 */
	private void stopBluetoothTraceMonitor() {
		try {
			if (mBluetoothTraceReceiver != null) {
				unregisterReceiver(mBluetoothTraceReceiver);
				mBluetoothIntentFilter = null;
			}
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mBluetoothTraceReceiver");
		}
	}
	
	/**
	 * Stops the wifi trace collection
	 */
	private void stopWifiTraceMonitor() {
		try {
			if (mWifiTraceReceiver != null) {
				unregisterReceiver(mWifiTraceReceiver);
				mWifiManager = null;
			}
			mConnectivityManager = null;
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mWifiTraceReceiver");
		}
	}
	
	/**
	 * Stops the camera trace collection
	 */
	private void stopCameraTrace() {
		if (checkCameraLaunch != null) {
			checkCameraLaunch.cancel();
			checkCameraLaunch = null;
		}
	}
	
	/**
	 * Stops the bearer change notification broadcast
	 */
	private void stopDataBearerChangeNotification() {
		try {
			if (mBearerChangeReceiver != null){
				unregisterReceiver(mBearerChangeReceiver);
				mBearerChangeReceiver = null;
			}
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mBearerChangeReceiver");
		}
	}
	
	/**
	 * Stops the SD Card memory check timer during the trace
	 */
	private void stopDeviceSDCardSpaceMidTrace() {
		if (checkSDCardSpace != null){
			checkSDCardSpace.cancel();
			checkSDCardSpace = null;
		}
	}
	
	/**
	 * Stops the Airplane mode enabled check timer during the trace
	 */
	private void stopAirplaneModeMidTrace() {
		if (checkAirplaneModeEnabled != null){
			checkAirplaneModeEnabled.cancel();
			checkAirplaneModeEnabled = null;
		}
	}
	
	/**
	 * Unregisters the screen rotation broadcast receiver.
	 */
	private void stopScreenRotationMonitor() {
		try {
			if (mScreenRotationReceiver != null){
				unregisterReceiver(mScreenRotationReceiver);
				mScreenRotationReceiver = null;
			}
		} catch (IllegalArgumentException e) {
			Logger.e("IllegalArgumentException at unregister mScreenRotationReceiver");
		}
	}
	
	/**
	 * Method will close all trace file Streams and closes the files.
	 * Should set the writer to null after closing, so that a check to 
	 * verify an open writer can be performed in writeTraceLineToTraceFile
	 * 
	 * @throws IOException
	 */
	private void closeTraceFiles() throws IOException {
		if (mTouchScreenTracewriter != null) {
			mTouchScreenTracewriter.close();
			mTouchScreenTracewriter = null;
			mTouchScreenTraceOutputFile.close();
		}
		if (mWifiTracewriter != null) {
			mWifiTracewriter.close();
			mWifiTracewriter = null;
			mWifiTraceOutputFile.close();
		}
		if (mRadioTracewriter != null) {
			mRadioTracewriter.close();
			mRadioTracewriter = null;
			mRadioTraceOutputFile.close();
		}
		if (mCameraTracewriter != null) {
			mCameraTracewriter.close();
			mCameraTracewriter = null;
			mCameraTraceOutputFile.close();
		}
		if (mBluetoothTracewriter != null) {

			mBluetoothTracewriter.close();
			mBluetoothTracewriter = null;
			mBluetoohTraceOutputFile.close();
		}
		if (mBatteryTracewriter != null) {
			mBatteryTracewriter.close();
			mBatteryTracewriter = null;
			mBatteryTraceOutputFile.close();
		}
		if (mGPSTracewriter != null) {
			mGPSTracewriter.close();
			mGPSTracewriter = null;
			mGPSTraceOutputFile.close();
		}
		if (mScreenTracewriter != null) {
			mScreenTracewriter.close();
			mScreenTracewriter = null;
			mScreenOutputFile.close();
		}
		if (mScreenRotationTracewriter != null) {
			mScreenRotationTracewriter.close();
			mScreenRotationTracewriter = null;
			mScreenRotationOutputFile.close();
		}
		if (mActiveProcessTracewriter != null) {
			mActiveProcessTracewriter.close();
			mActiveProcessTracewriter = null;
			mActiveProcessOutputFile.close();
		}
		if (mDeviceInfoWriter != null) {
			mDeviceInfoWriter.close();
			mDeviceInfoWriter = null;
			mDeviceInfoOutputFile.close();
		}
		if (mDeviceDetailsWriter != null) {
			mDeviceDetailsWriter.close();
			mDeviceDetailsWriter = null;
			mDeviceDetailsOutputFile.close();
		}
		if (mNetworkTracewriter != null) {
			mNetworkTracewriter.close();
			mNetworkTracewriter = null;
			mNetworkDetailsOutputFile.close();
		}
	}
	
	/**
	 * Method write given String message to trace file passed as an argument
	 * outputfilewriter : Name of Trace File writer to which trace has to be
	 * written content : Trace message to be written
	 */
	private void writeLineToTraceFile(BufferedWriter outputfilewriter, String content, boolean timestamp) {
		try {
			if (outputfilewriter != null){
				final String eol = System.getProperty("line.separator");
				if (timestamp) {
					outputfilewriter.write(getDataCollectorEventTimeStamp() + " " + content + eol);
					outputfilewriter.flush();
				} else {
					outputfilewriter.write(content + eol);
					outputfilewriter.flush();
				}
			}
		} catch (IOException e) {
			// TODO: Need to display the exception error instead of Mid Trace mounted error
			mApp.isSDCardMounted = mPhoneUtils.checkSDCardMounted();
			Logger.e("exception in writeTraceLineToTraceFile", e);
		}
	}
}
