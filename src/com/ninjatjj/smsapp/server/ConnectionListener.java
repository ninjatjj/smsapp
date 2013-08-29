package com.ninjatjj.smsapp.server;

public interface ConnectionListener {

	public void connectionStateChange(int numberOfClients);

	public boolean vetoConnection(String clientID);
}
