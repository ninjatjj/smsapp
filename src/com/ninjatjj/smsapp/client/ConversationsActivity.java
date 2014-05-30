package com.ninjatjj.smsapp.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.ninjatjj.smsapp.NewMessageActivity;
import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.core.client.ClientConnectionListener;
import com.ninjatjj.smsapp.ui.AbstractConversationsActivity;

public class ConversationsActivity extends AbstractConversationsActivity
		implements ClientConnectionListener {
	protected static final int CANNOT_CONNECT = 3;
	protected static final int CONNECTED = 4;
	protected static final int NOT_CONNECTED = 5;
	protected static final int LONG_CONNECT = 6;

	private Menu menu;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CANNOT_CONNECT:
				Toast.makeText(ConversationsActivity.this,
						"Could not connect: " + (String) msg.obj,
						Toast.LENGTH_SHORT).show();
				break;
			case LONG_CONNECT:
				AlertBox(
						"Taking a while to connect",
						"It is taking a long time to connect, have you accepted this device on the server?",
						false);
				break;
			case CONNECTED:
				if (menu != null) {
					menu.getItem(0).setEnabled(false);
				}
				break;
			case NOT_CONNECTED:
				if (menu != null) {
					menu.getItem(0).setEnabled(true);
				}
				break;
			}
		}
	};

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			application = ((SmsApplicationClient.LocalBinder) service)
					.getService();
			if (((SmsApplicationClient) application).getClient().connected()) {
				handler.obtainMessage(CONNECTED).sendToTarget();
			} else {
				handler.obtainMessage(NOT_CONNECTED).sendToTarget();
			}

			for (Sms sms : application.getMessages()) {
				messageReceived(sms);
			}
			((SmsApplicationClient) application).getClient()
					.addMessageListener(ConversationsActivity.this);
			((SmsApplicationClient) application).getClient()
					.addClientConnectionListener(ConversationsActivity.this);
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
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		bindService(new Intent(this, SmsApplicationClient.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (application != null) {
			((SmsApplicationClient) application).getClient()
					.removeMessageListener(this);
			((SmsApplicationClient) application).getClient()
					.removeClientConnectionListener(this);
			unbindService(mConnection);
		}
	}

	// Initiating Menu XML file (menu.xml)
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.layout.menu_connect, menu);
		this.menu = menu;
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu
	 * item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case R.id.reconnect:
			((SmsApplicationClient) application).getClient().reconnect();
			return true;
		case R.id.settings:
			startActivity(new Intent(this, UserSettingsActivity.class));
			return true;
		case R.id.stopNexit:
			// Done in onDestroy
			// unbindService(mConnection);
			// mConnection = null;
			stopService(new Intent(SmsApplicationClient.class.getName()));
			finish();
			// System.exit(0);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (((SmsApplicationClient) application).getClient().connected()) {
			menu.getItem(0).setEnabled(false);
		}
		return true;
	}

	@Override
	public void connectionStateChange(boolean connected) {
		if (connected) {
			handler.obtainMessage(CONNECTED).sendToTarget();
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					for (Sms sms : application.getMessages()) {
						messageReceived(sms);
					}
				}
			});
		} else {
			handler.obtainMessage(NOT_CONNECTED).sendToTarget();
		}
	}

	@Override
	public void cannotConnect(String reason) {
		handler.obtainMessage(CANNOT_CONNECT, 0, 0, reason).sendToTarget();
	}

	@Override
	public void longTimeToConnect() {
		handler.obtainMessage(LONG_CONNECT).sendToTarget();
	}

	@Override
	protected String getNewMessageActivityName() {
		return NewMessageActivity.class.getName();
	}

	@Override
	protected String getConversationActivityName() {
		return ConversationActivity.class.getName();
	}
}