package com.ninjatjj.smsapp.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;

public class BooleanWrapper {

	private boolean booleanValue;
	private boolean set;
	private String clientID;

	public BooleanWrapper(String clientID) {
		this.clientID = clientID;
	}

	public synchronized void setTrue() {
		set = true;
		booleanValue = true;
		notify();
	}

	public synchronized void setFalse() {
		set = true;
		booleanValue = false;
		notify();
	}

	public synchronized boolean getValue() {
		while (!set) {
			try {
				wait();
			} catch (InterruptedException e) {
				Log.e("smsapp", "Could not wait for value", e);
			}
		}
		return booleanValue;
	}

	public String getClientID() {
		return clientID;
	}

	public void showDialog(Activity conversationsActivity) {
		new AlertDialog.Builder(conversationsActivity)
				.setTitle("Connection OK?")
				.setMessage("Is connection from: " + getClientID() + " ok?")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface arg0, int arg1) {
						setTrue();
					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface arg0, int arg1) {
						setFalse();
					}
				}).show();

	}
}
