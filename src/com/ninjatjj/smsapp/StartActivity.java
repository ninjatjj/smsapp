package com.ninjatjj.smsapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.ninjatjj.smsapp.client.SplashScreen;
import com.ninjatjj.smsapp.server.ConversationsActivity;

public class StartActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences defaultSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		String operationMode = defaultSharedPreferences.getString(
				"operationMode", null);

		if (operationMode == null) {
			Intent i = new Intent(this, ChooseModeActivity.class);
			startActivity(i);
		} else if (operationMode.equals("server")) {
			Intent i = new Intent(this, ConversationsActivity.class);
			startActivity(i);
		} else if (operationMode.equals("client")) {
			Intent i = new Intent(this, SplashScreen.class);
			startActivity(i);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		finish();
	}
}
