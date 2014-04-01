package sjtu.omnilab.omniperf.activities;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import sjtu.omnilab.omniperf.R;
import sjtu.omnilab.omniperf.core.Config;
import sjtu.omnilab.omniperf.core.DataCollectorService;
import sjtu.omnilab.omniperf.core.OmniperfApp;
import sjtu.omnilab.omniperf.core.UpdateIntent;
import sjtu.omnilab.omniperf.core.DataCollectorService.CollectorBinder;
import sjtu.omnilab.omniperf.core.MeasurementSchedulerService.SchedulerBinder;
import sjtu.omnilab.omniperf.utils.Logger;
import sjtu.omnilab.omniperf.utils.PhoneUtils;
import sjtu.omnilab.omniperf.utils.Util;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

public class DataCollectorActivity extends Activity {
    
	/** Activity tag required by TabView */
	public final static String TAB_TAG = "PASSIVE_MEASUREMENT_MAIN";
	
	/** Unique application instance */
	private OmniperfApp mApp;
	
	/** Parent activity */
	private OmniperfMainActivity parent;
		
	/** Phone utils */
	private PhoneUtils phoneUtils;
	
	/** Shared preferences */
	private SharedPreferences mPreferences;
	
	/** Data collector service */
	private DataCollectorService mCollectorService;
	
	/** Start/end button to control passive data collectors. */
	private Button startPassiveCollector;
	
    /** Upload passive collected data to server. */
    private Button uploadPassiveData;
    
    /** Used for calculating the trace duration*/
    private Chronometer stopWatch;
    
    /** TextView to show elapsed time since collector starts.*/
    private TextView timer;
    
    /** TextView to show data collecotr update */
    private TextView collectorConsole;
    private String collectorConsoleContent;
    
    /**
     * Message handler to process other thread requests on UI change.
     */
    private final static int MID_COLLECTOR_STOPPED = 0;
    private final static int MID_COLLECTOR_STARTING = 1;
    private final static int MID_COLLECTOR_STARTED = 2;
    private final static int MID_COLLECTOR_TIMER_TEXT = 3;
    private final static int MID_NO_MEDIA_MOUNTED = 4;
    private final static int MID_AIRPLANE_MODE_ON = 5;
    private final static int MID_NO_NETWORKS = 6;
	private Handler dataCollectorStateHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
			switch (msg.what) {
			case MID_COLLECTOR_STOPPED:
				updateCollectorConsole("Data collector start failed.");
				changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
				break;
			case MID_COLLECTOR_STARTING:
				changeButtonStates(R.string.passiveButtonStart, false, R.string.passiveButtonUpload, false);
				break;
			case MID_COLLECTOR_STARTED:
				long startTime = mCollectorService.getTcpdumpStartTime();
				updateCollectorConsole("Data collector started from " + Util.getTimeStringFromMicrosecond(startTime*1000));
				changeButtonStates(R.string.passiveButtonStop, true, R.string.passiveButtonHide, true);
				break;
			case MID_COLLECTOR_TIMER_TEXT:
				timer.setText( (String) msg.obj);
				break;
			case MID_NO_MEDIA_MOUNTED:
				updateCollectorConsole(getResources().getString(R.string.errorMassageSDStorage));
	        	changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
				break;
			case MID_AIRPLANE_MODE_ON:
				updateCollectorConsole(getResources().getString(R.string.errorMassageAirPlaneMode));
	        	changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
				break;
			case MID_NO_NETWORKS:
				updateCollectorConsole(getResources().getString(R.string.errorMassageNoNetworks));
	            changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
				break;
			}
		}
    };

    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    Logger.d("DataCollectorActivity onCreate called.");
	    super.onCreate(savedInstanceState);
	    this.setContentView(R.layout.data_collector_main);
	    this.mApp = (OmniperfApp) getApplication();
	    this.parent = (OmniperfMainActivity) this.getParent();
	    this.phoneUtils = mApp.getPhoneUtils();
	    this.mPreferences = mApp.getSharedPreferences();
	    this.mCollectorService = parent.getCollector();
        initCollectorControls();
        initCollectorControlsListener();
	    updateCollectorConsole("Data collector UI ready!");
	}
	
	@Override
	protected void onStart() {
		Logger.d("DataCollectorActivity onStart called.");
		super.onStart();
		Logger.e("collector = " + mCollectorService);
		if ( mCollectorService != null ){
			Logger.e("collector running = " + mCollectorService.isTcpdumpRunning());
		    if ( !mCollectorService.isTcpdumpRunning() ){
		    	timer.setText(R.string.passiveTimerDefault);
		    	changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
		    } else {
		    	long startTime = mCollectorService.getTcpdumpStartTime();
			    updateCollectorConsole("Data collector started from " + Util.getTimeStringFromMicrosecond(startTime*1000));
		    	stopWatch.setBase(startTime);
		    	stopWatch.start();
		    	changeButtonStates(R.string.passiveButtonStop, true, R.string.passiveButtonHide, true);
		    }	
		}
	}
	
	@Override
	protected void onStop(){
		Logger.d("DataCollectorActivity onStop called.");
		super.onStop();
		stopWatch.stop();
	}
	
    @Override
    protected void onDestroy() {
    	Logger.d("DataCollectorActivity onDestroy called.");
        super.onDestroy();
    }
    
	/**
	 * Initiate the static control views in passive activity.
	 */
	private void initCollectorControls(){
	    startPassiveCollector = (Button) findViewById(R.id.collectorButtonStart);
	    uploadPassiveData = (Button) findViewById(R.id.collectorButtonUpload);
	    timer = (TextView) findViewById(R.id.collectorTimer);
	    collectorConsole = (TextView) findViewById(R.id.collectorConsoleText);
	    collectorConsoleContent = "";
	    stopWatch = (Chronometer) findViewById(R.id.chrono);
	}
	
	/**
	 *  Change state of start/upload button of data collector activity.
	 * @param startButtonID
	 * @param startButtonClickable
	 * @param uploadButtonID
	 * @param uploadButtonClickable
	 */
	protected void changeButtonStates(int startButtonID, boolean startButtonClickable,
			int uploadButtonID, boolean uploadButtonClickable){
		this.startPassiveCollector.setText(getResources().getString(startButtonID));
		this.startPassiveCollector.setEnabled(startButtonClickable);
		this.uploadPassiveData.setText(getResources().getString(uploadButtonID));
		this.uploadPassiveData.setEnabled(uploadButtonClickable);
	}
	
	/**
	 * Initiate the control's listeners
	 */
	private void initCollectorControlsListener(){
		// Listener on Stop watch
		stopWatch.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener(){   
			@Override             
            public void onChronometerTick(Chronometer arg0) {
				//Logger.d("tcpdump running  = " + mCollectorService.isTcpdumpRunning());
				/** The tick count of timer in seconds. */
            	long stopWatchCountUp;
                stopWatchCountUp = (System.currentTimeMillis() - arg0.getBase()) / 1000;
                String asText;
                String hourPlace = "";
                String minPlace = "";
                String secPlace = "";
                if ( stopWatchCountUp / 3600 < 10 )
                	hourPlace = "0";
                if (((stopWatchCountUp / 60) % 60) < 10)
                    minPlace = "0";
                if (stopWatchCountUp % 60 < 10)
                    secPlace = "0";
                //Display the time in standard "Hours:Minutes:Seconds" | "00:00:00"
                asText = hourPlace + (stopWatchCountUp / 3600) + ":" + minPlace + ((stopWatchCountUp / 60) % 60) + ":" + secPlace + (stopWatchCountUp % 60);
                Message msg  = Message.obtain(dataCollectorStateHandler, MID_COLLECTOR_TIMER_TEXT);
                msg.obj = new String(asText);
                dataCollectorStateHandler.sendMessage( msg );
            }         
        });
		
		// Listener on start button.
        startPassiveCollector.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( startPassiveCollector.getText().equals(getResources().getString(R.string.passiveButtonStart))){
                    Logger.d("BUTTON-START clicked");
                    // Check root access of device and start collector service
                    if ( checkRootAccess() ){
                    	startCollector();
                    }
                } else if ( startPassiveCollector.getText().equals(getResources().getString(R.string.passiveButtonStop)) ){
                	Logger.d("BUTTON-STOP clicked");
                    // Make sure there is data collector running
                    if ( mApp.isTcpdumpRunning() ){
                		updateCollectorConsole("Stop data collector.");
                		mCollectorService.requestStop();
                		stopWatch.stop();
                    } else {
                        Logger.e("Error in stopping before starting.");
                    }
                    changeButtonStates(R.string.passiveButtonStart, true, R.string.passiveButtonUpload, true);
                } else {
                    Logger.e("Error in START button logic.");
                }
                return;
            }
        });
        
        // Listener on upload button
        uploadPassiveData.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( uploadPassiveData.getText().equals(getResources().getString(R.string.passiveButtonUpload))){
                    Logger.d("BUTTON-UPLOAD clicked");
                    if ( mCollectorService.isTcpdumpRunning() ){
                    	Logger.e("Uploading while collecting is not allowed");
                    } else {
                    	// TODO: compress data and upload to server
                    }
                } else if ( uploadPassiveData.getText().equals(getResources().getString(R.string.passiveButtonHide))){
                	Logger.d("BUTTON-HIDE clicked");
                    finish();
                } else {
                    Logger.e("Error in UPLOAD button logic.");
                }
                return;
            }
        });
	}
	
	/**
	 * Start passive collectors
	 */
	private void startCollector(){
		// Change UI
		changeButtonStates(R.string.passiveButtonStart, false, R.string.passiveButtonUpload, false);
	    // Check network states
	    ConnectivityManager mConnectiviyMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo.State wifiState = mConnectiviyMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
        NetworkInfo.State mobileState = mConnectiviyMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
        Logger.d("Checking network states: wifiState=" + wifiState + "; mobileState=" + mobileState);
        
        // Check to validate is SD card is available for writing trace data
        final String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
        	dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_NO_MEDIA_MOUNTED));
            return;
        } else if (phoneUtils.isAirplaneModeOn(getApplicationContext())
                && (wifiState == NetworkInfo.State.UNKNOWN || wifiState == NetworkInfo.State.DISCONNECTED)) {
            // Check if Airplane mode is on AND Wifi network is disconnected
        	dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_AIRPLANE_MODE_ON));
            return;
        } else if ((wifiState == NetworkInfo.State.DISCONNECTED || wifiState == NetworkInfo.State.UNKNOWN)
                && (mobileState == NetworkInfo.State.DISCONNECTED || mobileState == NetworkInfo.State.UNKNOWN)) {
            // do not allow DC to start with both Mobile and Wifi off
        	dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_NO_NETWORKS));
            return;
        }
        
        // Make sure tcpdump instance is not running
        if ( checkTcpdumpRunning() ){
        	dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_COLLECTOR_STOPPED));
        	return;
        }

        // Creates the dump directory on the device SD card under root directory of Omniperf
        String currentWorkFolder = getDefaultWorkFolderName();
        String currentWorkPath = mApp.EXTERNAL_ROOT_FOLDER.getPath() + "/" + currentWorkFolder;
        mApp.createDirectory(currentWorkPath);
        if( currentWorkFolder !=null) {
        	SharedPreferences.Editor editor = mPreferences.edit();
        	editor.putString(Config.PREF_KEY_COLLECTOR_WORK_FOLDER, currentWorkFolder);
        	editor.putString(Config.PREF_KEY_COLLECTOR_WORK_PATH, currentWorkPath);
        	editor.commit();
        	Logger.d("currentWorkPath: " + currentWorkPath);
        	startDataCollectorService();
            return;
        } else {
	        Logger.d("start collector failed");
        }
	}
	
	
	/**
	 * Start a new data collector service and monitor its running state
	 */
	private void startDataCollectorService(){ 
    	updateCollectorConsole("Starting data collector service ... ");
    	// Start and bind to data collector services
        startService(new Intent(getApplicationContext(), DataCollectorService.class));
        
        // Timer object which start as soon user press the Start Data Collector
        // to checks the tcpdump execution in the shell
        final Timer mCollectorStartWatchTimer = new Timer();
        // Timers to get the PS list from the shell every seconds to verify
        // tcpdump execution till mCollectorStartWatchTimer times out*/
        final Timer mCollectorStartTimer = new Timer();
        
        // Watch timer for failed start message of data collector after DATA_COLLECTOR_START_WATCH_TIME
        mCollectorStartWatchTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				if ( ! mApp.isTcpdumpRunning() ) {
					mCollectorService.requestStop();
					Logger.e("Failed to start DataCollector in " + Config.DATA_COLLECTOR_START_WATCH_TIME/1000 + " sec");
					// make sure we delete all of the traces if we don't have tcpdump running
					String currentWorkPath = mPreferences.getString(Config.PREF_KEY_COLLECTOR_WORK_PATH, "");
					boolean flag = mApp.deleteDirectory(new File(currentWorkPath));
					Logger.d("Delete " + currentWorkPath + " " + flag);
					// change controller UI
					dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_COLLECTOR_STOPPED));	
				}
				// Cancel the timers
				mCollectorStartWatchTimer.cancel();
				mCollectorStartTimer.cancel();
			}
		}, Config.DATA_COLLECTOR_START_WATCH_TIME);
        
		// Timer to check start data collector kick-off at every second
        mCollectorStartTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if ( mApp.isTcpdumpRunning() ){
					// Data collector starts now.
					long startTime = System.currentTimeMillis();
					stopWatch.setBase(startTime);
					stopWatch.start();
					// change UI
					dataCollectorStateHandler.sendMessage(Message.obtain(dataCollectorStateHandler, MID_COLLECTOR_STARTED));
					// Stop timers
					mCollectorStartWatchTimer.cancel();
					mCollectorStartTimer.cancel();
				}
			}
		}, Config.DATA_COLLECTOR_START_TICK_TIME, Config.DATA_COLLECTOR_START_TICK_TIME);
    }
	
	/**
	 * Check access root and pop alert dialog if not enabled.
	 * @return Boolean flag indicating if root access allowed.
	 */
	protected boolean checkRootAccess(){
		boolean hasRootAccess = this.phoneUtils.hasRootAccess();
		Logger.i("Checking root accesss: hasRootAccess = " + hasRootAccess);
		if ( !hasRootAccess ){
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.errorMassageNoRootAccess).setTitle(R.string.dialogTitleNoRootAccess);
			builder.setPositiveButton(R.string.dialogButtonOk, new DialogInterface.OnClickListener() {
				@Override   
				public void onClick(DialogInterface dialog, int id) {
		               dialog.cancel();
		        }
		    });
			builder.create().show();
		}
		return hasRootAccess;
	}
	
	/**
	 * Check tcpdump instance and pop alert dialog if it is already running.
	 * @return Boolean flag indicating if root access allowed.
	 */
	protected boolean checkTcpdumpRunning(){
		boolean hasTcpdumpInstance  = mApp.isTcpdumpRunning();
		Logger.i("Checking tcpdump running state: hasTcpdumpInstance = " + hasTcpdumpInstance);
		if ( hasTcpdumpInstance ){
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.errorMassageTcpdumpAlreadyRunning).setTitle(R.string.dialogTitleTcpdumpInstanceRunning);
			builder.setPositiveButton(R.string.dialogButtonKillManually, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mApp.killTcpdump(true);
					Logger.i("Kill tcpdump violantly at " + System.currentTimeMillis());
				}
			});
			builder.setNegativeButton(R.string.dialogButtonKillNoAction, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			builder.create().show();
		}
		return mApp.isTcpdumpRunning();
	}
	
    /**
     * Gets the default Omniperf dump folder name in the HH:MM:SS:DD:MM:YY format.
     * @return The default Omniperf dump folder name.
     */
    private String getDefaultWorkFolderName() {
        final Date systemDate = new Date();
        final Calendar now = Calendar.getInstance();
        final int currenthours = systemDate.getHours();
        final int currentminutes = systemDate.getMinutes();
        final int currentseconds = systemDate.getSeconds();
        final int currentdate = now.get(Calendar.DATE); // java calendar
        int currentmonth = now.get(Calendar.MONTH); // As Jan is defined as 0 in
        currentmonth = currentmonth + 1;
        if (currentmonth >= 13) // As Jan is defined as 0 in java calendar
            currentmonth = 1;
        String currentMonth = Integer.toString(currentmonth);
        String currentDate = Integer.toString(currentdate);
        String currentHours = Integer.toString(currenthours);
        String currentMinutes = Integer.toString(currentminutes);
        String currentSeconds = Integer.toString(currentseconds - 1);

        if (currentmonth < 10) {
            currentMonth = "";
            currentMonth = "0" + currentmonth;
        }
        if (currentdate < 10) {
            currentDate = "";
            currentDate = "0" + currentdate;
        }
        if (currenthours < 10) {
            currentHours = "";
            currentHours = "0" + currenthours;
        }
        if (currentminutes < 10) {
            currentMinutes = "";
            currentMinutes = "0" + currentminutes;
        }
        if (currentseconds < 10) {
            currentSeconds = "";
            currentSeconds = "0" + currentseconds;
        }
        final String folderName = now.get(Calendar.YEAR) + "-" + currentMonth + "-" + currentDate
                + "-" + currentHours + "-" + currentMinutes + "-" + currentSeconds;

        return folderName;
    }
    
    /**
     * Update collector console content.
     * @param message
     */
    private void updateCollectorConsole(String message){
    	Logger.i(message);
    	this.collectorConsoleContent += "\n" + message;
    	this.collectorConsole.setText(this.collectorConsoleContent);
    }
}
