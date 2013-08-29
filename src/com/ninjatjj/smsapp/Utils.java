package com.ninjatjj.smsapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

public class Utils {

	private static boolean logcatting;

	public static synchronized void logcat(final String name) {
		if (logcatting) {
			return;
		}
		logcatting = true;

		new Thread(new Runnable() {
			@SuppressLint("SimpleDateFormat")
			public void run() {
				File root = Environment.getExternalStorageDirectory();
				if (root.canWrite()) {
					File smsappLogFolder = new File(root, "smsapp");
					if (!smsappLogFolder.exists()) {
						smsappLogFolder.mkdir();
					}
					DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
					Date today = Calendar.getInstance().getTime();
					String reportDate = df.format(today);
					File gpslogfile = new File(smsappLogFolder, name + "-"
							+ reportDate + ".txt");
					try {
						Process process = Runtime.getRuntime().exec(
								"logcat -v time -s smsapp");
						BufferedReader bufferedReader = new BufferedReader(
								new InputStreamReader(process.getInputStream()));

						String line;
						while ((line = bufferedReader.readLine()) != null) {
							FileWriter gpswriter = new FileWriter(gpslogfile,
									true);
							PrintWriter out = new PrintWriter(gpswriter);
							out.append(line + "\n");
							out.flush();
							out.close();
						}
					} catch (IOException e) {
						Log.e("smsapp", "Problem writing log file from logcat",
								e);
					}
				}
			}
		}, "logwriter").start();
	}
}
