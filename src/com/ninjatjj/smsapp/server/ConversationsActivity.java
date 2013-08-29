package com.ninjatjj.smsapp.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ninjatjj.smsapp.NewMessageActivity;
import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.ui.AbstractConversationsActivity;

public class ConversationsActivity extends AbstractConversationsActivity
		implements ConnectionListener {
	protected static final int CONNECTION_OK = 1;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CONNECTION_OK:
				final BooleanWrapper wrapper = (BooleanWrapper) msg.obj;
				wrapper.showDialog(ConversationsActivity.this);
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
			application = ((SmsApplicationServer.LocalBinder) service)
					.getService();
			((SmsApplicationServer) application)
					.addMessageListener(ConversationsActivity.this);
			((SmsApplicationServer) application)
					.addConnectionListener(ConversationsActivity.this);

			for (Sms sms : application.getMessages()) {
				messageReceived(sms);
			}

			connectionStateChange(((SmsApplicationServer) application)
					.getConnectedClientCount());
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			application = null;
		}
	};

	// private MenuItem connectedClientsCountItem;

	@Override
	public void onResume() {
		super.onResume();
		if (PreferenceManager.getDefaultSharedPreferences(this).getString(
				"operationMode", null) == null) {
			stopService(new Intent(SmsApplicationServer.class.getName()));
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mConnection != null) {
			try {
				unbindService(mConnection);
			} catch (Throwable t) {
				Log.e("smsapp", "Could unbind connection", t);
			}
		}

		if (application != null) {
			((SmsApplicationServer) application).removeMessageListener(this);
			((SmsApplicationServer) application).removeConnectionListener(this);
		}
	}

	// Initiating Menu XML file (menu.xml)
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.layout.servermenu, menu);
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu
	 * item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case R.id.settings:
			startActivity(new Intent(this, UserSettingsActivity.class));
			return true;
		case R.id.stopNexit:
			unbindService(mConnection);
			mConnection = null;
			stopService(new Intent(SmsApplicationServer.class.getName()));
			finish();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// @Override
	// public boolean onPrepareOptionsMenu(Menu menu) {
	// this.connectedClientsCountItem = menu.getItem(1);
	// connectedClientsCountItem.setTitle("Clients: "
	// + ((SmsApplicationServer) application)
	// .getConnectedClientCount());
	// return true;
	// }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		startService(new Intent(SmsApplicationServer.class.getName()));

		bindService(new Intent(this, SmsApplicationServer.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void connectionStateChange(final int connectedCount) {
		// if (connectedClientsCountItem != null) {
		// runOnUiThread(new Runnable() {
		// @Override
		// public void run() {
		// connectedClientsCountItem.setTitle("Clients: "
		// + connectedCount);
		// }
		// });
		// }
	}

	@Override
	public boolean vetoConnection(String clientID) {
		final BooleanWrapper ok = new BooleanWrapper(clientID);
		handler.obtainMessage(CONNECTION_OK, ok).sendToTarget();
		return ok.getValue();
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
