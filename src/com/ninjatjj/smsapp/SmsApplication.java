package com.ninjatjj.smsapp;

import java.io.IOException;
import java.util.Set;

import com.ninjatjj.smsapp.core.Sms;

public interface SmsApplication {

	public Set<Sms> getMessages();

	public void deleteThread(String address) throws IOException;

	public boolean hasUnread(String address);

	public void sendMessage(String address, String message) throws IOException,
			InterruptedException;
}
