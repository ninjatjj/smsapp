package com.ninjatjj.smsapp.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.SmsApplication;
import com.ninjatjj.smsapp.Utils;
import com.ninjatjj.smsapp.core.MessageListener;
import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.core.client.Client;
import com.ninjatjj.smsapp.core.client.ClientConnectionListener;
import com.ninjatjj.smsapp.ui.ContactManager;

public class SmsApplicationClient extends Service implements
		ClientConnectionListener, MessageListener, SmsApplication {
	private MyClient myClient;
	volatile private String uuid;
	private SharedPreferences defaultSharedPreferences;

	private Map<String, Set<Sms>> currentNotification = new HashMap<String, Set<Sms>>();

	private final BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, final Intent intent) {
			final String action = intent.getAction();
			if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
				ConnectivityManager conMan = (ConnectivityManager) context
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo netInfo = conMan.getActiveNetworkInfo();
				if (netInfo != null
						&& netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					if (myClient.shouldConnectWifi()) {
						myClient.reconnect();
					}
				}
			}
		}
	};

	public class LocalBinder extends Binder {
		public SmsApplicationClient getService() {
			return SmsApplicationClient.this;
		}
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new LocalBinder();
	private boolean phoneConnected;

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("LocalService", "Received start id " + startId + ": " + intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onCreate() {

		Utils.logcat("smsappclient");

		Log.d("smsapp", "service created");

		super.onCreate();

		IntentFilter filter4 = new IntentFilter();
		filter4.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		registerReceiver(receiver, filter4);

		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				"persistentNotification", true)) {
			startForeground();
		}

		defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		uuid = PreferenceManager.getDefaultSharedPreferences(
				SmsApplicationClient.this).getString("uuid", null);
		myClient = new MyClient(this,
				(AlarmManager) (this.getSystemService(Context.ALARM_SERVICE)),
				PreferenceManager.getDefaultSharedPreferences(
						SmsApplicationClient.this).getString("remoteAddress",
						null), PreferenceManager.getDefaultSharedPreferences(
						SmsApplicationClient.this).getString("wifiAddress",
						null), "8765", uuid);
		myClient.addMessageListener(SmsApplicationClient.this);
		myClient.addClientConnectionListener(SmsApplicationClient.this);

		connectionStateChange(false);
	}

	@Override
	public void onDestroy() {

		super.onDestroy();
		unregisterReceiver(receiver);

		myClient.removeMessageListener(SmsApplicationClient.this);
		myClient.removeClientConnectionListener(SmsApplicationClient.this);

		disconnect();

		NotificationManager notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				"persistentNotification", true)) {
			stopForeground();
		}
		notificationManger.cancel(02);
		notificationManger.cancel(03);
		notificationManger.cancel(04);

		myClient.exit();
		System.exit(0);
	}

	public Client getClient() {
		return myClient;
	}

	@Override
	public void connectionStateChange(boolean connected) {
		processNotification();
	}

	@Override
	public void messageReceived(Sms sms) {
		if (!sms.isRead() && sms.getPrefix().length() == 0) {
			Set<Sms> set2 = currentNotification.get(sms.getAddress());
			if (set2 == null) {
				set2 = new TreeSet<Sms>();
				currentNotification.put(sms.getAddress(), set2);
			}
			set2.add(sms);
			processNotification();
		}
	}

	@Override
	public void clearMessageReceived() {
		boolean cleared = currentNotification.size() > 0;
		currentNotification.clear();
		if (cleared) {
			processNotification();
		}
	}

	@Override
	public void messageRemoved(Sms sms) {
	}

	@Override
	public void cannotConnect(String reason) {
	}

	@Override
	public void longTimeToConnect() {
		// TODO Auto-generated method stub

	}

	public void disconnect() {
		myClient.setSocketConnectionDetails(null, "8765", myClient.uuid);
		myClient.disconnect();
	}

	@Override
	public void deleteThread(String address) throws IOException {
		myClient.deleteThread(address);
	}

	@Override
	public Set<Sms> getMessages() {
		return myClient.getMessages();
	}

	@Override
	public boolean hasUnread(String address) {
		return currentNotification.get(address) != null
				&& currentNotification.get(address).size() != 0;
	}

	@Override
	public void sendMessage(String address, String message) throws IOException,
			InterruptedException {
		myClient.sendMessage(address, message);
	}

	public void signalStrengthChanged(boolean connected) {
		Log.d("smsapp", "Signal strength changed: " + connected);
		phoneConnected = connected;
		processNotification();

	}

	private void processNotification() {
		NotificationManager notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManger.cancel(02);
		notificationManger.cancel(03);
		notificationManger.cancel(04);

		if (!myClient.connected()) {
			Intent intent = new Intent(SmsApplicationClient.this,
					SplashScreen.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent pendingIntent = PendingIntent.getActivity(
					SmsApplicationClient.this, 01, intent, 0);
			NotificationCompat.Builder builder = new NotificationCompat.Builder(
					getApplicationContext());
			builder.setContentTitle(getResources().getString(R.string.app_name));
			builder.setContentIntent(pendingIntent);
			builder.setSmallIcon(R.drawable.icon_disconnected);// android.R.color.transparent);
			builder.setPriority(0);
			builder.setOngoing(true);
			Notification notification = builder.build();
			notificationManger.notify(03, notification);
		} else if (currentNotification.size() > 0
				&& PreferenceManager.getDefaultSharedPreferences(
						SmsApplicationClient.this).getBoolean(
						"newMessageNotification", true)) {
			String address = currentNotification.keySet()
					.toArray(new String[0])[0];
			Intent intent = new Intent(SmsApplicationClient.this,
					ClearNotificationConversationActivity.class);
			intent.putExtra("address", address);

			PendingIntent pendingIntent = PendingIntent.getActivity(
					SmsApplicationClient.this, 01, intent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			NotificationCompat.Builder builder = new NotificationCompat.Builder(
					getApplicationContext());
			builder.setContentTitle(ContactManager.getDisplayName(
					getContentResolver(), address));
			builder.setContentIntent(pendingIntent);
			builder.setSmallIcon(R.drawable.icon_msg);
			builder.setPriority(0);
			Notification notification = builder.build();
			notification.defaults |= Notification.DEFAULT_ALL;
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			notificationManger.notify(02, notification);
		} else if (!phoneConnected) {
			Intent intent = new Intent(SmsApplicationClient.this,
					SplashScreen.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent pendingIntent = PendingIntent.getActivity(
					SmsApplicationClient.this, 01, intent, 0);
			NotificationCompat.Builder builder = new NotificationCompat.Builder(
					getApplicationContext());
			builder.setContentTitle(getResources().getString(R.string.app_name));
			builder.setContentIntent(pendingIntent);
			builder.setSmallIcon(R.drawable.icon_phone_disconnected);
			builder.setPriority(0);
			builder.setOngoing(true);
			Notification notification = builder.build();
			notificationManger.notify(04, notification);
		}
	}

	public void startForeground() {
		Intent intent = new Intent(this, ConversationsActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 01,
				intent, 0);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				getApplicationContext());
		builder.setPriority(Notification.PRIORITY_MIN);
		builder.setContentTitle(getResources().getString(R.string.app_name));
		builder.setContentIntent(pendingIntent);
		builder.setSmallIcon(R.drawable.icon);// android.R.color.transparent);
		Notification notification = builder.build();
		startForeground(01, notification);
	}

	public void stopForeground() {
		stopForeground(true);
	}
}
