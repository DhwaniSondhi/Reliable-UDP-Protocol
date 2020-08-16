package server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import addedFiles.Packet;
import addedFiles.UDPReceiver;
import addedFiles.UDPSender;

public class HttpServer {
	private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
	static boolean printDebugMessage=false;
	static int port=8007;
	static String filePath="/";
	static boolean upAbleToUnderstand=false;
	/*
	 * Initiate the server
	 */
	public static void startWorking(Packet packet, DatagramChannel connectChannel,SocketAddress router) throws IOException {
		Packet outPacket=packet.toBuilder()
				.setType(5)
				.create();
		InetAddress peerAddress=packet.getPeerAddress();
		int peerPort=packet.getPeerPort();
		connectChannel.send(outPacket.toBuffer(), router);
		//connectChannel.configureBlocking(false);

		System.out.println("Connection made");
		UDPReceiver uDPReceiver=new UDPReceiver(connectChannel,peerAddress,peerPort);
		uDPReceiver.setUp();
		String input="";
		try {
			uDPReceiver.receive();
			input=uDPReceiver.getOutput();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(input!=null  &&  input.trim().length()>0) {
			String inputList[] = input.split("\\r?\\n");
			String ans=new ServerImpl(inputList, uDPReceiver.getPeerAddress(), uDPReceiver.getPeerPort()).getAns();
			UDPSender udpSender=new UDPSender(true);
			udpSender.execute(ans,peerAddress,peerPort,false);
		}


	}

	public static void initiateServer() throws IOException{
		DatagramChannel connectChannel=DatagramChannel.open();
		connectChannel.bind(new InetSocketAddress(port));
		while(true) {

			ByteBuffer buffer = ByteBuffer
					.allocate(Packet.MAX_LEN)
					.order(ByteOrder.BIG_ENDIAN);
			buffer.clear();
			SocketAddress router = connectChannel.receive(buffer);
			buffer.flip();
			try {
				Packet packet = Packet.fromBuffer(buffer);
				buffer.flip();

				//4---3-way connection syn
				//5---3-way connection syn-ack
				//6---3-way connection ack
				if(packet.getType()==4) {
					//new Thread(()->{
						try {
							startWorking(packet,connectChannel,router);
						} catch (IOException e) {
							e.printStackTrace();
						}
					//}).start();
				}
			}catch(Exception e) {}

		}



	}
	/*
	 * The start up method
	 */
	public static void main(String[] args){
		Scanner scan=new Scanner(System.in);
		System.out.println("Please enter the command");
		String[] inputArgs=scan.nextLine().trim().split(" ");
		if(inputArgs[0].equalsIgnoreCase("httpfs")) {
			for(int i=1;i<inputArgs.length;i++) {
				if(inputArgs[i].equalsIgnoreCase("-v")) {
					printDebugMessage=true;
				}else if(inputArgs[i].equalsIgnoreCase("-p")) {
					port = (int)Integer.parseInt(inputArgs[++i]);
				}else if(inputArgs[i].equalsIgnoreCase("-d")) {
					filePath=inputArgs[++i].trim();
				}
			}
			try {
				initiateServer();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		scan.close();
	}
}

