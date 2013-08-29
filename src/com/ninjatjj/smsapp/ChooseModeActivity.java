package com.ninjatjj.smsapp;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ChooseModeActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.choosemode);

		Button server = (Button) findViewById(R.id.server);
		server.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				changeMode("server");
			}

		});

		Button client = (Button) findViewById(R.id.client);
		client.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				changeMode("client");
			}
		});
	}

	private void changeMode(String string) {
		Context context = ChooseModeActivity.this.getApplicationContext();

		SharedPreferences defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(context);

		Editor edit = defaultSharedPreferences.edit();
		edit.putString("operationMode", string);
		edit.commit();

		Intent mStartActivity = new Intent(context, StartActivity.class);
		int mPendingIntentId = 123456;
		PendingIntent mPendingIntent = PendingIntent.getActivity(context,
				mPendingIntentId, mStartActivity,
				PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100,
				mPendingIntent);
		finish();

	}
}
