package com.ninjatjj.smsapp.ui;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.SmsApplication;
import com.ninjatjj.smsapp.core.MessageListener;
import com.ninjatjj.smsapp.core.Sms;
import com.ninjatjj.smsapp.core.ui.SmsWrapper;

public abstract class AbstractConversationsActivity extends Activity implements
		MessageListener {
	private ArrayAdapter<SmsWrapper> mConversationArrayAdapter;
	private ListView mConversationView;

	private boolean alertVisible = false;

	protected abstract String getNewMessageActivityName();

	protected abstract String getConversationActivityName();

	protected SmsApplication application;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set up the window layout
		setContentView(R.layout.conversations);

		Button newMessageButton = (Button) findViewById(R.id.newmessage_create);
		newMessageButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				Intent intent = new Intent(getNewMessageActivityName());
				startActivity(intent);
			}
		});
		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<SmsWrapper>(this,
				R.layout.message) {

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView view = (TextView) super.getView(position, convertView,
						parent);
				if (hasUnread(view.getText().toString())) {
					view.setTypeface(null, Typeface.BOLD);
				}
				return view;
			}

		};
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);
		registerForContextMenu(mConversationView);
		mConversationView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				SmsWrapper person = (SmsWrapper) mConversationView
						.getItemAtPosition(arg2);
				Intent intent = new Intent(getConversationActivityName());
				intent.putExtra("address",
						ContactManager.getAddress(person.toString()));
				startActivity(intent);
			}

		});

		if (PreferenceManager.getDefaultSharedPreferences(
				AbstractConversationsActivity.this).getBoolean("useBluetooth",
				false)) {
			if (BluetoothAdapter.getDefaultAdapter() == null) {
				AlertBox("Warning", "Bluetooth Not supported", false);
			}
			if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
				Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivity(enableBtIntent);
			}
		}

		if (PreferenceManager.getDefaultSharedPreferences(
				AbstractConversationsActivity.this).getBoolean("useWifi", true)) {
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			if (!wifi.isWifiEnabled()) {
				new AlertDialog.Builder(this)
						.setTitle("Enable Wifi?")
						.setMessage("You must enable Wifi for smsapp to work")
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface arg0,
											int arg1) {
										WifiManager wifi;
										wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

										wifi.setWifiEnabled(true);
									}
								})
						.setNegativeButton("Not now",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface arg0,
											int arg1) {
									}
								}).show();
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		SharedPreferences defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (defaultSharedPreferences.getString("operationMode", null) == null) {
			finish();
		}
	}

	protected boolean hasUnread(String item) {
		String address = ContactManager.getAddress(item);
		return application.hasUnread(address);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.menu_list, menu);

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		if (item.getItemId() == R.id.delete) {

			SmsWrapper name = mConversationArrayAdapter.getItem(info.position);
			deleteThread(name.toString());
			return true;
		} else {
			return super.onContextItemSelected(item);
		}
	}

	public final void deleteThread(String name) {
		try {
			application.deleteThread(ContactManager.getAddress(name));
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	public void messageRemoved(final Sms sms) {
		// We could clean the conversation panel if there are no messages
		final String address = sms.getAddress();

		Set<Sms> messages = application.getMessages();
		Iterator<Sms> iterator = messages.iterator();
		boolean found = false;
		while (iterator.hasNext()) {
			Sms next = iterator.next();
			if (next.getAddress().equals(address)) {
				found = true;
				break;
			}
		}

		if (!found) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					synchronized (mConversationArrayAdapter) {
						SmsWrapper smsWrapper = new SmsWrapper(sms,
								ContactManager.getDisplayName(
										getContentResolver(), sms.getAddress()));
						int position = mConversationArrayAdapter
								.getPosition(smsWrapper);
						if (position >= 0) {
							mConversationArrayAdapter.remove(smsWrapper);
						}
					}
				}
			});
		}
	}

	// private List<String> toInsert = new ArrayList<String>();

	@Override
	public void messageReceived(final Sms sms) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mConversationArrayAdapter) {
					SmsWrapper smsWrapper = new SmsWrapper(sms,
							ContactManager.getDisplayName(getContentResolver(),
									sms.getAddress()));
					int position = mConversationArrayAdapter
							.getPosition(smsWrapper);
					if (position >= 0) {
						SmsWrapper existingWrapper = mConversationArrayAdapter
								.getItem(position);
						mConversationArrayAdapter.remove(smsWrapper);
						if (existingWrapper.getSms().compareTo(sms) < 0) {
							smsWrapper = existingWrapper;
						}
					}

					int count = mConversationArrayAdapter.getCount();
					for (int i = 0; i < count; i++) {
						Sms sms2 = mConversationArrayAdapter.getItem(i)
								.getSms();
						if (smsWrapper.getSms().compareTo(sms2) < 0) {
							mConversationArrayAdapter.insert(smsWrapper, i);
							break;
						}
					}
					if (mConversationArrayAdapter.getPosition(smsWrapper) < 0) {
						mConversationArrayAdapter.add(smsWrapper);
					}
				}
			}
		});
	}

	@Override
	public final void clearMessageReceived() {
	}
}