package com.ninjatjj.smsapp.core;

public interface MessageListener {

	public void messageReceived(Sms sms);

	public void messageRemoved(Sms sms);

	public void clearMessageReceived();

	// public void notifyNewMessages();
}
