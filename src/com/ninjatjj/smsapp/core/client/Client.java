package com.ninjatjj.smsapp.core.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ninjatjj.smsapp.core.Constants;
import com.ninjatjj.smsapp.core.MessageListener;
import com.ninjatjj.smsapp.core.Sms;

public abstract class Client {
	protected volatile boolean exit;

	protected String hostname;
	protected String port;

	public String uuid;

	private volatile Set<Sms> messages = new TreeSet<Sms>();

	private volatile boolean connected;

	protected ServerHandler reestablishConnection;

	private volatile List<MessageListener> messageListeners = new ArrayList<MessageListener>();
	private volatile List<ClientConnectionListener> clientConnectionListeners = new ArrayList<ClientConnectionListener>();

	public volatile long lastPing;

	public synchronized void start() {
		if (reestablishConnection == null) {
			reestablishConnection = new ServerHandler();
		}
	}

	public final Set<Sms> getMessages() {
		synchronized (messages) {
			return messages;
		}
	}

	public final void sendMessage(String address, String message)
			throws IOException {
		reestablishConnection.sendMessage(address, message);
	}

	public final void clearMessageReceived() throws IOException,
			InterruptedException {
		reestablishConnection.clearMessageReceived();
	}

	public final void messageRemoved(Sms sms) {
		synchronized (messages) {
			messages.remove(sms);
		}
		for (MessageListener handler : messageListeners) {
			handler.messageRemoved(sms);
		}
	}

	public final void messageReceived(Sms sms) {
		synchronized (messages) {
			messages.add(sms);
		}

		for (MessageListener messageListener : messageListeners) {
			messageListener.messageReceived(sms);
		}
	}

	public final void addMessageListener(MessageListener main) {
		messageListeners.add(main);
	}

	public final void removeMessageListener(MessageListener main) {
		messageListeners.remove(main);
	}

	public final void addClientConnectionListener(ClientConnectionListener main) {
		clientConnectionListeners.add(main);
	}

	public final void removeClientConnectionListener(
			ClientConnectionListener main) {
		clientConnectionListeners.remove(main);
	}

	public final boolean connected() {
		return connected;
	}

	public final void setSocketConnectionDetails(String hostname, String port,
			String uuid) {
		this.hostname = hostname;
		this.port = port;
		this.uuid = uuid;
		reconnect();
	}

	protected void debug(String message) {
		System.out.println(new Date() + " " + Thread.currentThread().getName()
				+ " " + message);
	}

	protected void warn(String message, Throwable e) {
		System.out.println(new Date() + " " + Thread.currentThread().getName()
				+ " " + message);
		e.printStackTrace();
	}

	protected void warn(String message) {
		System.out.println(new Date() + " " + Thread.currentThread().getName()
				+ " " + message);
	}

	public synchronized void disconnect() {
		boolean wasConnected = connected;
		connected = false;
		if (reestablishConnection != null) {
			reestablishConnection.disconnect();
		}
		if (wasConnected) {
			for (ClientConnectionListener clientConnectionListener : clientConnectionListeners) {
				clientConnectionListener.connectionStateChange(false);
			}
		}
	}

	public final void reconnect() {
		disconnect();
		start();
		reconnectImpl();
	}

	protected abstract void reconnectImpl();

	protected abstract void myConnected();

	public boolean shouldConnectWifi() {
		return hostname != null && port != null && uuid != null;
	}

	public void deleteThread(String address) throws IOException {
		reestablishConnection.deleteThread(address);
	}

	public boolean canConnect() {
		return shouldConnectWifi();
	}

	class ServerHandler extends Thread {

		private AtomicBoolean doNotConnect = new AtomicBoolean(false);
		private boolean connecting;
		private DataOutputStream outStream;
		private DataInputStream inStream;
		private Socket socket;
		private Object socketLock = new Object();

		public ServerHandler() {
			super("ServerHandler");
			setDaemon(true);
			start();
		}

		public void deleteThread(String address) throws IOException {
			synchronized (outStream) {
				outStream.writeByte(Constants.DELETE_THREAD);
				outStream.writeUTF(address);
				outStream.flush();
			}
		}

		public void clearMessageReceived() throws IOException {
			synchronized (outStream) {
				outStream.writeByte(Constants.CLEAR_MESSAGE_RECEIVED);
				outStream.flush();
			}
		}

		public void sendMessage(String address, String message)
				throws IOException {
			synchronized (outStream) {
				outStream.writeByte(Constants.SEND_SMS);
				outStream.writeUTF(address);
				outStream.writeUTF(message);
				outStream.flush();
			}
		}

		public void disconnect() {
			synchronized (socketLock) {
				if (socket != null) {
					try {
						debug("Closing socket: " + socket.getLocalPort());
						socket.close();
					} catch (IOException e) {
						warn("could not close socket: " + e.getMessage(), e);
					}
					socket = null;
				}
			}

			synchronized (doNotConnect) {
				if (connecting) {
					doNotConnect.set(true);
					try {
						doNotConnect.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					doNotConnect.set(false);
				}
			}
		}

		public void run() {
			while (!exit) {
				// List<Exception> problems = new ArrayList<Exception>();
				if (!connected) {
					if (shouldConnectWifi()) {
						connecting = true;
						List<String> toCheck2 = new ArrayList<String>();
						StringTokenizer stringTokenizer = new StringTokenizer(
								hostname, ",");
						while (stringTokenizer.hasMoreTokens() && !connected) {
							String toCheck = stringTokenizer.nextToken().trim();
							int indexOf = toCheck.indexOf("*");
							if (indexOf >= 0) {
								for (int i = 0; i < 255; i++) {
									toCheck2.add(toCheck.replaceFirst("\\*",
											String.valueOf((i))));
								}
							} else {
								toCheck2.add(toCheck);
							}
						}

						for (String toCheck : toCheck2) {
							debug("Connecting to: " + toCheck + " on: " + port);
							try {
								synchronized (socketLock) {
									socket = new Socket();
									socket.connect(
											new InetSocketAddress(
													toCheck,
													Integer.parseInt(Client.this.port)),
											10000);
									setName("ServerHandler-"
											+ socket.getLocalPort());
									debug("socket ports opened - client side: "
											+ socket.getLocalPort()
											+ " server side: "
											+ socket.getRemoteSocketAddress());
									myConnected();
									// socket.setKeepAlive(true);
									// socket.setTcpNoDelay(true);
									this.outStream = new DataOutputStream(
											socket.getOutputStream());
									this.inStream = new DataInputStream(
											socket.getInputStream());

									this.outStream.writeUTF("smsapp");
									this.outStream.flush();
									this.outStream.writeUTF(uuid);
									this.outStream.flush();

									String readLine = null;
									if (socket != null) {
										socket.setSoTimeout(5000);
									}
									try {
										readLine = inStream.readUTF();
										if (socket != null) {
											socket.setSoTimeout(0);
										}
									} catch (SocketTimeoutException e) {
										for (ClientConnectionListener clientConnectionListener : clientConnectionListeners) {
											clientConnectionListener
													.longTimeToConnect();
										}

										// if (socket != null) {
										// socket.setSoTimeout(0);
										// }
										// readLine = inStream.readUTF();
									}
									if (readLine == null
											|| !readLine.equals("smsappserver")) {

										synchronized (socketLock) {
											if (socket != null) {
												try {
													debug("Closing socket2: "
															+ socket.getLocalPort());
													socket.close();
												} catch (IOException e) {
													warn("could not close socket: "
															+ e.getMessage(), e);
												}
												socket = null;
											}
										}
										throw new Exception(
												"did not complete handshake");
									} else {
										connected = true;

										try {
											int numberOfMessages = inStream
													.readInt();

											synchronized (messages) {

												TreeSet<Sms> newMessages = new TreeSet<Sms>();
												for (int i = 0; i < numberOfMessages; i++) {
													Sms sms = new Sms();
													sms.setId(inStream
															.readUTF());
													sms.setAddress(inStream
															.readUTF());
													sms.setReceived(new Date(
															inStream.readLong()));
													sms.setBody(inStream
															.readUTF());
													sms.setPrefix(inStream
															.readUTF());
													// sms.setRead(inStream.readBoolean());
													// // TODO readd
													newMessages.add(sms);
												}
												messages.clear();
												messages.addAll(newMessages);
											}

											for (ClientConnectionListener clientConnectionListener : clientConnectionListeners) {
												clientConnectionListener
														.connectionStateChange(true);
											}

											debug("connected to smsapp");
										} catch (Exception e) {
											warn("Problems retrieving initial messages",
													e);

											synchronized (socketLock) {
												if (socket != null) {
													try {
														debug("Closing socket3: "
																+ socket.getLocalPort());
														socket.close();
													} catch (IOException e2) {
														warn("could not close socket: "
																+ e2.getMessage(),
																e2);
													}
													socket = null;
												}
											}
											throw e;
										}
									}
								}
								break;
							} catch (Exception e) {

								synchronized (socketLock) {
									if (socket != null) {
										try {
											debug("Closing socket4: "
													+ socket.getLocalPort());
											socket.close();
										} catch (IOException e2) {
											warn("could not close socket: "
													+ e2.getMessage(), e2);
										}
										socket = null;
									}
								}
								if (e instanceof EOFException) {
									for (ClientConnectionListener clientConnectionListener : clientConnectionListeners) {
										clientConnectionListener
												.cannotConnect("Client has not yet been accepted by server");
									}
								} else {
									warn("connection problem, scheduling reconnect: "
											+ e.getClass()
											+ " "
											+ e.getMessage(), e);
								}
								synchronized (doNotConnect) {
									if (doNotConnect.get()) {
										break;
									}
								}
							}
						}
						connecting = false;
					}
					lastPing = System.currentTimeMillis();
				}
				synchronized (doNotConnect) {
					doNotConnect.notify();
				}
				if (!connected) {
					for (ClientConnectionListener clientConnectionListener : clientConnectionListeners) {
						clientConnectionListener
								.cannotConnect("Could not connect to any known server");
					}
					setName("ServerHandler-waitingToReconnect60");
					waitToReconnect(60000);
				} else {
					Thread pingThread = new Thread("PingChecker") {
						@Override
						public void run() {
							while (connected) {
								if (System.currentTimeMillis() - lastPing > Constants.PING_RATE * 2) {
									warn("did not receive a ping within: "
											+ Constants.PING_RATE * 2
											+ " millis");
									Client.this.disconnect();
								} else {
									waitToPing(((Constants.PING_RATE * 2) - (System
											.currentTimeMillis() - lastPing)) + 1);
								}
							}
						}
					};
					pingThread.start();

					try {
						while (true) {
							int command = inStream.readByte();
							switch (command) {
							case Constants.MSG_RECEIVED: {
								Sms sms = new Sms();
								sms.setId(inStream.readUTF());
								sms.setAddress(inStream.readUTF());
								sms.setReceived(new Date(inStream.readLong()));
								sms.setBody(inStream.readUTF());
								sms.setPrefix(inStream.readUTF());
								messageReceived(sms);
								break;
							}
							case Constants.MSG_REMOVED: {
								Sms sms = new Sms();
								sms.setId(inStream.readUTF());
								sms.setAddress(inStream.readUTF());
								sms.setReceived(new Date(inStream.readLong()));
								sms.setBody(inStream.readUTF());
								sms.setPrefix(inStream.readUTF());
								messageRemoved(sms);
								break;
							}
							case Constants.PING: {
								debug("Received ping");
								lastPing = System.currentTimeMillis();
								synchronized (outStream) {
									// debug("sending ping back");
									outStream.write(Constants.PING);
									outStream.flush();
								}
								break;
							}
							case Constants.CLEAR_MESSAGE_RECEIVED: {
								for (MessageListener handler : messageListeners) {
									handler.clearMessageReceived();
								}
								break;
							}
							case Constants.SIGNAL_STRENGTH_CHANGED: {
								signalStrengthChanged(inStream.readBoolean());
								break;
							}
							default: {
								warn("Unknown command: " + command);
								disconnect();
								break;
							}
							}
						}
					} catch (IOException e) {
						warn("Server doNotConnect: " + e.getMessage(), e);
						Client.this.disconnect();

						try {
							wakeUpPingThread(pingThread);
							pingThread.join();
						} catch (InterruptedException e1) {
							warn("Could not join ping checker straight away",
									e1);
						}

						setName("ServerHandler-waitingToReconnect10");
						waitToReconnect(10000);
					}
				}
			}
		}
	}

	public abstract void waitToReconnect(long waitTime);

	public abstract void signalStrengthChanged(boolean readBoolean);

	public abstract void waitToPing(long timeSinceLastPing);

	public abstract void wakeUpPingThread(Thread pingThread);
}
