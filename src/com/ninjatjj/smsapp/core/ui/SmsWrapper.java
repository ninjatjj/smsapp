package com.ninjatjj.smsapp.core.ui;

import com.ninjatjj.smsapp.core.Sms;

public class SmsWrapper {
	private Sms sms;
	private String displayName;

	public SmsWrapper(Sms sms, String displayName) {
		this.sms = sms;
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}

	public Sms getSms() {
		return sms;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof SmsWrapper) {
			return displayName.equals(((SmsWrapper) o).toString());
		}
		return false;
	}
}
