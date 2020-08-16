package addedFiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UDPReceiver {

	private static final Logger logger = LoggerFactory.getLogger(UDPReceiver.class);
	private Window window;
	private int rowsFilled=0;
	private Queue<Integer[]> windowAttr;
	private Queue<Packet> outputPack;
	private String output;
	private InetAddress peerAddress;
	private int peerPort;
	private DatagramChannel channel;
	private int timeStamp=0;
	private int ackTimeStamp=1;

	public UDPReceiver(DatagramChannel channel,InetAddress peerAddress,int peerPort){
		this.channel=channel;
		this.peerAddress=peerAddress;
		this.peerPort=peerPort;
	}

	public int changeWindow() {
		int seqAck=window.lastSeqFilled;
		while(!windowAttr.isEmpty()) {
			Integer[] array=windowAttr.peek();
			if(window.getPackets().get(array[0]+array[1])!=null) {
				array=windowAttr.remove();
				seqAck=array[1];
				outputPack.add(window.getPackets().get(array[0]+array[1]));

				String loopPayload=new String(window.getPackets().get(array[0]+array[1]).getPayload(),UTF_8);
				int index=(loopPayload.indexOf("||"));
				String loopPayLoadArr=loopPayload.substring(0,index);
				timeStamp=(int)Integer.parseInt(loopPayLoadArr.trim());
				try {
					System.out.println(timeStamp);
				}catch(Exception e) {
					System.out.println("Exceptions");
				}
				window.windowStart++;
				window.windowEnd++;
				if(rowsFilled>=1  &&  window.windowStart+rowsFilled==window.getPackets().size()) {
					window.windowStart=0L;
				}
				if(window.windowEnd+rowsFilled==window.getPackets().size()) {
					Integer[] loopArr=new Integer[2];
					rowsFilled+=((int)Packet.MAX_SEQNUMBER);
					window.windowEnd=0L;
					loopArr[0]=rowsFilled;
					loopArr[1]=(int)window.windowEnd;
					windowAttr.add(loopArr);
					for(int loop=0;loop<(int)Packet.MAX_SEQNUMBER;loop++) {
						window.getPackets().add(null);
					}
				}else {

					Integer[] loopArr=new Integer[2];
					loopArr[0]=rowsFilled;
					loopArr[1]=(int)window.windowEnd;
					windowAttr.add(loopArr);
				}
			}else {
				break;
			}
		}
		return seqAck;
	}


	public void receive() throws IOException {

		while(true) {
			ByteBuffer buffer = ByteBuffer
					.allocate(Packet.MAX_LEN)
					.order(ByteOrder.BIG_ENDIAN);
			buffer.clear();
			SocketAddress router = channel.receive(buffer);
			buffer.flip();
			if (buffer.limit() >= Packet.MIN_LEN  &&  buffer.limit() <= Packet.MAX_LEN) {
				Packet packet = Packet.fromBuffer(buffer);
				buffer.flip();

				if(packet.getPeerAddress().equals(peerAddress)  && (packet.getType()==9  ||  packet.getType()==1)){

					if(packet.getType()==9) {
						boolean check=true;
						Stack<Packet> st=new Stack<>();
						while(!outputPack.isEmpty()) {
							st.add(outputPack.remove());
						}
						long seqNo=packet.getSequenceNumber();
						if(seqNo==0) {
							seqNo=Packet.MAX_SEQNUMBER;
						}
						if(st.isEmpty()  ||  seqNo-1!=st.peek().getSequenceNumber()) {
							check=false;
						}
						while(!st.isEmpty()) {
							outputPack.add(st.pop());
						}
						if(check) {
							Packet outPacket = packet.toBuilder()
									.setType(2)
									.setPayload((ackTimeStamp+" || "+"1").getBytes())
									.create();
							channel.send(outPacket.toBuffer(), router);
							ackTimeStamp++;
							String serInput="";
							for(Packet loopPack:outputPack) {
								if(loopPack!=null) {
									String loopPayload = new String(loopPack.getPayload(), UTF_8);
									int index=(loopPayload.indexOf("||"))+3;
									String loopPayLoadArr=loopPayload.substring(index);

									serInput+=loopPayLoadArr;
								}
							}
							output=serInput;
							break;
						}

					}
					if(packet.getType()==1){
						for(Integer[] loopArr:windowAttr) {
							//System.out.println("Packet:"+packet.getSequenceNumber()+" "+loopArr[0]+" "+loopArr[1]);
							if(packet.getSequenceNumber()==loopArr[1]  &&  window.getPackets().get(loopArr[0]+loopArr[1])==null){
								String loopPayload = new String(packet.getPayload(), UTF_8);
								int index=(loopPayload.indexOf("||"));
								String loopPayLoadArr=loopPayload.substring(0,index);			
								int loopCounter=(int)Integer.parseInt(loopPayLoadArr.trim());

								if(loopCounter>timeStamp) {
									window.getPackets().set(loopArr[0]+loopArr[1],packet);
								}
								break;
							}
						}
						int ackSeq=changeWindow();
						Packet outPacket = null;
						//System.out.println("Packet:"+packet.getSequenceNumber()+" askSeq:"+ackSeq+" window.lastSeqFilled:"+window.lastSeqFilled);
						if(ackSeq==window.lastSeqFilled) {
							System.out.println("Packet No: "+packet.getSequenceNumber()+" DUP Ack");
							outPacket = packet.toBuilder()
									.setType(2)
									.setPayload((ackTimeStamp+" || "+"-1").getBytes())
									.create();
							channel.send(outPacket.toBuffer(), router);
							ackTimeStamp++;
						}else {
							System.out.println("Packet No: "+packet.getSequenceNumber()+" Ack");
							outPacket = packet.toBuilder()
									.setType(2)
									.setPayload((ackTimeStamp+" || "+"1").getBytes())
									.create();
							channel.send(outPacket.toBuffer(), router);
							ackTimeStamp++;
						}
						window.lastSeqFilled=ackSeq;
					}
				}else if(packet.getType()==4) {
					Packet outPacket=packet.toBuilder()
							.setType(5)
							.create();
					channel.send(outPacket.toBuffer(), router);
				}
			}else {
				//System.out.println("Buffer out of range.");
			}
		}
	}

	public void setUp(){
		this.window=new Window();
		this.windowAttr=new LinkedList<>();
		this.outputPack=new LinkedList<>();
		for(int loop=(int)this.window.windowStart;loop<=(int)this.window.windowEnd;loop++) {
			Integer[] loopArr=new Integer[2];
			loopArr[0]=0;
			loopArr[1]=loop;
			this.windowAttr.add(loopArr);
		}

	}

	public String getOutput() {
		return output;
	}


	public void setOutput(String output) {
		this.output = output;
	}

	public InetAddress getPeerAddress() {
		return peerAddress;
	}


	public void setPeerAddress(InetAddress peerAddress) {
		this.peerAddress = peerAddress;
	}


	public int getPeerPort() {
		return peerPort;
	}


	public void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
	}

}
