package com.ninjatjj.smsapp.core;

public interface Constants {
	public static final long PING_RATE = 300000;

	public static final byte SEND_SMS = 0;
	public static final byte MSG_RECEIVED = 1;
	public static final byte MSG_REMOVED = 2;
	public static final byte PING = 3;
	public static final byte CLEAR_MESSAGE_RECEIVED = 4;
	public static final byte DELETE_THREAD = 5;
	public static final byte SIGNAL_STRENGTH_CHANGED = 6;

	public static final int SDK_VERSION = 11;

	public static final boolean STATUS_BLACK_ICON = true;
}
