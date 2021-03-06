package com.ninjatjj.smsapp.client;

import java.io.IOException;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class ClearNotificationConversationActivity extends Activity {
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			SmsApplicationClient application = ((SmsApplicationClient.LocalBinder) service)
					.getService();

			try {
				application.getClient().clearMessageReceived();
			} catch (IOException e) {
				Log.e("smsapp", "Problems", e);
			} catch (InterruptedException e) {
				Log.e("smsapp", "Problems", e);
			}

			Intent intent = new Intent(
					ClearNotificationConversationActivity.this,
					ConversationActivity.class);
			intent.putExtra("address", address);
			startActivity(intent);
			finish();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
		}
	};

	private String address;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		bindService(new Intent(this, SmsApplicationClient.class), mConnection,
				Context.BIND_AUTO_CREATE);

		Bundle extras = getIntent().getExtras();
		address = extras.getString("address");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mConnection);
	}

	// @Override
	// protected void onResume() {
	// super.onResume();
	// }
}
