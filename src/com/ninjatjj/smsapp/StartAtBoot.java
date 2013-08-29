package com.ninjatjj.smsapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ninjatjj.smsapp.client.SmsApplicationClient;
import com.ninjatjj.smsapp.server.SmsApplicationServer;

public class StartAtBoot extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {

			SharedPreferences defaultSharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(context);

			String operationMode = defaultSharedPreferences.getString(
					"operationMode", null);

			if (operationMode == null) {
				return;
			} else {
				boolean startAtBoot = defaultSharedPreferences.getBoolean(
						"startAtBoot", false);
				if (startAtBoot) {
					if (operationMode.equals("server")) {
						context.startService(new Intent(
								SmsApplicationServer.class.getName()));
					} else {
						context.startService(new Intent(
								SmsApplicationClient.class.getName()));
					}
				}
			}
		}
	}
}
