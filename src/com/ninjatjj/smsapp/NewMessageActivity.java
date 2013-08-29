package com.ninjatjj.smsapp;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ninjatjj.smsapp.client.SmsApplicationClient;
import com.ninjatjj.smsapp.core.client.ClientConnectionListener;
import com.ninjatjj.smsapp.server.ConnectionListener;
import com.ninjatjj.smsapp.server.SmsApplicationServer;
import com.ninjatjj.smsapp.ui.LimitedEditText;

public class NewMessageActivity extends Activity {

	protected static final int CONNECTION_OK = 1;
	protected static final int CANNOT_CONNECT = 3;
	protected static final int LONG_CONNECT = 6;

	private SmsApplication application;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CONNECTION_OK:
				final BooleanWrapper wrapper = (BooleanWrapper) msg.obj;
				wrapper.showDialog(NewMessageActivity.this);
				break;
			case CANNOT_CONNECT:
				Toast.makeText(NewMessageActivity.this,
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
	private ConnectionListener connectionListener;

	private ClientConnectionListener clientConnectionListener;

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			if (server) {
				application = ((SmsApplicationServer.LocalBinder) service)
						.getService();
				connectionListener = new ConnectionListener() {

					@Override
					public boolean vetoConnection(String clientID) {
						final BooleanWrapper ok = new BooleanWrapper(clientID);
						handler.obtainMessage(CONNECTION_OK, ok).sendToTarget();
						return ok.getValue();
					}

					@Override
					public void connectionStateChange(final int connectedCount) {
					}
				};
				((SmsApplicationServer) application)
						.addConnectionListener(connectionListener);
			} else {

				application = ((SmsApplicationClient.LocalBinder) service)
						.getService();
				clientConnectionListener = new ClientConnectionListener() {

					@Override
					public void connectionStateChange(boolean connected) {
					}

					@Override
					public void cannotConnect(String reason) {
						handler.obtainMessage(CANNOT_CONNECT, 0, 0, reason)
								.sendToTarget();
					}

					@Override
					public void longTimeToConnect() {
						handler.obtainMessage(LONG_CONNECT).sendToTarget();
					}
				};
				((SmsApplicationClient) application).getClient()
						.addClientConnectionListener(clientConnectionListener);
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
	private boolean server;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.newmessage);

		Button addRecipient = (Button) findViewById(R.id.addrecipient);
		addRecipient.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
						Contacts.CONTENT_URI);
				startActivityForResult(contactPickerIntent,
						CONTACT_PICKER_RESULT);
			}
		});

		newMessageText = (LimitedEditText) findViewById(R.id.newmessage_text);
		newMessageText.setMaxTextSize(140);
		newMessageText.setOnEditorActionListener(mWriteListener);
		newMessageText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (s.toString().length() > 0 && address != null) {
					mSendButton.setEnabled(true);
				} else {
					mSendButton.setEnabled(false);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});

		mSendButton = (Button) findViewById(R.id.newmessage_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				TextView view = (TextView) findViewById(R.id.newmessage_text);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});

		Intent intent = getIntent();
		if (intent != null) {
			if (Intent.ACTION_SENDTO.equals(intent.getAction())) {
				String number = intent.getDataString();
				number = URLDecoder.decode(number);
				number = number.replace("-", "").replace("smsto:", "")
						.replace("sms:", "");
				address = number;
			}
		}
		if (address == null) {
		} else {
			addRecipient.setEnabled(false);
		}
		mSendButton.setEnabled(false);

		SharedPreferences defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		String operationMode = defaultSharedPreferences.getString(
				"operationMode", null);

		if (operationMode == null) {
			Intent i = new Intent(this, ChooseModeActivity.class);
			startActivity(i);
		} else if (operationMode.equals("server")) {
			bindService(new Intent(this, SmsApplicationServer.class),
					mConnection, Context.BIND_AUTO_CREATE);
			server = true;
		} else if (operationMode.equals("client")) {
			bindService(new Intent(this, SmsApplicationClient.class),
					mConnection, Context.BIND_AUTO_CREATE);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (application != null) {
			if (server) {
				((SmsApplicationServer) application)
						.removeConnectionListener(connectionListener);
			} else {
				((SmsApplicationClient) application).getClient()
						.removeClientConnectionListener(
								clientConnectionListener);
			}
		}
		unbindService(mConnection);
	}

	protected static final int CONTACT_PICKER_RESULT = 1001;
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event) {
			if (actionId == EditorInfo.IME_NULL
					&& event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			return true;
		}
	};
	private String address;
	private Button mSendButton;
	private LimitedEditText newMessageText;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case CONTACT_PICKER_RESULT:
				address = null;
				mSendButton.setEnabled(false);

				Cursor cursor = null;
				String phoneNumber = "";
				List<String> allNumbers = new ArrayList<String>();
				int phoneIdx = 0;
				try {
					Uri result = data.getData();
					String id = result.getLastPathSegment();
					cursor = getContentResolver().query(Phone.CONTENT_URI,
							null, Phone.CONTACT_ID + "=?", new String[] { id },
							null);
					phoneIdx = cursor.getColumnIndex(Phone.DATA);
					if (cursor.moveToFirst()) {
						while (cursor.isAfterLast() == false) {
							phoneNumber = cursor.getString(phoneIdx);
							allNumbers.add(phoneNumber);
							cursor.moveToNext();
						}
					} else {
						// no results actions
					}
				} catch (Exception e) {
					// error actions
				} finally {
					if (cursor != null) {
						cursor.close();
					}

					final CharSequence[] items = allNumbers
							.toArray(new String[allNumbers.size()]);
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Choose a number");
					builder.setItems(items,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int item) {
									String selectedNumber = items[item]
											.toString();
									selectedNumber = selectedNumber.replace(
											"-", "");

									address = PhoneNumberUtils
											.formatNumber(selectedNumber);
									if (newMessageText.getText().length() > 0) {
										mSendButton.setEnabled(true);
									}
								}
							});
					AlertDialog alert = builder.create();
					if (allNumbers.size() > 1) {
						alert.show();
					} else {
						String selectedNumber = phoneNumber.toString();
						selectedNumber = selectedNumber.replace("-", "");
						address = PhoneNumberUtils.formatNumber(selectedNumber);
						mSendButton.setEnabled(true);
					}

					if (phoneNumber.length() == 0) {
						// no numbers found actions
					}
				}
				break;
			}

		} else {
			Log.w("smsapp", "Warning: activity result not ok");
		}
	}

	private void sendMessage(String message) {
		if (server
				|| ((SmsApplicationClient) application).getClient().connected()) {
			if (message.length() > 0) {

				try {
					application.sendMessage(address, message);
				} catch (Exception e) {
					Log.w("smsapp", "Cannot send message:" + e.getMessage(), e);
					AlertBox("Cannot send message", e.getMessage(), false);
				}

				finish();
			}
		} else {
			AlertBox("Server not connected", "please try again later", false);
		}
	}

	public final void AlertBox(String title, String message,
			final boolean finish) {
		new AlertDialog.Builder(this).setTitle(title)
				.setMessage(message + (finish ? " Press OK to exit" : ""))
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface arg0, int arg1) {
						if (finish) {
							finish();
						}
					}
				}).show();
	}
}
