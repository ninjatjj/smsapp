package com.ninjatjj.smsapp.server;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.SmsApplication;
import com.ninjatjj.smsapp.Utils;
import com.ninjatjj.smsapp.core.Constants;
import com.ninjatjj.smsapp.core.MessageListener;
import com.ninjatjj.smsapp.core.SignalStrengthListener;
import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.ui.ContactManager;

public class SmsApplicationServer extends Service implements SmsApplication {

	private AlarmManager timer;

	private boolean useBluetooth;
	private boolean useWifi;

	public static final UUID MY_UUID_SECURE = UUID
			.fromString("5B92EE2B-75AB-4C71-AF22-5AF9861D182B");
	public static final int SMSAPP_PORT = 8765;
	// public static final long SAFETY_INTERVAL = 70000;

	private List<MessageListener> messageListeners = new ArrayList<MessageListener>();
	private List<ConnectionListener> connectionListeners = new ArrayList<ConnectionListener>();
	private List<SignalStrengthListener> signalStrengthListeners = new ArrayList<SignalStrengthListener>();

	private Map<String, Sms> pending = new HashMap<String, Sms>();
	private Map<String, Set<Sms>> messages = new HashMap<String, Set<Sms>>();

	private BluetoothServerSocketProcessor bssp;
	private ServerSocketProcessor ssp;

	// Weak reference
	private OnSharedPreferenceChangeListener listener;
	private AtomicBoolean connected = new AtomicBoolean(false);

	public InetAddress wifiAddress;

	public String getWifiAddress() {
		if (wifiAddress == null) {
			return "";
		}
		return wifiAddress.getHostAddress();
	}

	private MessagePoller messagePoller = new MessagePoller("MessagePoller");

	volatile public int acceptedClientCount;
	volatile List<String> acceptedClients = new ArrayList<String>();

	android.net.wifi.WifiManager.MulticastLock lock;

	public class LocalBinder extends Binder {
		public SmsApplicationServer getService() {
			return SmsApplicationServer.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("LocalService", "Received start id " + startId + ": " + intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
				new Thread("SMSAPP-SMS_SENT") {
					public void run() {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						messagePoller.checkNow();
					}
				}.start();
				// Bundle bundle = intent.getExtras();
				// if (bundle != null) {
				// Object[] pdus = (Object[]) bundle.get("pdus");
				// final SmsMessage[] msgs = new SmsMessage[pdus.length];
				// for (int i = 0; i < msgs.length; i++) {
				// msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				// }
				// new Thread("SMSAPP-smsrec") {
				// public void run() {
				// for (int i = 0; i < msgs.length; i++) {
				//
				// Sms sms = new Sms();
				// sms.setAddress(msgs[i].getOriginatingAddress());
				// sms.setBody(msgs[i].getMessageBody());
				// sms.setReceived(new Date(msgs[i]
				// .getTimestampMillis()));
				// messageReceived(sms);
				// }
				// }
				// }.start();
				// }
			} else if (action.equals("tom.SMS_SENT")) {
				final String createdDate = intent.getStringExtra("createdTime");
				if (createdDate != null) {
					new Thread("SMSAPP-SMS_SENT") {
						public void run() {
							releaseMessage(createdDate);
						}
					}.start();
				}
			} else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(
						BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
				case BluetoothAdapter.STATE_ON:
					if (bssp != null) {
						new Thread("SMSAPP-resumeServer") {
							public void run() {
								if (bssp != null) {
									bssp.resumeServer();
								}
							}
						}.start();
					}
					break;
				case BluetoothAdapter.STATE_OFF:
					if (bssp != null) {
						new Thread("SMSAPP-pauseServer") {
							public void run() {
								if (bssp != null) {
									bssp.pauseServer();
								}
							}
						}.start();
					}
					break;
				}
			} else if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
				ConnectivityManager conMan = (ConnectivityManager) context
						.getSystemService(Context.CONNECTIVITY_SERVICE);

				NetworkInfo mobNetInfo = conMan
						.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
				NetworkInfo netInfo = conMan.getActiveNetworkInfo();
				if (netInfo != null
						&& netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					if (ssp != null) {
						new Thread("SMSAPP-resumeServer-wifi") {
							public void run() {
								if (ssp != null) {
									ssp.resumeServer();
								}
							}
						}.start();
					}
				} else {
					if (ssp != null) {
						new Thread("SMSAPP-pauseServer-wifi") {
							public void run() {
								if (ssp != null) {
									ssp.pauseServer();
								}
							}
						}.start();
					}
				}

			}
		}
	};
	private SharedPreferences defaultSharedPreferences;

	private Map<String, Set<Sms>> currentNotification = new HashMap<String, Set<Sms>>();

	// private volatile Notification persistentNotification;

	@Override
	public void onCreate() {

		Utils.logcat("smsapp");

		Log.d("smsapp", "service created");

		super.onCreate();

		timer = (AlarmManager) (SmsApplicationServer.this
				.getSystemService(Context.ALARM_SERVICE));
		((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).listen(
				new PhoneStateListener() {

					@Override
					public void onServiceStateChanged(ServiceState serviceState) {
						// int asu = signalStrength.getGsmSignalStrength();
						// int cdmaDbm = signalStrength.getCdmaDbm();
						// int evdoDbm = signalStrength.getEvdoDbm();
						// Log.d("smsapp", "onSignalStrengthsChanged: " + asu
						// + " " + cdmaDbm + " " + evdoDbm);
						// boolean connected = asu != 0 && cdmaDbm != 0
						// && evdoDbm != 0;
						boolean connected = serviceState.getState() == ServiceState.STATE_IN_SERVICE;
						synchronized (SmsApplicationServer.this.signalStrengthListeners) {
							if (SmsApplicationServer.this.connected.get()
									&& !connected
									|| !SmsApplicationServer.this.connected
											.get() && connected) {
								Log.d("smsapp", "onServiceStateChanged: "
										+ connected);
								SmsApplicationServer.this.connected
										.set(connected);
								for (SignalStrengthListener listener : signalStrengthListeners) {
									listener.onSignalStrengthsChanged(connected);
								}
							}
						}
					}

				}, PhoneStateListener.LISTEN_SERVICE_STATE);

		IntentFilter filter = new IntentFilter();
		filter.setPriority(99999999);
		filter.addAction("android.provider.Telephony.SMS_RECEIVED");
		registerReceiver(receiver, filter);
		IntentFilter filter2 = new IntentFilter();
		filter2.addAction("tom.SMS_SENT");
		registerReceiver(receiver, filter2);
		IntentFilter filter3 = new IntentFilter();
		filter3.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
		registerReceiver(receiver, filter3);
		IntentFilter filter4 = new IntentFilter();
		filter4.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		registerReceiver(receiver, filter4);

		defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		acceptedClientCount = defaultSharedPreferences.getInt(
				"acceptedClientCount", 0);

		for (int i = 1; i <= acceptedClientCount; i++) {
			String intern = defaultSharedPreferences.getString(
					"acceptedClient" + i, null).intern();
			acceptedClients.add(intern);
			Log.d("smsapp", "loaded clientID from store: " + intern);
		}

		if (defaultSharedPreferences.getBoolean("persistentNotification", true)) {
			startForeground();
		}

		try {
			messagePoller.poll();
			messagePoller.start();
		} catch (Exception e) {
			Log.e("smsapp", "Could not poll for messages!", e);
			// TODO report to application that it won't work!
		}

		if (defaultSharedPreferences.getBoolean("useBluetooth", false)) {
			useBluetooth = true;
			bssp = new BluetoothServerSocketProcessor();
			bssp.start();
		}

		if (defaultSharedPreferences.getBoolean("useWifi", true)) {
			useWifi = true;
			ssp = new ServerSocketProcessor();
			ssp.start();
		}

		defaultSharedPreferences
				.registerOnSharedPreferenceChangeListener(listener = new SharedPreferences.OnSharedPreferenceChangeListener() {

					@Override
					public void onSharedPreferenceChanged(
							SharedPreferences sharedPreferences, String key) {
						if (key.equals("useWifi")) {
							SmsApplicationServer.this.useWifi = sharedPreferences
									.getBoolean("useWifi", true);
							if (useWifi) {
								if (ssp == null) {
									ssp = new ServerSocketProcessor();
									ssp.start();
								} else {
									ConnectivityManager conMan = (ConnectivityManager) SmsApplicationServer.this
											.getApplicationContext()
											.getSystemService(
													Context.CONNECTIVITY_SERVICE);
									NetworkInfo netInfo = conMan
											.getActiveNetworkInfo();
									if (netInfo != null
											&& netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
										ssp.resumeServer();
									}
								}
							} else {
								ssp.pauseServer();
							}
						} else if (key.equals("useBluetooth")) {
							SmsApplicationServer.this.useBluetooth = sharedPreferences
									.getBoolean("useBluetooth", false);
							if (useBluetooth) {
								if (bssp == null) {
									bssp = new BluetoothServerSocketProcessor();
									bssp.start();
								} else {
									bssp.resumeServer();
								}
							} else {
								bssp.pauseServer();
							}
						}

					}

				});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
		messagePoller.shutdown();
		if (bssp != null) {
			bssp.stopServer();
		}
		if (ssp != null) {
			ssp.stopServer();
		}
		NotificationManager notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (defaultSharedPreferences.getBoolean("persistentNotification", true)) {
			stopForeground();
		}
		notificationManger.cancel(02);
	}

	public Set<Sms> getMessages() {
		Set<Sms> clone = Collections.synchronizedSet(new TreeSet<Sms>());
		synchronized (messages) {
			for (Set<Sms> messages : this.messages.values()) {
				for (Sms item : messages) {
					clone.add(item);
				}
			}
		}
		return clone;
	}

	public void sendMessage(String address, String message) {
		Date createdTime = new Date();

		Intent sentIntent = new Intent("tom.SMS_SENT");
		sentIntent.putExtra("createdTime", createdTime.toString());

		PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, sentIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		Sms sms = new Sms();
		sms.setPrefix("<me>");
		sms.setAddress(address);
		sms.setReceived(createdTime);
		sms.setBody(message);

		pending.put(createdTime.toString(), sms);

		SmsManager smsManager = SmsManager.getDefault();
		smsManager.sendTextMessage(address, null, message, sentPI, null);

		// Not persisting yet, in case we crash
		Log.d("smsapp", "Sending message: " + createdTime.toString());
	}

	void releaseMessage(String createdDate) {
		Sms sms = pending.get(createdDate);
		if (sms != null) {

			// Write it to storage as soon as we determine that it was sent, in
			// a crash we would miss this
			ContentValues values = new ContentValues();
			values.put("address", sms.getAddress());
			values.put("body", sms.getBody());
			values.put("date", sms.getReceived().getTime());
			values.put("read", "1");
			values.put("status", "0");
			values.put("type", "2");
			getContentResolver()
					.insert(Uri.parse("content://sms/sent"), values);

			messagePoller.checkNow();
		} else {
			Log.d("smsapp", "Message could not be found: " + createdDate);
		}
	}

	void markAsRead(Sms sms) {
		ContentValues values = new ContentValues();
		values.put("read", true);
		getContentResolver().update(Uri.parse("content://sms/inbox"), values,
				"_id=" + sms.getId(), null);
	}

	public void deleteThread(String address) {
		Log.d("smsapp", "deleteThread:" + address);
		if (address.startsWith("0")) {
			address = address.substring(1);
		}

		synchronized (messages) {
			Iterator<String> iterator2 = messages.keySet().iterator();
			while (iterator2.hasNext()) {
				String next = iterator2.next();
				if (next.endsWith(address)) {
					Set<Sms> toDelete = messages.get(next);
					Iterator<Sms> iterator = toDelete.iterator();
					while (iterator.hasNext()) {
						Sms sms = iterator.next();
						getContentResolver().delete(
								Uri.parse("content://sms/" + sms.getId()),
								null, null);
					}
				}
			}
		}
		messagePoller.checkNow();
		Log.d("smsapp", "deleteThread done:" + address);
	}

	public void messageReceived(Sms sms) {
		if (!sms.isRead() && sms.getPrefix().length() == 0) {

			if (defaultSharedPreferences.getBoolean("newMessageNotification",
					true)) {
				Intent intent = new Intent(this,
						ClearNotificationConversationActivity.class);
				intent.putExtra("address", sms.getAddress());

				PendingIntent pendingIntent = PendingIntent.getActivity(this,
						01, intent, PendingIntent.FLAG_UPDATE_CURRENT);
				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						getApplicationContext());
				builder.setContentTitle(ContactManager.getDisplayName(
						getContentResolver(), sms.getAddress()));
				builder.setContentIntent(pendingIntent);
				builder.setSmallIcon(R.drawable.icon_msg);
				builder.setPriority(0);
				Notification notification = builder.build();
				notification.defaults |= Notification.DEFAULT_ALL;
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				NotificationManager notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManger.notify(02, notification);
			}

			Set<Sms> set2 = currentNotification.get(sms.getAddress());
			if (set2 == null) {
				set2 = new TreeSet<Sms>();
				currentNotification.put(sms.getAddress(), set2);
			}
			set2.add(sms);
		}
	}

	public void clearMessageReceived() {

		boolean cleared = false;
		Iterator<Set<Sms>> iterator2 = currentNotification.values().iterator();
		while (iterator2.hasNext()) {
			Iterator<Sms> iterator = iterator2.next().iterator();
			while (iterator.hasNext()) {
				Sms next = iterator.next();
				Uri uri = Uri.parse("content://sms/inbox");
				String selection = "address = ? AND body = ? AND read = ?";
				String[] selectionArgs = { next.getAddress(), next.getBody(),
						"0" };

				ContentValues values = new ContentValues();
				values.put("read", true);

				getContentResolver().update(uri, values, selection,
						selectionArgs);
				iterator.remove();
				cleared = true;
			}
		}
		if (cleared) {
			for (MessageListener handler : messageListeners) {
				handler.clearMessageReceived();
			}
			if (defaultSharedPreferences.getBoolean("newMessageNotification",
					true)) {
				NotificationManager notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManger.cancel(02);
			}
		}
	}

	public void addMessageListener(MessageListener main) {
		messageListeners.add(0, main);
	}

	public void removeMessageListener(MessageListener main) {
		messageListeners.remove(main);
	}

	public void addConnectionListener(ConnectionListener main) {
		connectionListeners.add(0, main);
	}

	public void removeConnectionListener(ConnectionListener main) {
		connectionListeners.remove(main);
	}

	private class MessagePoller extends Thread {
		private AtomicBoolean check = new AtomicBoolean(false);
		private Object stop = new Object();
		private volatile boolean stopped;

		public MessagePoller(String name) {
			super(name);
		}

		public void shutdown() {
			synchronized (stop) {
				stopped = true;
				stop.notify();
			}
		}

		public void checkNow() {
			Log.d("smsapp", "checkNow");
			synchronized (stop) {
				check.set(true);
				stop.notify();
			}
			Log.d("smsapp", "checkNow done");
		}

		public void run() {
			while (true) {
				synchronized (stop) {

					if (stopped) {
						break;
					}

					while (true) {
						check.set(false);
						poll();
						if (check.getAndSet(false) == false) {
							break;
						}
					}

					try {
						stop.wait(60000);
					} catch (InterruptedException e) {
						Log.e("smsapp", "Could not wait to stop");
					}
				}
			}
		}

		private void poll() {
			Log.d("smsapp", "poll");
			List<Sms> messages = new ArrayList<Sms>();

			Cursor cursor = getContentResolver().query(
					Uri.parse("content://sms/inbox"), null, null, null, null);
			cursor.moveToFirst();

			if (cursor.getCount() > 0) {
				do {
					Sms sms = new Sms();
					for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
						String columnName = cursor.getColumnName(idx);
						String columnValue = cursor.getString(idx);
						if (columnName.equals("_id")) {
							sms.setId(columnValue);
						} else if (columnName.equals("address")) {
							sms.setAddress(columnValue);
						} else if (columnName.equals("date")) {
							long parseLong = Long.parseLong(columnValue);
							sms.setReceived(new Date(parseLong));
						} else if (columnName.equals("body")) {
							sms.setBody(columnValue);
						} else if (columnName.equals("read")) {
							sms.setRead(columnValue.equals("1"));
						}
					}
					// Log.d("smsapp", "recv check found: " +
					// sms.getReceived()
					// + " " + sms.getAddress() + " " + sms.getBody());
					messages.add(sms);
				} while (cursor.moveToNext());
				cursor.close();
			}

			cursor = getContentResolver().query(
					Uri.parse("content://sms/sent"), null, null, null, null);
			cursor.moveToFirst();

			if (cursor.getCount() > 0) {
				do {
					Sms sms = new Sms();
					sms.setPrefix("<me>");
					sms.setRead(true);
					for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
						String columnName = cursor.getColumnName(idx);
						String columnValue = cursor.getString(idx);
						if (columnName.equals("_id")) {
							sms.setId("" + columnValue);
						} else if (columnName.equals("address")) {
							sms.setAddress(columnValue);
						} else if (columnName.equals("date")) {
							long parseLong = Long.parseLong(columnValue);
							sms.setReceived(new Date(parseLong));
						} else if (columnName.equals("body")) {
							sms.setBody(columnValue);
						}
					}
					messages.add(sms);
				} while (cursor.moveToNext());
				cursor.close();
			}

			Iterator<Set<Sms>> iterator2 = currentNotification.values()
					.iterator();
			boolean doBreak = false;
			while (iterator2.hasNext()) {
				Iterator<Sms> iterator = iterator2.next().iterator();
				while (iterator.hasNext()) {
					Sms next = iterator.next();
					int indexOf = messages.indexOf(next);
					if (indexOf >= 0) {
						Sms sms = messages.get(indexOf);
						if (sms.isRead()) {
							clearMessageReceived();
							doBreak = true;
							break;
						}
					}
				}
				if (doBreak) {
					break;
				}
			}

			Iterator<Set<Sms>> values = SmsApplicationServer.this.messages
					.values().iterator();
			while (values.hasNext()) {
				Iterator<Sms> next = values.next().iterator();
				while (next.hasNext()) {
					Sms sms = next.next();
					int indexOf = messages.indexOf(sms);
					if (indexOf < 0) {
						Log.d("smsapp",
								"message could not be found: "
										+ sms.getPrefix() + " "
										+ sms.getReceived() + " "
										+ sms.getAddress() + " "
										+ sms.getBody());
						next.remove();
						for (MessageListener handler : messageListeners) {
							handler.messageRemoved(sms);
						}
					}
				}
			}

			for (Sms sms : messages) {
				synchronized (SmsApplicationServer.this.messages) {
					String index = sms.getAddress().startsWith("0") ? sms
							.getAddress().substring(1) : sms.getAddress();
					Set<Sms> set = SmsApplicationServer.this.messages
							.get(index);
					if (set == null) {
						set = new TreeSet<Sms>();
						SmsApplicationServer.this.messages.put(index, set);
					}
					if (!set.contains(sms)) {
						set.add(sms);

						for (MessageListener handler : messageListeners) {
							handler.messageReceived(sms);
						}
						messageReceived(sms);
					}
				}
			}
			Log.d("smsapp", "poll done");
		}
	}

	private class BluetoothServerSocketProcessor extends Thread {
		private BluetoothServerSocket mmServerSocket;
		private volatile boolean stopping;
		private BluetoothAdapter bluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		private volatile List<ClientHandler> handlers = new ArrayList<ClientHandler>();

		public BluetoothServerSocketProcessor() {
			super("BluetoothServerSocketProcessor");
		}

		public synchronized void pauseServer() {
			Log.d("smsapp", "BluetoothServerSocketProcessor pauseServer");
			if (mmServerSocket != null) {
				try {
					mmServerSocket.close();
				} catch (IOException e) {
					Log.e("smsapp",
							"Could not close server socket: " + e.getMessage(),
							e);
				}

				mmServerSocket = null;
			}

			Iterator<ClientHandler> iterator = handlers.iterator();
			while (iterator.hasNext()) {
				iterator.next().disconnect();
			}
		}

		public synchronized void resumeServer() {
			if (useBluetooth && bluetoothAdapter.isEnabled()) {
				notify();
			}
		}

		public synchronized void stopServer() {
			stopping = true;
			pauseServer();
		}

		public void run() {
			while (!stopping) {
				try {
					Log.d("smsapp",
							"Waiting for input on: "
									+ MY_UUID_SECURE.toString());
					mmServerSocket = bluetoothAdapter
							.listenUsingRfcommWithServiceRecord("MYYAPP",
									MY_UUID_SECURE);
					while (!stopping) {
						BluetoothSocket socket = mmServerSocket.accept();
						try {
							ClientHandler clientHandler = new ClientHandler(
									socket, socket.getInputStream(),
									socket.getOutputStream(), handlers);
							handlers.add(clientHandler);
							synchronized (signalStrengthListeners) {
								signalStrengthListeners.add(clientHandler);
							}
							clientHandler.start();
						} catch (IOException e) {
							Log.w("smsapp", "Could not create handler: " + e, e);
						}
					}
				} catch (Exception e) {
					synchronized (this) {
						if (!stopping) {
							Log.w("smsapp",
									"Exception from socket, probably disabled: "
											+ e, e);
						}
						if (!stopping
								&& (!useBluetooth || !bluetoothAdapter
										.isEnabled())) {
							pauseServer();
							try {
								wait();
							} catch (InterruptedException e1) {
								Log.e("smsapp", "Could not wait to reconnect");
							}
						}
					}
				}
			}
		}

		public int getConnectedClientCount() {
			return handlers.size();
		}
	}

	private class ServerSocketProcessor extends Thread {
		private volatile boolean stopping;
		// private JmDNS jmdns;
		private ServerSocket serverSocket;
		private volatile List<ClientHandler> handlers = new ArrayList<ClientHandler>();

		public ServerSocketProcessor() {
			super("ServerSocketProcessor");
		}

		public synchronized void pauseServer() {
			Log.d("smsapp", "ServerSocketProcessor pauseServer");
			// if (jmdns != null) {
			//
			// try {
			// jmdns.unregisterAllServices();
			// jmdns.close();
			// } catch (Exception e) {
			// Log.e("smsapp", "Could not close jmdns: " + e.getMessage(), e);
			// }
			// jmdns = null;
			// }

			if (lock != null) {
				lock.release();
				lock = null;
			}

			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					Log.e("smsapp",
							"Could not close server socket: " + e.getMessage(),
							e);
				}
				serverSocket = null;
			}

			Iterator<ClientHandler> iterator = handlers.iterator();
			while (iterator.hasNext()) {
				iterator.next().disconnect();
			}
		}

		public synchronized void resumeServer() {
			if (useWifi) {
				Log.d("smsapp", "ServerSocketProcessor resumeServer");
				WifiManager wifiManager = (WifiManager) getApplicationContext()
						.getSystemService(Context.WIFI_SERVICE);

				for (int i = 0; i < 10; i++) {
					int ipAddress = wifiManager.getConnectionInfo()
							.getIpAddress();
					InetAddress wifiAddress = null;
					if (ipAddress != 0) {
						if (ByteOrder.nativeOrder().equals(
								ByteOrder.LITTLE_ENDIAN)) {
							ipAddress = Integer.reverseBytes(ipAddress);
						}

						byte[] ipByteArray = BigInteger.valueOf(ipAddress)
								.toByteArray();

						try {
							wifiAddress = InetAddress.getByAddress(ipByteArray);
						} catch (UnknownHostException e) {
							Log.e("smsapp", "Could not convert ip address", e);
						}
					}

					if (ipAddress == 0 || wifiAddress == null
							|| wifiAddress.getHostAddress().startsWith("169")) {
						Log.d("smsapp", "waiting for valid ip");
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							Log.e("smsapp", "Could not wait to get valid ip");
						}
					} else {
						notify();
						break;
					}
				}
			}
		}

		public synchronized void stopServer() {
			Log.d("smsapp", "Stopping server");
			stopping = true;
			pauseServer();
		}

		public void run() {
			Log.d("smsapp", "ServerSocketProcessor started");
			while (!stopping) {
				try {

					WifiManager wifiManager = (WifiManager) getApplicationContext()
							.getSystemService(Context.WIFI_SERVICE);
					int ipAddress = wifiManager.getConnectionInfo()
							.getIpAddress();
					// Log.d("smsapp", "Got ip address: " + ipAddress);

					// Convert little-endian to big-endianif needed
					if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
						ipAddress = Integer.reverseBytes(ipAddress);
					}

					byte[] ipByteArray = BigInteger.valueOf(ipAddress)
							.toByteArray();

					wifiAddress = InetAddress.getByAddress(ipByteArray);
					// Log.d("smsapp", "Got wifi address: " + wifiAddress);
					if (wifiAddress == null
							|| wifiAddress.getHostAddress().startsWith("169")) {
						throw new Exception("no wifi address");
					}

					android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
					lock = wifi.createMulticastLock("smsappserver");
					lock.setReferenceCounted(true);
					lock.acquire();

					// try {
					// jmdns = JmDNS.create(wifiAddress);
					// ServiceInfo serviceInfo = ServiceInfo.create(
					// "_workstation._tcp.local.", "AndroidTest",
					// SMSAPP_PORT, "test from android");
					// jmdns.registerService(serviceInfo);
					// } catch (Exception e) {
					// Log.e("smsapp",
					// "Could not register in JMDNS: "
					// + e.getMessage(), e);
					// }

					serverSocket = new ServerSocket(SMSAPP_PORT, 50,
							wifiAddress);

					Log.d("smsapp",
							"Waiting for input on: "
									+ serverSocket.getInetAddress() + ":"
									+ SMSAPP_PORT);
					while (!stopping) {
						Socket socket = serverSocket.accept();
						socket.setKeepAlive(true);
						socket.setTcpNoDelay(true);
						try {
							ClientHandler clientHandler = new ClientHandler(
									socket, socket.getInputStream(),
									socket.getOutputStream(), handlers);
							handlers.add(clientHandler);
							clientHandler.start();
						} catch (IOException e) {
							Log.w("smsapp", "Could not create handler: " + e, e);
						}
					}
				} catch (Exception e) {
					synchronized (this) {
						if (!stopping) {
							Log.w("smsapp", "Could not handle server socket: "
									+ e, e);
							pauseServer();
							try {
								wait();
							} catch (InterruptedException e1) {
								Log.e("smsapp", "Could not wait to reconnect");
							}
						}
					}
				}
			}
		}

		public int getConnectedClientCount() {
			return handlers.size();
		}
	}

	private class ClientHandler extends Thread implements MessageListener,
			SignalStrengthListener {

		private Closeable closeable;
		private DataOutputStream pWriter;
		private DataInputStream bReader;
		private OutputStream outStream;
		private Socket socket;
		private final String remoteName;
		private volatile long lastPing;
		private BroadcastReceiver toCancel;
		private volatile List<ClientHandler> handlers = new ArrayList<ClientHandler>();
		private PendingIntent pi;

		public ClientHandler(BluetoothSocket closeable,
				InputStream inputStream, OutputStream outputStream,
				List<ClientHandler> handlers) throws IOException {
			super("ClientHandler-" + closeable.getRemoteDevice().getAddress());
			this.closeable = closeable;
			this.outStream = outputStream;
			this.bReader = new DataInputStream(inputStream);
			addMessageListener(this);
			remoteName = closeable.getRemoteDevice().getAddress();
			this.handlers = handlers;
		}

		public ClientHandler(Socket socket, InputStream inputStream,
				OutputStream outputStream, List<ClientHandler> handlers) {
			super("ClientHandler-" + socket.getRemoteSocketAddress());
			this.socket = socket;
			this.outStream = outputStream;
			this.bReader = new DataInputStream(inputStream);
			addMessageListener(this);
			remoteName = socket.getRemoteSocketAddress().toString();
			this.handlers = handlers;
		}

		public synchronized void disconnect() {
			Log.d("smsapp", "disconnecting: " + remoteName);
			try {
				if (closeable != null) {
					closeable.close();
					closeable = null;
				}
				if (socket != null) {
					socket.close();
					socket = null;
				}
			} catch (IOException e) {
				Log.e("smsapp",
						"Could not close server socket: " + e.getMessage(), e);
			}
			synchronized (signalStrengthListeners) {
				signalStrengthListeners.remove(this);
			}
			messageListeners.remove(this);
			if (toCancel != null) {
				Log.d("smsapp", "cancelling timer");
				timer.cancel(pi);
				unregisterReceiver(toCancel);
				toCancel = null;
			}
		}

		public void messageReceived(Sms sms) {
			try {
				synchronized (pWriter) {
					pWriter.writeByte(Constants.MSG_RECEIVED);
					pWriter.writeUTF(sms.getId());
					pWriter.writeUTF(sms.getAddress());
					pWriter.writeLong(sms.getReceived().getTime());
					pWriter.writeUTF(sms.getBody());
					pWriter.writeUTF(sms.getPrefix());
					pWriter.flush();
				}
			} catch (Exception e) {
				Log.w("smsapp", "Could not notify of received message");
			}
		}

		@Override
		public void onSignalStrengthsChanged(boolean connected) {
			try {
				synchronized (pWriter) {
					pWriter.writeByte(Constants.SIGNAL_STRENGTH_CHANGED);
					pWriter.writeBoolean(connected);
					pWriter.flush();
				}
			} catch (Exception e) {
				Log.w("smsapp", "Could not notify of signal strength message");
			}
		}

		@Override
		public void messageRemoved(Sms sms) {
			try {
				synchronized (pWriter) {
					pWriter.writeByte(Constants.MSG_REMOVED);
					pWriter.writeUTF(sms.getId());
					pWriter.writeUTF(sms.getAddress());
					pWriter.writeLong(sms.getReceived().getTime());
					pWriter.writeUTF(sms.getBody());
					pWriter.writeUTF(sms.getPrefix());
					pWriter.flush();
				}
			} catch (Exception e) {
				Log.w("smsapp", "Could not notify of received message");
			}
		}

		@Override
		public void clearMessageReceived() {
			try {
				synchronized (pWriter) {
					pWriter.writeByte(Constants.CLEAR_MESSAGE_RECEIVED);
					pWriter.flush();
				}
			} catch (Exception e) {
				Log.w("smsapp", "Could not notify of received message");
			}
		}

		public void run() {
			try {
				// Perform handshake
				String readLine = bReader.readUTF();
				if (readLine.equals("smsapp")) {

					if (socket != null) {

						String clientID = bReader.readUTF().intern();
						boolean votedOK = true;
						boolean voted = false;

						synchronized (acceptedClients) {
							if (!acceptedClients.contains(clientID)) {
								Log.d("smsapp", "was new client: " + clientID);
								Iterator<String> iterator = acceptedClients
										.iterator();
								while (iterator.hasNext()) {
									Log.d("smsapp", "already accepted: "
											+ iterator.next());
								}
								Log.d("smsapp", "will ask for acceptance");
								if (connectionListeners.size() > 0) {
									voted = true;
									votedOK = connectionListeners.get(0)
											.vetoConnection(clientID);
									if (votedOK) {
										Log.d("smsapp", "voted OK");
										Editor edit = defaultSharedPreferences
												.edit();
										edit.putInt("acceptedClientCount",
												++acceptedClientCount);
										edit.putString("acceptedClient"
												+ acceptedClientCount, clientID);
										edit.commit();
										acceptedClients.add(clientID);
										Log.d("smsapp",
												"added accepted client: "
														+ clientID);
									} else {
										Log.d("smsapp", "voted no");
									}
								} else {
									Log.d("smsapp",
											"no-one available to ask for client acceptance");
								}
							} else {
								voted = true;
								// Log.d("smsapp",
								// "was previously accepted client: "
								// + clientID);
							}
						}
						if (!votedOK || !voted) {
							disconnect();
							handlers.remove(this);
							return;
						} else {
							Log.d("smsapp", "Client connected: " + clientID);
						}
					}

					for (ConnectionListener handler : connectionListeners) {
						handler.connectionStateChange(getConnectedClientCount());
					}
					if (defaultSharedPreferences.getBoolean(
							"persistentNotification", true)) {
						// persistentNotification.number =
						// getConnectedClientCount();
						// NotificationManager notificationManger =
						// (NotificationManager)
						// getSystemService(Context.NOTIFICATION_SERVICE);
						// notificationManger.notify(01,
						// persistentNotification);
						startForeground();
					}

					pWriter = new DataOutputStream(outStream);
					pWriter.writeUTF("smsappserver");
					Set<Sms> messages = getMessages();
					pWriter.writeInt(messages.size());
					for (Sms sms : messages) {
						pWriter.writeUTF(sms.getId());
						pWriter.writeUTF(sms.getAddress());
						pWriter.writeLong(sms.getReceived().getTime());
						pWriter.writeUTF(sms.getBody());
						pWriter.writeUTF(sms.getPrefix());
						// pWriter.writeBoolean(sms.isRead()); // TODO readd
						pWriter.flush();
					}
					lastPing = System.currentTimeMillis();

					Iterator<Set<Sms>> iterator2 = currentNotification.values()
							.iterator();
					while (iterator2.hasNext()) {
						Iterator<Sms> iterator = iterator2.next().iterator();
						while (iterator.hasNext()) {
							messageReceived(iterator.next());
						}
					}

					synchronized (signalStrengthListeners) {
						onSignalStrengthsChanged(connected.get());
						signalStrengthListeners.add(this);
					}

					toCancel = new BroadcastReceiver() {
						@Override
						public void onReceive(Context c, Intent i) {
							if (System.currentTimeMillis() - lastPing > Constants.PING_RATE * 2) {
								Log.w("smsapp", "PingHandler-" + remoteName
										+ "did not receive a ping within: "
										+ (Constants.PING_RATE * 2) / 1000
										+ " seconds, last ping was at: "
										+ new Date(lastPing));
								disconnect();
							} else {
								try {
									// Log.d("smsapp", "PingHandler-" +
									// remoteName
									// + " sending ping");
									synchronized (pWriter) {
										pWriter.writeByte(Constants.PING);
										pWriter.flush();
									}
								} catch (Throwable e) {
									Log.w("smsapp",
											"PingHandler-"
													+ remoteName
													+ " disconnecting as could not ping",
											e);
									disconnect();
								}
							}
						}
					};
					registerReceiver(
							toCancel,
							new IntentFilter(
									"com.ninjatjj.smsapp.server.SmsApplicationServer.wake.remoteName"));
					pi = PendingIntent
							.getBroadcast(
									SmsApplicationServer.this,
									0,
									new Intent(
											"com.ninjatjj.smsapp.server.SmsApplicationServer.wake.remoteName"),
									0);
					timer.setRepeating(
							AlarmManager.ELAPSED_REALTIME_WAKEUP,
							SystemClock.elapsedRealtime() + Constants.PING_RATE,
							Constants.PING_RATE, pi);

					while (true) {
						int command = bReader.readByte();
						switch (command) {
						case Constants.SEND_SMS:
							sendMessage(bReader.readUTF(), bReader.readUTF());
							break;
						case Constants.DELETE_THREAD:
							deleteThread(bReader.readUTF());
							break;
						case Constants.CLEAR_MESSAGE_RECEIVED:
							SmsApplicationServer.this.clearMessageReceived();
							break;
						case Constants.PING:
							lastPing = System.currentTimeMillis();
							Log.d("smsapp", "PingHandler-" + remoteName
									+ " Received ping");
							break;
						default:
							throw new Exception("Unknown command: " + command);
						}
					}
				}
			} catch (Exception e) {
				Log.e("smsapp", "PingHandler-" + remoteName
						+ " could not read: " + e, e);
			} finally {
				disconnect();
				handlers.remove(this);

				for (ConnectionListener handler : connectionListeners) {
					handler.connectionStateChange(getConnectedClientCount());
				}
				if (defaultSharedPreferences.getBoolean(
						"persistentNotification", true)) {
					// persistentNotification.number =
					// getConnectedClientCount();
					// NotificationManager notificationManger =
					// (NotificationManager)
					// getSystemService(Context.NOTIFICATION_SERVICE);
					// notificationManger.notify(01, persistentNotification);
					startForeground();
				}
			}
		}
	}

	public int getConnectedClientCount() {
		int count = 0;
		if (bssp != null) {
			count += bssp.getConnectedClientCount();
		}

		if (ssp != null) {
			count += ssp.getConnectedClientCount();
		}

		return count;
	}

	public boolean hasUnread(String address) {
		return currentNotification.get(address) != null
				&& currentNotification.get(address).size() != 0;
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
		builder.setNumber(getConnectedClientCount());
		Notification notification = builder.getNotification();
		notification.number = getConnectedClientCount();
		startForeground(01, notification);
	}

	public void stopForeground() {
		stopForeground(true);
	}
}
