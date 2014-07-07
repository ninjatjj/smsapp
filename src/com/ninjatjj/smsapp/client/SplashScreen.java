package com.ninjatjj.smsapp.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ninjatjj.smsapp.R;

public class SplashScreen extends Activity {
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case R.id.stopNexit:
			stopService(new Intent(SmsApplicationClient.class.getName()));
			finish();

			System.exit(0);
			return true;
		case R.id.settings:
			startActivity(new Intent(this, UserSettingsActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// Initiating Menu XML file (menu.xml)
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.layout.menu_splash, menu);
		return true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);

		boolean startAtBoot = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				"startAtBoot", false);
		if (startAtBoot) {
			startService(new Intent(SmsApplicationClient.class.getName()));
		}

		bindService(new Intent(this, SmsApplicationClient.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (application != null && application.getClient().canConnect()) {
			finish();
		} else if (PreferenceManager.getDefaultSharedPreferences(this)
				.getString("operationMode", null) == null) {
			stopService(new Intent(SmsApplicationClient.class.getName()));
			finish();

			System.exit(0);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mConnection);
	}

	private SmsApplicationClient application;

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			application = ((SmsApplicationClient.LocalBinder) service)
					.getService();

			if (application.getClient().canConnect()) {
				Intent i = new Intent(SplashScreen.this,
						ConversationsActivity.class);
				startActivity(i);
			} else {
				startActivity(new Intent(SplashScreen.this,
						UserSettingsActivity.class));
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			application = null;
		}
	};
}