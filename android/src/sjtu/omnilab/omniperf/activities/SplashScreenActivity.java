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

package sjtu.omnilab.omniperf.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import sjtu.omnilab.omniperf.R;
import sjtu.omnilab.omniperf.core.Config;
import sjtu.omnilab.omniperf.core.OmniperfApp;
import sjtu.omnilab.omniperf.utils.Logger;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

/**
 * The splash screen for Speedometer
 */
public class SplashScreenActivity extends Activity {
	
	private OmniperfApp mApp;

    /**
	 * The name of native libs to be pushed to internal data path in sequence as
	 * per the resource id in R file
	 */
	private static final String PROCESS_CPU_MON = "processcpumon.sh";
	private static final String mDataCollectorNativeExe[] = { "key.db", PROCESS_CPU_MON, "tcpdump" };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash_screen);
		// Make sure the splash screen is shown in portrait orientation
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

		this.mApp = (OmniperfApp) getApplication();
		TextView version = (TextView) findViewById(R.id.splash_version);
		version.setText(mApp.getVersion());

		// add delay for splash screen.
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent();
				intent.setClassName(SplashScreenActivity.this.getApplicationContext(),OmniperfMainActivity.class.getName());
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				SplashScreenActivity.this.getApplication().startActivity(intent);
				SplashScreenActivity.this.finish();
			}
		}, Config.SPLASH_SCREEN_DURATION_MSEC);
		
		// a thread to do whatever initialization is needed
		final Thread initializeThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Logger.i("start initializeThread at timestamp:"+ System.currentTimeMillis());
				try {
					// Prepare working directories
					Logger.d("push excutive utilities into native storage");
					for (int resId = 0; resId < mDataCollectorNativeExe.length; resId++)
						PushDataCollectorExeToNative(resId, mDataCollectorNativeExe[resId]);
			        if ( !mApp.createDirectory(mApp.EXTERNAL_ROOT_FOLDER.getPath()) ){
			        	Logger.e("Fail to create Omniperf root storage in SD card.");
			        }
				} catch (IOException e) {
					Logger.e("Exception in initDataCollector", e);
				} finally {
					Logger.i("initialization complete at timestamp:"+ System.currentTimeMillis());
				}
			}
		});
		initializeThread.start();
	}
	
	/**
	 * Copies the key.db,ssldump and tcpdump executable to native Data Collector
	 * application path
	 * @param resourceId
	 *            resource ID to be pushed in native data path
	 * @param exetuableName
	 *            name of the executable
	 * @throws IOException
	 */
	private void PushDataCollectorExeToNative(int resourceId, String exetuableName)
			throws IOException {
		InputStream myInput = null;
		OutputStream myOutput = null;
		try {
			myInput = this.getResources().openRawResource(R.raw.key + resourceId);
			// Open private internal storage file
			myOutput = openFileOutput(exetuableName, Context.MODE_PRIVATE);
			final byte[] buffer = new byte[1024];
			int length;
			while ((length = myInput.read(buffer)) > 0) {
				myOutput.write(buffer, 0, length);
			}
			myOutput.flush();
		} catch (Exception e) {
			Logger.e("Push native files exception - " + e.toString());
		} finally {
			if (myOutput != null) {
				myOutput.close();
			}
			if (myInput != null) {
				myInput.close();
			}
		}
	}
}
