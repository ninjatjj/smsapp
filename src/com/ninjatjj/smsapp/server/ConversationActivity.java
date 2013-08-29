package com.ninjatjj.smsapp.server;

import java.io.IOException;
import java.util.Collections;
import java.util.TreeSet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.TextView;

import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.ui.AbstractConversationActivity;
import com.ninjatjj.smsapp.ui.ContactManager;

public class ConversationActivity extends AbstractConversationActivity
		implements ConnectionListener {

	protected static final int CONNECTION_OK = 1;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CONNECTION_OK:
				final BooleanWrapper wrapper = (BooleanWrapper) msg.obj;
				wrapper.showDialog(ConversationActivity.this);
				break;
			}
		}
	};

	private String person;
	private SmsApplicationServer application;
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			application = ((SmsApplicationServer.LocalBinder) service)
					.getService();

			person = ContactManager.getDisplayName(getContentResolver(),
					address);

			TextView findViewById = (TextView) findViewById(com.ninjatjj.smsapp.R.id.conversationWith);
			findViewById.setText(person == null ? address : person);

			TreeSet<Sms> treeSet = new TreeSet<Sms>(Collections.reverseOrder());
			treeSet.addAll(application.getMessages());
			for (Sms sms : treeSet) {
				messageReceived(sms);
			}
			application.addMessageListener(ConversationActivity.this);
			application.addConnectionListener(ConversationActivity.this);

			connectionStateChange(application.getConnectedClientCount());
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
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		bindService(new Intent(this, SmsApplicationServer.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	protected void onResume() {
		super.onResume();
		if (application != null) {
			application.clearMessageReceived();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (application != null) {
			application.removeMessageListener(this);
			application.removeConnectionListener(this);
		}
		unbindService(mConnection);
	}

	@Override
	public void connectionStateChange(final int connectedCount) {
	}

	@Override
	public boolean vetoConnection(String clientID) {
		final BooleanWrapper ok = new BooleanWrapper(clientID);
		handler.obtainMessage(CONNECTION_OK, ok).sendToTarget();
		return ok.getValue();
	}

	@Override
	protected void sendMessage(String address, String message)
			throws InterruptedException, IOException {

		application.sendMessage(address, message);
	}

	@Override
	protected boolean connected() {
		return true;
	}

	// @Override
	// public void deleteMessage(Sms sms) {
	// application.deleteMessage(sms);
	// }
}
