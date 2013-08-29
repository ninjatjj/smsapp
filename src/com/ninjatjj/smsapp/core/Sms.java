package com.ninjatjj.smsapp.core;

import java.util.Date;

public class Sms implements Comparable<Sms> {

	private String id;
	private String address;
	private Date received;
	private String body;
	private String prefix = "";

	private boolean read;

	public Sms() {

	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
		// Log.d("smsapp", "address is: " + address);
	}

	public Date getReceived() {
		return received;
	}

	public void setReceived(Date received) {
		this.received = received;
		// Log.d("smsapp", "received is: " + received);
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
		// Log.d("smsapp", "body is: " + body);
	}

	@Override
	public int compareTo(Sms sms) {
		return sms.getReceived().compareTo(received);
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
		// Log.d("smsapp", "prefix is: " + prefix);
	}

	public String getPrefix() {
		return prefix;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Sms)) {
			return false;
		}
		Sms sms = (Sms) o;
		return address.equals(sms.getAddress())
				&& received.equals(sms.getReceived())
				&& body.equals(sms.getBody()) && prefix.equals(sms.getPrefix());
	}

	@Override
	public String toString() {
		return getBody() + "\n" + getReceived();
	}
}
