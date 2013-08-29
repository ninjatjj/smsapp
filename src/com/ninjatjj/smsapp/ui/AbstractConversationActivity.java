package com.ninjatjj.smsapp.ui;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.ninjatjj.smsapp.R;
import com.ninjatjj.smsapp.core.MessageListener;
import com.ninjatjj.smsapp.core.Sms;

public abstract class AbstractConversationActivity extends Activity implements
		MessageListener {
	private ArrayAdapter<Sms> mConversationArrayAdapter;
	private ListView mConversationView;

	private final TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event) {
			// If the action is a key-up event on the return key, send the
			// message
			if (actionId == EditorInfo.IME_NULL
					&& event.getAction() == KeyEvent.ACTION_UP) {
				sendMessage();
			}
			return true;
		}
	};

	private LimitedEditText mOutEditText;
	private Button mSendButton;
	protected String address;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.conversation);

		Bundle extras = getIntent().getExtras();
		address = extras.getString("address");

		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<Sms>(this,
				R.layout.message) {

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView view = (TextView) super.getView(position, convertView,
						parent);
				view.setBackgroundResource(getItem(position).getPrefix()
						.startsWith("<me>") ? R.drawable.bubble_left
						: R.drawable.bubble_right);
				if (!getItem(position).isRead()) {
					view.setTypeface(null, Typeface.BOLD);
				}
				return view;
			}

		};
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		// Initialize the compose field with a listener for the return key
		mOutEditText = (LimitedEditText) findViewById(R.id.edit_text_out);
		mOutEditText.setMaxTextSize(140);
		mOutEditText.setOnEditorActionListener(mWriteListener);
		mOutEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				mSendButton.setEnabled(s.toString().length() > 0);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});

		// Initialize the send button with a listener that for click events
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Send a message using content of the edit text widget
				sendMessage();
			}
		});
		mSendButton.setEnabled(false);
	}

	// @Override
	// public void onCreateContextMenu(ContextMenu menu, View v,
	// ContextMenuInfo menuInfo) {
	// super.onCreateContextMenu(menu, v, menuInfo);
	//
	// MenuInflater inflater = getMenuInflater();
	// inflater.inflate(R.layout.menu_list, menu);
	//
	// }
	//
	// @Override
	// public boolean onContextItemSelected(MenuItem item) {
	// AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
	// .getMenuInfo();
	// if (item.getItemId() == R.id.delete) {
	//
	// String content = mConversationArrayAdapter.getItem(info.position);
	// Sms sms = new Sms();
	// sms.setAddress(address);
	// if (content.startsWith("<me>")) {
	// sms.setPrefix("<me>");
	// content = content.substring(content.indexOf(">" + 1));
	// }
	// sms.setBody(content.substring(0, content.indexOf("\n") - 1));
	// sms.setReceived(new Date(
	// content.substring(content.indexOf("\n") + 1)));
	// deleteMessage(sms);
	// return true;
	// } else {
	// return super.onContextItemSelected(item);
	// }
	// }
	//
	// public abstract void deleteMessage(Sms string);

	@Override
	public final void messageRemoved(final Sms sms) {
		if (ContactManager.getDisplayName(getContentResolver(),
				sms.getAddress()).equals(
				ContactManager.getDisplayName(getContentResolver(), address))) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mConversationArrayAdapter.remove(sms);
				}
			});
		}
	}

	@Override
	public final void messageReceived(final Sms sms) {
		if (ContactManager.getDisplayName(getContentResolver(),
				sms.getAddress()).equals(
				ContactManager.getDisplayName(getContentResolver(), address))) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mConversationArrayAdapter.add(sms);

					TextView view = (TextView) findViewById(R.id.edit_text_out);
					String message = view.getText().toString();
					mSendButton.setEnabled(message != null
							&& message.length() > 0);
				}
			});
		}
	}

	@Override
	public final void clearMessageReceived() {
	}

	private final void sendMessage() {
		if (connected()) {
			TextView view = (TextView) findViewById(R.id.edit_text_out);
			String message = view.getText().toString();
			if (message.length() > 0) {
				try {
					mSendButton.setEnabled(false);
					sendMessage(address, message);
					view.setText("");
				} catch (Exception e) {
					Log.w("smsapp", "Cannot send message:" + e.getMessage(), e);
					AlertBox("Cannot send message", e.getMessage(), false);
				}
			}
		} else {
			AlertBox("Server not connected", "please try again later", false);
		}
	}

	protected abstract void sendMessage(String address2, String message)
			throws InterruptedException, IOException;

	protected abstract boolean connected();

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
