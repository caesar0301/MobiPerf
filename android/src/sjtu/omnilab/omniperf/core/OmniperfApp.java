package sjtu.omnilab.omniperf.core;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import sjtu.omnilab.omniperf.R;
import sjtu.omnilab.omniperf.R.string;
import sjtu.omnilab.omniperf.activities.OmniperfMainActivity;
import sjtu.omnilab.omniperf.utils.Logger;
import sjtu.omnilab.omniperf.utils.PhoneUtils;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Represents the OmniperfApp application. The OmniperfApp class
 * provides methods for storing, getting, and setting data that is used during
 * the life cycle of the application.
 * */
public class OmniperfApp extends Application{
	
	/** The internal storage folder of Omniperf*/
    public File INTERNAL_DATA_PATH;
    
    /** The external folder of Omniperf to store collected data. */
    public File EXTERNAL_ROOT_FOLDER;
    
    /** indicator about whether SD card is mounted */
    public boolean isSDCardMounted;
    
    /** This arbitrary id is private to Omniperf */
    private static final int NOTIFICATION_ID = 98765;
    
    /** Notification manager for Omniperf. */
    public NotificationManager mNotificationManager;
    
    /** Global notification for Omniperf. */
    private Notification mNotification;
    
    /** Global phone utilities */
    private PhoneUtils phoneUtils;
    
    /** Global alarm manager */
    private AlarmManager alarmManager;
    
    /** Global battery power manager */
    private BatteryCapPowerManager powerManager;
    
    /** Shared preferences of Omniperf */
    private SharedPreferences mPreferences;
    
        
    /**
     * Handles processing when an OmniperfApp object is created. Overrides
     * the android.app.Application#onCreate method.
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate() {
        Logger.d("OmniperfApp onCreate() called");
        super.onCreate();
        
        // Init PhoneUtils
        PhoneUtils.setGlobalContext(this.getApplicationContext());
        phoneUtils = PhoneUtils.getPhoneUtils();
        phoneUtils.registerSignalStrengthListener();
        this.alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        this.powerManager = new BatteryCapPowerManager(Config.DEFAULT_BATTERY_THRESH_PRECENT, this);
        this.INTERNAL_DATA_PATH = this.getFilesDir();
        this.EXTERNAL_ROOT_FOLDER = new File(Environment.getExternalStorageDirectory() + "/Omniperf/");
        this.isSDCardMounted = phoneUtils.checkSDCardMounted();
        // set default running states
        this.mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = this.mPreferences.edit();
        editor.putBoolean(Config.PREF_KEY_IS_PAUSE_REQUESTED, true);
        editor.putBoolean(Config.PREF_KEY_IS_STOP_REQUESTED, false);
        editor.putBoolean(Config.PREF_KEY_IS_SCHEDULER_STARTED, false);
        editor.commit();
    }
    
    /**
     * Starts the Omniperf alert menu notification.
     */
    public void initNotificationManager() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        // Add an icon to the device status bar.
        mNotificationManager.notify(NOTIFICATION_ID, getServiceRunningNotification());
    }

    /**
     * Get the notification that indicates the service is running.
     */ 
    public Notification getServiceRunningNotification() {
        if ( mNotification ==  null ){
            //The intent to launch when the user clicks the expanded notification
            Intent intent = new Intent(this, OmniperfMainActivity.class);
            PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            //This constructor is deprecated in 3.x. But most phones still run 2.x systems
            Notification notice = new Notification(R.drawable.icon_statusbar,
                getString(R.string.notificationSchedulerStarted), System.currentTimeMillis());
            notice.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
            //This is deprecated in 3.x. But most phones still run 2.x systems
            notice.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notificationServiceRunning), pendIntent);
            return notice;
        }
        return mNotification;
    }
    
    @SuppressWarnings("unused")
    public void updateNotificationBar(String notificationMsg) {
        if ( mNotificationManager != null ){
            //The intent to launch when the user clicks the expanded notification
            Intent intent = new Intent(this, OmniperfMainActivity.class);
            PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);  
            //This constructor is deprecated in 3.x. But most phones still run 2.x systems
            Notification notice = new Notification(R.drawable.icon_statusbar, notificationMsg, System.currentTimeMillis());
            //This is deprecated in 3.x. But most phones still run 2.x systems
            notice.setLatestEventInfo(this, getString(R.string.app_name), notificationMsg, pendIntent);
            mNotificationManager.notify(NOTIFICATION_ID, notice);
        }
    }
    
    /**
     * Remove the icon from the device status bar.
     */
    public void removeIconFromStatusBar() {
        if ( mNotificationManager != null )
            mNotificationManager.cancel(NOTIFICATION_ID);
    }
    
    /**
     * Return if it is allowed to schedule experiment at current battery level.
     * @return
     */
    public boolean hasBatteryToScheduleExperiment() {
        return this.powerManager.canScheduleExperiment();
    }
    
    /**
     * Helper function to create directory.
     * @param path
     * @return
     */
    public boolean createDirectory(String path){
    	boolean success = false;
    	File newPath = new File(path);
    	try {
    		if ( ! newPath.exists() || (newPath.exists() && !newPath.isDirectory()) ){
        		if ( newPath.mkdirs() ){
        			Logger.d("Creating new directory succeeds: " + path);
        			success = true;
        		} else {
        			Logger.e("Creating new directory failed: " + path);
        		}
        	} else {
        		Logger.i("Directory already exists: " + path);
        		success = true;
        	}
		} catch (Exception e) {
			Logger.e("Error when creating directory " + path + "\n" + e.toString());
		}
    	return success;
    }
    
    /**
	 * Deletes the specified trace directory from the device with given match pattern.
	 * @param path The trace directory path to be deleted.
	 * @return A boolean value that is "true" if the trace folder was deleted
	 *         successfully, and "false" if it was not.
	 */
	public boolean deleteDirectory(File path) {
		if (path.exists()) {
			// TODO: add pattern matching
			final File[] files = path.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					deleteDirectory(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return (path.delete());
	}
	
	/**
	 * Returns a value indicating whether or not tcpdump is running on the
	 * native shell.
	 * @return A boolean value that is "true" if tcpdump is running on the
	 *         native shell, and is "false" otherwise.
	 */
	public boolean isTcpdumpRunning() {
		boolean isTcpDumpRunning = false;
		try {	
			String line = null;
			line = this.phoneUtils.executePS(Config.TCPDUMP_PROCESS_NAME);		
			String[] rows = line.split("\\n");
			if (rows[0].startsWith("USER")) {
				for (int rowNum = 1; rowNum < rows.length; rowNum++) {
					final String row = rows[rowNum];
					final String[] columns = row.split("\\s+");
					int pNameIndex = columns.length - 1; //process name is the last column
					String pName = columns[pNameIndex];
					if (pName != null && pName.contains(Config.TCPDUMP_PNAME)) {
						isTcpDumpRunning = true;
						break;
					}
				}
			}
		} catch (IOException e) {
			Logger.e(e.getClass().getName() + " thrown by pstcpdump()");
		} catch (InterruptedException e) {
			Logger.e(e.getClass().getName() + " thrown by pstcpdump()");
		}
		return isTcpDumpRunning;
	}
	
	/**
	 * Kill tcpdump processe.
	 */
	@SuppressWarnings("finally")
	public boolean killTcpdump(boolean force) {
		Logger.d("killTcpdump called.");
		boolean success = false;
		try {
			int PID = this.phoneUtils.getProcessID(Config.TCPDUMP_PROCESS_NAME);
			if ( PID != 0 ){
				Logger.d("tcpdump PID = " + PID);
				Process sh = Runtime.getRuntime().exec("su");
				DataOutputStream os = new DataOutputStream(sh.getOutputStream());
				if ( force)
					os.writeBytes("kill -9 " + PID + "\n");
				else
					os.writeBytes("kill " + PID + "\n");
				os.writeBytes("exit\n");
				os.flush();
				// get adb shell stdout and stderr
				StreamClearer stdoutClearer = new StreamClearer(sh.getInputStream(), "stdout", true);
				new Thread(stdoutClearer).start();
				StreamClearer stderrClearer = new StreamClearer(sh.getErrorStream(), "stderr", true);
				new Thread(stderrClearer).start();
				int shExitValue = sh.waitFor();
				Logger.i("kill tcpdump returns exit value: " + shExitValue + " at " + System.currentTimeMillis());
				success = true;
			}
		} catch (IOException e) {
			Logger.e("IOException when killing tcpdump - " + e.toString());
		} catch (InterruptedException e) {
			Logger.e("InterruptedException when killing tcpdump - " + e.toString());
		} finally {
			return success;
		}
	}

	/**
	 * Recall output of native shell excution.
	 * @author chenxm
	 */
	public class StreamClearer implements Runnable {
		InputStream streamToClear = null;
		boolean logStream = false;
		String name = null;
		
		public StreamClearer(InputStream is, String name, boolean logStream){
			streamToClear = is;
			this.name = name;
			this.logStream = logStream;
		}
		
		@Override
		public void run() {
			final BufferedReader reader = new BufferedReader(new InputStreamReader(streamToClear));
			String buf = null;
			if (logStream)
				Logger.i("StreamClearer logging content from shell's " + name);
			try {
				while ((buf = reader.readLine()) != null) {
					buf = buf.trim();
					if (logStream && buf.length() > 0)
						Logger.e(name + ">" + buf + "\n");
				}
			} catch (IOException e) {
				Logger.e("StreamClearer IOException in StreamClearer", e);
			}
			if (logStream)
				Logger.i("StreamClearer done logging content from shell's " + name);
		}
	}
	
	/**
	 * Gets the application version from the manifest file.
	 * @return A string that is the application version.
	 */
	public String getVersion() {
		PackageInfo pInfo = null;
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return pInfo.versionName;
		} catch (NameNotFoundException e) {
			Logger.d("Error in getting package version: " + e.toString());
		}
		return null;
	}
	
	/**
	 * Simple wrapper to show toast in Omniperf.
	 * @param resStringID
	 */
	public void showToast(int resStringID) {
		Toast.makeText(getApplicationContext(),getResources().getString(resStringID), Toast.LENGTH_SHORT).show();
	}
    
    /** Following are POJO methods of OmniperfApp. */
    public PhoneUtils getPhoneUtils(){
        return this.phoneUtils;
    }
    
    public AlarmManager getAlarmManager(){
        return this.alarmManager;
    }
    
    public BatteryCapPowerManager getPowerManager(){
        return this.powerManager;
    }
    
    public SharedPreferences getSharedPreferences(){
    	return this.mPreferences;
    }
}
