/*
 * Copyright 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package sjtu.omnilab.omniperf.activities;

import java.security.Security;

import sjtu.omnilab.omniperf.core.Config;
import sjtu.omnilab.omniperf.core.DataCollectorService;
import sjtu.omnilab.omniperf.core.MeasurementSchedulerService;
import sjtu.omnilab.omniperf.core.MeasurementTask;
import sjtu.omnilab.omniperf.core.OmniperfApp;
import sjtu.omnilab.omniperf.core.UpdateIntent;
import sjtu.omnilab.omniperf.core.DataCollectorService.CollectorBinder;
import sjtu.omnilab.omniperf.core.MeasurementSchedulerService.SchedulerBinder;
import sjtu.omnilab.omniperf.utils.AccountSelector;
import sjtu.omnilab.omniperf.utils.Logger;
import sjtu.omnilab.omniperf.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The main UI thread that manages different tabs
 */
public class OmniperfMainActivity extends TabActivity {

    public static final String TAG = "Omniperf";

    private TabHost tabHost;
    private OmniperfApp mApp;
    private SharedPreferences mPreferences;
    TextView statusBar, statsBar;
    private BroadcastReceiver receiver;

    private boolean userConsented = false;
    private String selectedAccount = null;

    private static final int DIALOG_CONSENT = 0;
    private static final int DIALOG_ACCOUNT_SELECTOR = 1;
    
    /** Sheduler service */
    private MeasurementSchedulerService scheduler;
    private boolean isBoundScheduler = false;
    private ServiceConnection shedulerServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d("sheduler onServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get
            // LocalService
            // instance
            SchedulerBinder binder = (SchedulerBinder) service;
            scheduler = binder.getService();
            isBoundScheduler = true;
            initializeStatusBar();
            OmniperfMainActivity.this.sendBroadcast(new UpdateIntent("",UpdateIntent.SCHEDULER_CONNECTED_ACTION));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Logger.d("sheduler onServiceDisconnected called");
            isBoundScheduler = false;
        }
    };
    
    /** Data collector service */
   	private DataCollectorService collector;
   	private boolean isBoundCollector = false;
    private ServiceConnection collectorServiceConn = new ServiceConnection() {
    	
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d("collector onServiceConnected called");
            CollectorBinder binder = (CollectorBinder) service;
            collector = binder.getService();
            isBoundCollector = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Logger.d("collector onServiceDisconnected called");
            isBoundCollector = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d("OmniperfMainActivity onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mApp = (OmniperfApp) this.getApplication();
        mPreferences = mApp.getSharedPreferences();

        restoreDefaultAccount();
        if (selectedAccount == null) {
            showDialog(DIALOG_ACCOUNT_SELECTOR);
        } else {
            // double check the user consent selection
            consentDialogWrapper();
        }

        /*
         * Set the DNS cache TTL to 0 such that measurements can be more
         * accurate. However, it is known that the current Android OS does not
         * take actions on these properties but may enforce them in future
         * versions.
         */
        System.setProperty("networkaddress.cache.ttl", "0");
        System.setProperty("networkaddress.cache.negative.ttl", "0");
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");

        Resources res = getResources(); // Resource object to get Drawables
        tabHost = getTabHost(); // The activity TabHost
        TabHost.TabSpec spec; // Resusable TabSpec for each tab
        Intent intent; // Reusable Intent for each tab

        // Do the same for the other tabs
        intent = new Intent().setClass(this, MeasurementCreationActivity.class);
        spec = tabHost.newTabSpec(MeasurementCreationActivity.TAB_TAG).setIndicator("Measure",res.getDrawable(R.drawable.ic_tab_user_measurement)).setContent(intent);
        tabHost.addTab(spec);
        // Creates the user task console tab
        intent = new Intent().setClass(this, ResultsConsoleActivity.class);
        spec = tabHost.newTabSpec(ResultsConsoleActivity.TAB_TAG).setIndicator("Results",res.getDrawable(R.drawable.ic_tab_results_icon)).setContent(intent);
        tabHost.addTab(spec);
        // Creates the measurement schedule console tab
        intent = new Intent().setClass(this,
                MeasurementScheduleConsoleActivity.class);
        spec = tabHost.newTabSpec(MeasurementScheduleConsoleActivity.TAB_TAG).setIndicator("Tasks",res.getDrawable(R.drawable.ic_tab_schedules)).setContent(intent);
        tabHost.addTab(spec);
        // Create the passive measurement cosole tab
        intent = new Intent().setClass(this, DataCollectorActivity.class);
        spec = tabHost.newTabSpec(DataCollectorActivity.TAB_TAG).setIndicator("Collector",res.getDrawable(R.drawable.ic_tab_user_measurement)).setContent(intent);
        tabHost.addTab(spec);

        tabHost.setCurrentTabByTag(MeasurementCreationActivity.TAB_TAG);
        statusBar = (TextView) findViewById(R.id.systemStatusBar);
        statsBar = (TextView) findViewById(R.id.systemStatsBar);

        // We only need one instance of the scheduler thread
        intent = new Intent(this, MeasurementSchedulerService.class);
        this.startService(intent);

        this.receiver = new BroadcastReceiver() {
            @Override
            // All onXyz() callbacks are single threaded
            public void onReceive(Context context, Intent intent) {
                // Update the status bar on SYSTEM_STATUS_UPDATE_ACTION intents
                String statusMsg = intent.getStringExtra(UpdateIntent.STATUS_MSG_PAYLOAD);
                if (statusMsg != null) {
                    updateStatusBar(statusMsg);
                } else if (scheduler != null) {
                    initializeStatusBar();
                }
                String statsMsg = intent.getStringExtra(UpdateIntent.STATS_MSG_PAYLOAD);
                if (statsMsg != null) {
                    updateStatsBar(statsMsg);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
        this.registerReceiver(this.receiver, filter);
    }

    /**
     * Returns the scheduler singleton instance. Should only be called from the UI thread.
     */
    public MeasurementSchedulerService getScheduler() {
        if (isBoundScheduler) {
            return this.scheduler;
        } else {
        	bindToServices();
            return null;
        }
    }
    
    /**
     * Returns the collector singleton instance. Should only be called from the UI thread.
     */
    public DataCollectorService getCollector() {
    	if ( isBoundCollector ){
    		return this.collector;
    	} else {
    		bindToServices();
    		return null;
    	}
    }

    /**
     * Bind the main UI to services
     */
    private void bindToServices() {
        if (!isBoundScheduler) {
            // Bind to the scheduler service if it is not bounded
            Intent service = new Intent(this, MeasurementSchedulerService.class);
            bindService(service, shedulerServiceConn, Context.BIND_AUTO_CREATE);
        }
        if ( !isBoundCollector ){
        	Intent intent = new Intent(this, DataCollectorService.class);
			bindService(intent, collectorServiceConn, Context.BIND_AUTO_CREATE);
        }
    }

    /** 
     * Returns the tab host. Allows child tabs to request focus changes, etc... 
     */
    public TabHost getOmniperfTabHost() {
        return tabHost;
    }

    private void setPauseIconBasedOnSchedulerState(MenuItem item) {
        if (this.scheduler != null && item != null) {
        	boolean isPaused = mPreferences.getBoolean(Config.PREF_KEY_IS_PAUSE_REQUESTED, false);
            if (isPaused) {
                item.setIcon(android.R.drawable.ic_media_play);
                item.setTitle(R.string.menuResume);
            } else {
                item.setIcon(android.R.drawable.ic_media_pause);
                item.setTitle(R.string.menumPause);
            }
        }
    }

    /** Populate the application menu. Only called once per onCreate() */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Adjust menu items depending on system state. Called every time the menu
     * pops up
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        setPauseIconBasedOnSchedulerState(menu.findItem(R.id.menuPauseResume));
        return true;
    }

    /** React to menu item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menuPauseResume:
            if (this.scheduler != null) {
            	boolean isPaused = mPreferences.getBoolean(Config.PREF_KEY_IS_PAUSE_REQUESTED, false);
                if (isPaused) {
                    this.scheduler.resume();
                } else {
                    this.scheduler.pause();
                }
            }
            return true;
        case R.id.menuQuit: {
            Logger.i("User requests exit. Quitting the app");
            quitApp();
            return true;
        }
        case R.id.menuSettings: {
            Intent settingsActivity = new Intent(getBaseContext(),
                    OmniperfPreferenceActivity.class);
            startActivity(settingsActivity);
            return true;
        }
        case R.id.aboutPage: {
            Intent intent = new Intent(getBaseContext(),
                    sjtu.omnilab.omniperf.activities.AboutActivity.class);
            startActivity(intent);
            return true;
        }
        case R.id.menuLog: {
            Intent intent = new Intent(getBaseContext(),
                    SystemConsoleActivity.class);
            startActivity(intent);
            return true;
        }
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Logger.d("OmniperfMainActivity onCreateDialog called");
        switch (id) {
        case DIALOG_CONSENT:
            return showConsentDialog();
        case DIALOG_ACCOUNT_SELECTOR:
            return showAccountDialog();
        default:
            return null;
        }
    }

    private void initializeStatusBar() {
    	boolean isPaused = mPreferences.getBoolean(Config.PREF_KEY_IS_PAUSE_REQUESTED, false);
        if (isPaused ) {
            updateStatusBar(OmniperfMainActivity.this.getString(R.string.pauseMessage));
        } else if (!this.mApp.hasBatteryToScheduleExperiment()) {
            updateStatusBar(OmniperfMainActivity.this.getString(R.string.powerThreasholdReachedMsg));
        } else {
            MeasurementTask currentTask = scheduler.getCurrentTask();
            if (currentTask != null) {
                if (currentTask.getDescription().priority == MeasurementTask.USER_PRIORITY) {
                    updateStatusBar("User task " + currentTask.getDescriptor() + " is running");
                } else {
                    updateStatusBar("System task " + currentTask.getDescriptor() + " is running");
                }
            } else {
                updateStatusBar(OmniperfMainActivity.this.getString(R.string.resumeMessage));
            }
        }
    }

    private void updateStatusBar(String statusMsg) {
        if (statusMsg != null) {
            statusBar.setText(statusMsg);
        }
    }

    private void updateStatsBar(String statsMsg) {
        if (statsMsg != null) {
            statsBar.setText(statsMsg);
        }
    }

    private Dialog showAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Authentication Account");
        final CharSequence[] items = AccountSelector.getAccountList(this
                .getApplicationContext());

        builder.setCancelable(false).setSingleChoiceItems(items, -1,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        Toast.makeText(
                                getApplicationContext(),
                                items[item] + " "
                                        + getString(R.string.selectedString),
                                Toast.LENGTH_SHORT).show();

                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(getBaseContext());
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(Config.PREF_KEY_SELECTED_ACCOUNT,
                                (String) items[item]);
                        editor.commit();
                        dialog.dismiss();
                        // need consent dialog when user first perform the
                        // account selection
                        consentDialogWrapper();
                    }
                });
        return builder.create();
    }

    private Dialog showConsentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final TextView message = new TextView(this);
        final SpannableString s = new SpannableString(getString(R.string.terms));
        Linkify.addLinks(s, Linkify.WEB_URLS);
        message.setText(s);
        message.setTextColor(Color.WHITE);
        message.setLinkTextColor(Color.CYAN);
        message.setTextSize(17);
        message.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setView(message).setCancelable(false)
        	.setPositiveButton(R.string.dialogButtonOk,
                new DialogInterface.OnClickListener() {
        			@Override
                    public void onClick(DialogInterface dialog, int which) {
                        recordUserConsent();
                        // Enable auto start on boot.
                        // setStartOnBoot(true);
                        // Force a checkin now since the one initiated
                        // by the scheduler was likely skipped.
                        // doCheckin();
                    }
                })
           .setNegativeButton(R.string.dialogButtonQuit,
                new DialogInterface.OnClickListener() {
        			@Override
                    public void onClick(DialogInterface dialog, int id) {
                        quitApp();
                    }
                });
        return builder.create();
    }

    @Override
    protected void onStart() {
        Logger.d("OmniperfMainActivity onStart called");
        // Bind to the scheduler service for only once during the lifetime of
        // the activity
        bindToServices();
        super.onStart();
    }

    @Override
    protected void onStop() {
        Logger.d("OmniperfMainActivity onStop called");
        super.onStop();
        if (isBoundScheduler) {
            unbindService(shedulerServiceConn);
            isBoundScheduler = false;
        }
        if ( isBoundCollector ){
        	unbindService(collectorServiceConn);
        	isBoundCollector = false;
        }
    }

    @Override
    protected void onDestroy() {
        Logger.d("OmniperfMainActivity onDestroy called");
        super.onDestroy();
        this.unregisterReceiver(this.receiver);
    }

    private void quitApp() {
        Logger.d("quitApp called");
        if (isBoundScheduler) {
            unbindService(shedulerServiceConn);
            isBoundScheduler = false;
        }
        if (this.scheduler != null) {
            scheduler.requestStop();
        }
        if ( isBoundCollector ){
        	unbindService(collectorServiceConn);
        	isBoundCollector = false;
        }
        if ( this.collector != null ){
        	collector.requestStop();
        }
        // Remove consent on next restart.
        userConsented = true;
        saveConsentState();
        // Disable auto start on boot.
        setStartOnBoot(false);

        this.finish();
        System.exit(0);
    }

    private void doCheckin() {
        if (scheduler != null) {
            scheduler.handleCheckin(true);
        }
    }

    /** Set preference to indicate whether start on boot is enabled. */
    private void setStartOnBoot(boolean startOnBoot) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(getString(R.string.startOnBootPrefKey), startOnBoot);
        editor.commit();
    }

    private void recordUserConsent() {
        userConsented = true;
        saveConsentState();
    }

    /** Save consent state persistent storage. */
    private void saveConsentState() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Config.PREF_KEY_CONSENTED, userConsented);
        editor.commit();
    }

    /**
     * Restore the last used account
     */
    private void restoreDefaultAccount() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        selectedAccount = prefs.getString(Config.PREF_KEY_SELECTED_ACCOUNT,
                null);
    }

    /**
     * Restore measurement statistics from persistent storage.
     */
    private void restoreConsentState() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        userConsented = prefs.getBoolean(Config.PREF_KEY_CONSENTED, false);
    }

    /**
     * A wrapper function to check user consent selection, and generate one if
     * user haven't agreed on.
     */
    private void consentDialogWrapper() {
        restoreConsentState();
        if (!userConsented) {
            // Show the consent dialog. After user select the content
            showDialog(DIALOG_CONSENT);
        }
    }
}
