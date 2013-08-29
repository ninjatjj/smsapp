package com.ninjatjj.smsapp.client;

import java.util.UUID;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.StartActivity;
import com.ninjatjj.smsapp.core.client.ClientConnectionListener;

public class UserSettingsActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener, ClientConnectionListener {

	private boolean alertVisible = false;

	protected static final int CANNOT_CONNECT = 3;
	protected static final int LONG_CONNECT = 6;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CANNOT_CONNECT:
				Toast.makeText(UserSettingsActivity.this,
						"Could not connect: " + (String) msg.obj,
						Toast.LENGTH_SHORT).show();
				break;
			case LONG_CONNECT:
				AlertBox(
						"Taking a while to connect",
						"It is taking a long time to connect, have you accepted this device on the server?",
						false);
				break;
			}
		}
	};

	private SmsApplicationClient application;
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			application = ((SmsApplicationClient.LocalBinder) service)
					.getService();
			application.getClient().addClientConnectionListener(
					UserSettingsActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			application = null;
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
		} else if (key.equals("useBluetooth")) {
			// Reset other items
			CheckBoxPreference p = (CheckBoxPreference) findPreference("useBluetooth");
			if (p.isChecked()) {
				p = (CheckBoxPreference) findPreference("useWifi");
				p.setChecked(false);

				if (BluetoothAdapter.getDefaultAdapter() == null) {
					AlertBox("Warning", "Bluetooth Not supported", false);
				}
				if (BluetoothAdapter.getDefaultAdapter() != null) {
					if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
						Intent enableBtIntent = new Intent(
								BluetoothAdapter.ACTION_REQUEST_ENABLE);
						startActivity(enableBtIntent);
					}
				}
				if (settings.getBoolean("useBluetooth", false)
						&& settings.getString("remoteAddress", null) == null) {
					startActivity(new Intent(
							"android.bluetooth.devicepicker.action.LAUNCH")
							.putExtra(
									"android.bluetooth.devicepicker.extra.NEED_AUTH",
									false)
							.putExtra(
									"android.bluetooth.devicepicker.extra.FILTER_TYPE",
									0)
							.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
				}
			} else {
				application.disconnect();
				Intent i = new Intent(UserSettingsActivity.this,
						SplashScreen.class);
				startActivity(i);
			}
		} else if (key.equals("useWifi")) {
			// Reset other items
			CheckBoxPreference p = (CheckBoxPreference) findPreference("useWifi");
			if (p.isChecked()) {
				p = (CheckBoxPreference) findPreference("useBluetooth");
				p.setChecked(false);

				String address = settings.getString("wifiAddress", null);
				if (address != null) {
					String uuid = settings.getString("uuid", null);
					application.getClient().setSocketConnectionDetails(address,
							"8765", uuid);
					Intent i = new Intent(UserSettingsActivity.this,
							ConversationsActivity.class);
					startActivity(i);
				}
			} else {
				application.disconnect();
				Intent i = new Intent(UserSettingsActivity.this,
						SplashScreen.class);
				startActivity(i);
			}
		} else if (key.equals("wifiAddress")) {
			String address = settings.getString("wifiAddress", null);
			if (address != null) {
				String uuid = settings.getString("uuid", null);
				if (uuid == null) {
					uuid = UUID.randomUUID().toString();
					Editor edit = settings.edit();
					edit.putString("uuid", uuid);
					edit.commit();
				}
				application.getClient().setSocketConnectionDetails(address,
						"8765", uuid);

				Intent i = new Intent(UserSettingsActivity.this,
						ConversationsActivity.class);
				startActivity(i);
			}
			Preference findPreference = findPreference("wifiAddress");
			findPreference.setSummary(address);
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.layout.clientsettings);

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

		SharedPreferences defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		Preference findPreference = findPreference("wifiAddress");
		findPreference.setSummary(defaultSharedPreferences.getString(
				"wifiAddress", null));
		bindService(new Intent(this, SmsApplicationClient.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (application != null) {
			application.getClient().removeClientConnectionListener(this);
		}
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		unbindService(mConnection);
	}

	public void AlertBox(String title, String message, final boolean finish) {
		if (!alertVisible || finish) {
			alertVisible = true;
			new AlertDialog.Builder(this)
					.setTitle(title)
					.setMessage(message + (finish ? " Press OK to exit" : ""))
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface arg0,
										int arg1) {
									if (finish) {
										finish();
									} else {
										alertVisible = false;
									}
								}
							}).show();
		}
	}

	@Override
	public void connectionStateChange(boolean connected) {
		// TODO Auto-generated method stub

	}

	@Override
	public void cannotConnect(String reason) {
		handler.obtainMessage(CANNOT_CONNECT, 0, 0, reason).sendToTarget();
	}

	@Override
	public void longTimeToConnect() {
		handler.obtainMessage(LONG_CONNECT).sendToTarget();
	}
}