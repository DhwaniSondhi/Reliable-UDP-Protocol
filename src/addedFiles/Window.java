package addedFiles;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Window {
	private static final Logger logger = LoggerFactory.getLogger(Window.class);

	public static final long WINDOW_SIZE=4L;
	public List<Packet> packets;
	public int lastSeqFilled=-1;
	public long windowStart=0L;
	public long windowEnd=Window.WINDOW_SIZE-1;
	private static final SimpleDateFormat SIMPLE_DF = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

	public Window() {
		super();	
		this.packets=new ArrayList<>();
		for(int loop=0;loop<(int)Packet.MAX_SEQNUMBER;loop++) {
			this.packets.add(null);
		}
	}
	public Window(byte[] data,InetAddress peerAddress,int peerPort) throws UnknownHostException {
		this.packets=new ArrayList<>();

		int maxLength=Packet.PAY_LOADSIZE;
		long seqNum=0L;
int counter=1;
		for(int loop=0;loop<data.length;) {
			if(seqNum>=Packet.MAX_SEQNUMBER) {
				seqNum=0L;
			}
			int start=loop;
			int end=(loop+maxLength)<data.length?(loop+maxLength):data.length;
			
			String dataloop=counter+" || "+(new String(Arrays.copyOfRange(data, start, end), UTF_8));
			byte[] byteloop=dataloop.getBytes();
			counter++;
			
			Packet packet=new Packet.Builder()
					.setType(1)
					.setSequenceNumber(seqNum)
					.setPeerAddress(peerAddress)
					.setPortNumber(peerPort)
					.setPayload(byteloop)
					.create();
			this.packets.add(packet);

			loop=end;
			seqNum++;
		}
		if(seqNum>=Packet.MAX_SEQNUMBER) {
			seqNum=0L;
		}
		//9---->last packet
		this.packets.add(new Packet(9,seqNum,peerAddress,peerPort,"".getBytes()));
	}

	public Queue<Packet> setUpWindow() {
		int counterForVar=0;
		Queue<Packet> queue=new LinkedList<>();
		for(Packet packet:packets) {
			if(counterForVar<(int)WINDOW_SIZE) {
				queue.add(packet);
				counterForVar++;
			}else {
				break;
			}
		}
		lastSeqFilled=counterForVar;
		return queue;
	}

	public void send(Queue<Packet> queue,DatagramChannel channel) throws IOException {
		SocketAddress router = new InetSocketAddress(AddressPorts.routerAddress, AddressPorts.routerPort);
		//System.out.println(queue.size());
		for(Packet packet:queue) {
			System.out.println("Sending "+packet.getSequenceNumber());
			channel.send(packet.toBuffer(), router);
		}
	}

	public List<Packet> getPackets() {
		return packets;
	}
	public void setPackets(List<Packet> packets) {
		this.packets = packets;
	}



}
