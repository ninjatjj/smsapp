package com.ninjatjj.smsapp.client;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import com.ninjatjj.smsapp.core.client.Client;

public class MyClient extends Client {

	private SmsApplicationClient service;
	private AlarmManager timer;
	volatile private String remoteDevice;
	private AtomicBoolean reconnect = new AtomicBoolean(false);
	private Object o;

	public MyClient(SmsApplicationClient service, AlarmManager alarmManager,
			String remoteDevice, String hostname, String port, String uuid) {
		this.service = service;
		this.timer = alarmManager;
		this.remoteDevice = remoteDevice;

		this.hostname = hostname;
		this.port = port;
		this.uuid = uuid;
	}

	@Override
	protected void reconnectImpl() {
		synchronized (reconnect) {
			reconnect.set(true);
			reconnect.notify();
		}
	}

	public void exit() {
		synchronized (reconnect) {
			exit = true;
			reconnect.notify();
		}
	}

	@Override
	public void waitToReconnect(long waitTime) {
		synchronized (reconnect) {
			if (!reconnect.getAndSet(false)) {

				BroadcastReceiver receiver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context c, Intent i) {
						// debug("attempt");
						synchronized (MyClient.this) {
							MyClient.this.notify();
						}
					}
				};
				service.registerReceiver(
						receiver,
						new IntentFilter(
								"com.ninjatjj.smsapp.client.SmsApplicationClient.reconnect"));

				PendingIntent timerTask = PendingIntent
						.getBroadcast(
								service,
								111,
								new Intent(
										"com.ninjatjj.smsapp.client.SmsApplicationClient.reconnect"),
								PendingIntent.FLAG_UPDATE_CURRENT);
				// Make sure this is synced as it might ping you before you wake
				// up
				timer.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
						+ waitTime, timerTask);

				try {
					reconnect.wait();
				} catch (InterruptedException e) {
					warn("Could not wait", e);
				}

				timerTask.cancel();
				service.unregisterReceiver(receiver);
			}
		}
	}

	@Override
	public void waitToPing(long timeSinceLastPing) {

		o = new Object();

		BroadcastReceiver receiverPing = new BroadcastReceiver() {
			@Override
			public void onReceive(Context c, Intent i) {
				// debug("attempt");
				synchronized (o) {
					o.notify();
				}
			}
		};
		service.registerReceiver(receiverPing, new IntentFilter(
				"com.ninjatjj.smsapp.client.SmsApplicationClient.pingCheck"));

		PendingIntent pingCheck = PendingIntent
				.getBroadcast(
						service,
						0,
						new Intent(
								"com.ninjatjj.smsapp.client.SmsApplicationClient.pingCheck"),
						0);

		synchronized (o) {
			timer.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + timeSinceLastPing,
					pingCheck);
			try {
				o.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		pingCheck.cancel();
		service.unregisterReceiver(receiverPing);
		pingCheck = null;
		receiverPing = null;
	}

	@Override
	public void wakeUpPingThread(Thread pingThread) {
		synchronized (o) {
			o.notify();
		}
	}

	private BluetoothSocket socket = null;

	@Override
	public boolean shouldConnectBT() {
		return remoteDevice != null;
	}

	@Override
	public void connectBT() throws Exception {
		BluetoothDevice device = BluetoothAdapter.getDefaultAdapter()
				.getRemoteDevice(remoteDevice);
		socket = device.createRfcommSocketToServiceRecord(UUID
				.fromString("5B92EE2B-75AB-4C71-AF22-5AF9861D182B"));
		BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
		socket.connect();

		uuid = UUID.randomUUID().toString(); // TODO save this
		// connectImpl(socket.getOutputStream(), socket.getInputStream());
	}

	@Override
	public void disconnectBT() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				warn("could not close socket: " + e.getMessage(), e);
			}
			socket = null;
		}
	}

	@Override
	protected void debug(String message) {
		Log.d("smsapp", message);
	}

	@Override
	protected void warn(String message, Throwable e) {
		Log.w("smsapp", message, e);
	}

	@Override
	protected void warn(String message) {
		Log.w("smsapp", message);
	}

	public void setRemoteDevice(String address) {
		this.remoteDevice = address;
	}

	@Override
	public void signalStrengthChanged(boolean readBoolean) {
		service.signalStrengthChanged(readBoolean);

	}
}