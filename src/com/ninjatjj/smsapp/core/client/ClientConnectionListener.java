package com.ninjatjj.smsapp.core.client;

public interface ClientConnectionListener {

	public void connectionStateChange(boolean connected);

	public void cannotConnect(String reason);

	public void longTimeToConnect();

}
