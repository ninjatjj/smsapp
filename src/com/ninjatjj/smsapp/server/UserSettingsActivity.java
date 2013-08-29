package com.ninjatjj.smsapp.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.StartActivity;

public class UserSettingsActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener, ConnectionListener {

	protected static final int CONNECTION_OK = 1;

	private SmsApplicationServer application;

	private Preference connectedClientsCountItem;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CONNECTION_OK:
				final BooleanWrapper wrapper = (BooleanWrapper) msg.obj;
				wrapper.showDialog(UserSettingsActivity.this);
				break;
			}
		}
	};

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		Log.d("smsapp", "preference changed: " + key);

		if (key.equals("persistentNotification")) {

			if (settings.getBoolean("persistentNotification", true)) {
				application.startForeground();
			} else {
				application.stopForeground();
			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			application = ((SmsApplicationServer.LocalBinder) service)
					.getService();

			Preference wifiAddress = findPreference("wifiAddress");
			wifiAddress.setSummary(application.getWifiAddress());

			UserSettingsActivity.this.connectedClientsCountItem = findPreference("clients");
			connectedClientsCountItem.setSummary(""
					+ application.getConnectedClientCount());

			application.addConnectionListener(UserSettingsActivity.this);

		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.layout.serversettings);

		Preference changeMode = (Preference) findPreference("changeMode");
		changeMode
				.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

					@Override
					public boolean onPreferenceClick(Preference arg0) {
						Context context = UserSettingsActivity.this
								.getApplicationContext();

						SharedPreferences defaultSharedPreferences = PreferenceManager
								.getDefaultSharedPreferences(UserSettingsActivity.this);

						Editor edit = defaultSharedPreferences.edit();
						edit.remove("operationMode");
						edit.commit();

						Intent mStartActivity = new Intent(context,
								StartActivity.class);
						int mPendingIntentId = 123456;
						PendingIntent mPendingIntent = PendingIntent
								.getActivity(context, mPendingIntentId,
										mStartActivity,
										PendingIntent.FLAG_CANCEL_CURRENT);
						AlarmManager mgr = (AlarmManager) context
								.getSystemService(Context.ALARM_SERVICE);
						mgr.set(AlarmManager.RTC,
								System.currentTimeMillis() + 100,
								mPendingIntent);
						finish();
						return true;
					}
				});

		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);

		bindService(new Intent(this, SmsApplicationServer.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		if (application != null) {
			application.removeConnectionListener(UserSettingsActivity.this);
		}
		unbindService(mConnection);
	}

	@Override
	public boolean vetoConnection(String clientID) {
		final BooleanWrapper ok = new BooleanWrapper(clientID);
		handler.obtainMessage(CONNECTION_OK, ok).sendToTarget();
		return ok.getValue();
	}

	@Override
	public void connectionStateChange(final int numberOfClients) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				connectedClientsCountItem.setSummary("" + numberOfClients);
			}
		});
	}
}