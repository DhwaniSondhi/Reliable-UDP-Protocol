package addedFiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.nio.channels.SelectionKey.OP_READ;

public class UDPSender {

	private boolean lastValue;
	private static final Logger logger = LoggerFactory.getLogger(UDPSender.class);
	private Window window;
	private Timer timer;
	private boolean transferHasBeenComplete;
	private Queue<Packet> sendWindow;
	private boolean onWait = false;
	private int timeStamp=0;

	public UDPSender(boolean lastValue) {
		super();
		this.lastValue = lastValue;
	}

	private void receive(Selector selector, DatagramChannel channel) throws IOException, InterruptedException {
		int counterLast = 0;
		while (!transferHasBeenComplete) {
			selector.select(AddressPorts.timeout);
			synchronized (this) {
				while (onWait) {
					wait();
				}
				Set<SelectionKey> keys = selector.selectedKeys();
				if (keys.isEmpty()) {
					System.out.println("No response");

					///
					/*
					 * if (lastValue && sendWindow.size()==1 ) { System.out.println("In");
					 * counterLast++; }
					 */
					if (lastValue && counterLast >= AddressPorts.lastLeft) {
						transferHasBeenComplete = true;
					}
					System.out.println("counterLast "+counterLast);
					counterLast++;
					///
					onWait = true;
					notifyAll();
					continue;
				}

				ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
				SocketAddress router = channel.receive(buf);
				buf.flip();
				Packet packet = Packet.fromBuffer(buf);
				String payload = new String(packet.getPayload(), StandardCharsets.UTF_8);
				// System.out.println("Packet No:"+packet.getSequenceNumber()+" Packet content:
				// "+payload);
				logger.info("Payload: {}", payload);
				keys.clear();
				if (packet.getType() == 1) {
					transferHasBeenComplete = true;
					break;
				} else if (packet.getType() == 2) {

					String loopPayload = new String(packet.getPayload(), UTF_8);
					int index=(loopPayload.indexOf("||"));
					String loopPayLoadArr=loopPayload.substring(0,index);			
					int loopCounter=(int)Integer.parseInt(loopPayLoadArr.trim());

					if(loopCounter>=timeStamp) {
						timeStamp=loopCounter;
						if (payload.trim().substring(index).trim().equalsIgnoreCase("1")) {
							boolean contains = false;

							for (Packet loopPacket : sendWindow) {
								if (packet.getSequenceNumber() == loopPacket.getSequenceNumber()) {
									contains = true;
									break;
								}
							}
							if (contains) {
								while (!sendWindow.isEmpty()) {
									Packet loopPacket = sendWindow.remove();
									if (window.lastSeqFilled < window.getPackets().size()) {
										sendWindow.add(window.getPackets().get(window.lastSeqFilled));
										window.lastSeqFilled++;
									}
									if (loopPacket.getSequenceNumber() == packet.getSequenceNumber()) {
										break;
									}
								}
								if (sendWindow.isEmpty()) {
									transferHasBeenComplete = true;
								}
							}
							onWait = true;
							notifyAll();
							continue;
						} else if (payload.trim().substring(index).trim().equalsIgnoreCase("-1")) {
							onWait = true;
							notifyAll();
						}
					}

				}

			}
		}

	}

	private void send(DatagramChannel channel) throws IOException, InterruptedException {
		onWait = true;
		while (!transferHasBeenComplete) {
			synchronized (this) {
				while (!onWait) {
					wait();
				}
				System.out.println("send acquired");
				onWait = false;
				this.window.send(sendWindow, channel);
				notifyAll();
			}

		}
	}

	public boolean connect(DatagramChannel channel, Selector selector, InetAddress host, int portNumber)
			throws IOException {
		// 4---3-way connection syn
		// 5---3-way connection syn-ack
		// 6---3-way connection ack
		SocketAddress router = new InetSocketAddress(AddressPorts.routerAddress, AddressPorts.routerPort);
		Packet packet = new Packet.Builder().setType(4).setSequenceNumber(0L).setPeerAddress(host)
				.setPortNumber(portNumber).setPayload("".getBytes()).create();

		channel.send(packet.toBuffer(), router);
		channel.configureBlocking(false);

		selector.select(AddressPorts.timeout);

		Set<SelectionKey> keys = selector.selectedKeys();
		if (keys.isEmpty()) {
			logger.error("No response after timeout");
			return false;
		}

		keys.clear();
		ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
		channel.receive(buf);
		buf.flip();
		Packet resp = Packet.fromBuffer(buf);
		if (resp.getType() == 5) {
			Packet out = resp.toBuilder().setType(6).create();
			channel.send(out.toBuffer(), router);
		}
		return true;

	}

	public DatagramChannel execute(String data, InetAddress host, int portNumber, boolean connectOrNot)
			throws UnknownHostException, IOException {
		this.transferHasBeenComplete = false;
		window = new Window(data.getBytes(), host, portNumber);
		sendWindow = window.setUpWindow();
		System.out.println("Number of packets: " + window.getPackets().size());
		Selector selector = Selector.open();
		DatagramChannel channel = DatagramChannel.open();
		channel.configureBlocking(false);
		channel.register(selector, OP_READ);
		if (connectOrNot) {
			boolean connect = false;
			int connectTry = 0;
			while (!connect && connectTry < 15) {
				connect = connect(channel, selector, host, portNumber);
				connectTry++;
			}
			if (connectTry == 5) {
				System.out.println("Connection not made");
				return null;
			}
			System.out.println("Connection made");
		}

		Thread t1 = new Thread(() -> {
			try {
				send(channel);
			} catch (Exception e) {
				try {
					channel.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				e.printStackTrace();
			}
		});
		t1.start();
		Thread t2 = new Thread(() -> {
			try {
				receive(selector, channel);
			} catch (Exception e) {
				try {
					channel.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				e.printStackTrace();
			}
		});
		t2.start();
		try {
			t2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return channel;
	}

	public Window getWindow() {
		return window;
	}

	public void setWindow(Window window) {
		this.window = window;
	}

	public Timer getTimer() {
		return timer;
	}

	public void setTimer(Timer timer) {
		this.timer = timer;
	}

}
