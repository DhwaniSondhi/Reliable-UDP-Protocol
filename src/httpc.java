import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

import addedFiles.UDPReceiver;
import addedFiles.UDPSender;


public class httpc {
	private int getOrPost=-1;
	private boolean verbose;
	private String inlineData;
	private String fileData;
	private boolean outFile;
	private String inputFileName;
	private URL url;
	private int repeatRedirect=-1;
	private HashMap<String,String> headers=new HashMap<>();
	private boolean hasFileWrite;
	private String outputFileName;

	/*
	 * To remove empty arguments
	 */
	public String[] removeSpaces(String[] args) {
		ArrayList<String> values=new ArrayList<String>();
		for(String loop:args) {
			if(loop.trim().length()>0)			values.add(loop.trim());
		}
		return values.toArray(new String[values.size()]);
	}
	/*
	 * To save data in the file or console
	 */
	public void print(String val){
		try {
			if(!hasFileWrite) {
				System.out.println(val);
			}else {
				File file=new File(outputFileName);
				file.createNewFile();
				FileWriter fileWrt = new FileWriter(file);
				PrintWriter printWrt = new PrintWriter(fileWrt);
				printWrt.print(val);
				printWrt.close();
				fileWrt.close();
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	/*
	 * To get data from the file
	 */
	public String getDataFromFile(String fileName) {
		File file=new File("C:\\Users\\Dhwani\\eclipse-workspace\\CNProj\\src\\"+fileName);
		String output="";
		try(FileReader fileRead = new FileReader(file);
				BufferedReader bufReader = new BufferedReader(fileRead)){
			String readVal=null;
			int len=0;
			while((readVal=bufReader.readLine())!=null) {
				if(len!=0) {
					output+="\n";
				}
				output+=readVal;
				len++;
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
		return output;
	}
	/*
	 * To set values in the class variables
	 */
	public boolean setValues(String[] args) {
		args=removeSpaces(args);
		for(int i=0;i<args.length;i++) {
			String loop=args[i];
			switch(loop.toLowerCase()) {
			case "get":	getOrPost=0;
			break;

			case "post":getOrPost=1;
			break;

			case "-v":	verbose=true;
			break;

			case "-h":	if( args[i+1].contains(":")) {
				args[i+1]=args[i+1].substring(1,args[i+1].length()-1);
				headers.put(args[i+1].split(":")[0],args[i+1].split(":")[1]);
				++i;
			}
			break;

			case "-d":	inlineData=args[i+1].substring(1,args[i+1].length()-1);++i;
			break;

			case "-f":	inputFileName=args[i+1];fileData=getDataFromFile(args[i+1]);++i;
			break;

			case "-o":	if(args[i+1].trim().length()>0) {
				hasFileWrite=true;
				outputFileName=args[i+1];
				++i;
			}
			break;

			default: 	try {
				String urlString=loop.substring(1,loop.length()-1);

				URL ur=new URL(urlString);
				ur.getHost();
				url=new URL(urlString);				
			}catch(Exception e) {
				e.printStackTrace();
			}
			}

		}
		if(url==null) {
			System.out.println("The URL is not correct");
			return false;
		}else if(getOrPost!=0  &&  getOrPost!=1){
			System.out.println("Either get or post methods must be included.");
			return false;
		}else if(getOrPost==0  &&  (inlineData!=null  ||  fileData!=null)) {
			System.out.println("[-d] or [-f] can only be used with post request");
			return false;
		}else if(getOrPost==1  &&  ((inlineData!=null)  &&  (fileData!=null))) {
			System.out.println("Either [-d] or [-f] should be used with post request but not both");
			return false;
		}
		if(!headers.containsKey("Connection")) {
			headers.put("Connection","close");
		}
		return true;
	}
	/*
	 * To send to the server
	 */
	public void send(String host,int port) throws IOException{
		//DataOutputStream dataOutputStream=new DataOutputStream(out);
		String str="";
		String path=url.getPath();
		if(path==null  ||  path.trim().length()==0) {
			path+="/";
		}
		//UDPSender udpSender=new UDPSender();
		if(getOrPost==0) {
			str = "GET "+path;
			String query=url.getQuery();
			if(query!=null  &&  query.trim().length()>0) {
				str+="?"+query;
			}
			str+=" HTTP/1.0\r\n";
			for(Map.Entry<String, String> pair:headers.entrySet()) {
				str+=pair.getKey()+":"+(pair.getValue().toString())+"\r\n";
			}

			str+="Host:"+host+"\r\n\r\n";
			
		}else if(getOrPost==1) {
			str = "POST "+path+" HTTP/1.0\r\n";
			String data="";
			if(inlineData!=null) {
				data=inlineData;
			}else if(fileData!=null){
				data=fileData;
			}

			headers.put("Content-Length", String.valueOf(data.length()));
			for(Map.Entry<String, String> pair:headers.entrySet()) {
				str+=pair.getKey()+":"+(pair.getValue().toString())+"\r\n";
			}
			str+="Host:"+host+"\r\n";
			if(data.trim().length()>0) {
				str+=data.trim()+"\r\n";
			}
			
			
			//dataOutputStream.writeUTF(str);
		}
		UDPSender udpSender=new UDPSender(false);
		if(host!=null  &&  host.trim().length()>0) {
			DatagramChannel channel=udpSender.execute(str,InetAddress.getByName(host),port,true);
			if(channel!=null) {
				UDPReceiver udpReceiver=new UDPReceiver(channel,InetAddress.getByName(host),port);
				udpReceiver.setUp();
				udpReceiver.receive();
				System.out.println("------------------------------------------------------");
				recieve(udpReceiver.getOutput());
			}
			//channel.close();
			
		}
		//bind the client channel with available port
		//link in notepad
		
	}
	/*
	 * To receive the data from the server
	 */
	public void recieve(String dataInputStream) throws IOException{
		int len=0;
		String printData="";
		String redirectData="";
		boolean redirect=false;

		for(String val:dataInputStream.split("\\r?\\n")) {
			int indexContentDis=val.indexOf("Content-Disposition");
			if(indexContentDis>-1) {
				String valueSecond=val.split(":")[1].trim();
				if(valueSecond.toLowerCase().indexOf("attachment")>-1) {
					hasFileWrite=true;
					int index=valueSecond.toLowerCase().indexOf("filename");
					if(index==-1) {
						outputFileName="attachment.txt";
					}else {
						outputFileName=valueSecond.substring(index+10,valueSecond.length()-1);
					}
				}
			}
			if(!verbose && len!=0) {
				printData+=val+"\n";
			}else if(verbose){
				printData+=val+"\n";
			}
			if(val.trim().length()==0) {
				len++;
			}else {
				if(val.toUpperCase().indexOf("HTTP")>-1   &&  val.matches("^.*\\s3\\d\\d\\s.*$")  &&  !redirect) {
					int index=val.indexOf("HTTP");
					if(index>-1) {
						int code=(int)Integer.parseInt(val.substring(index+9,index+12));
						if(code>308) {
							System.out.println("The codes greater than 308 are unassigned.");
						}else {
							redirect=true;
						}
					}
				}
			}
			redirectData+=val+"\n";
		}
		repeatRedirect=repeatRedirect==-1?5:repeatRedirect-1;
		if(redirect) 					redirect(redirectData.split("\n"),repeatRedirect);
		else							print(printData);

	}
	/*
	 * This method implements a redirect function
	 */
	public void redirect(String [] lines,int renum){//redirects repeat amount of times
		if(renum>0){
			Queue<String> que=new LinkedList<>();
			if(getOrPost==0)				que.add("get");
			else if(getOrPost==1)			que.add("post");
			if(verbose)						que.add("-v");
			if(inlineData!=null) {
				que.add("-d");
				que.add("'"+inlineData+"'");
			}
			if(inputFileName!=null) {
				que.add("-f");
				que.add(inputFileName);
			}
			for(int i=0;i<lines.length;i++) {
				if(lines[i].toUpperCase().indexOf("LOCATION")>-1) {

					int index=lines[i].indexOf(":");
					String urlValue=lines[i].substring(index+1);

					String newUrl="";
					try {
						if(new URL(urlValue).getHost()!=null) {
							newUrl="'"+urlValue.trim()+"'";
						}
					}catch(Exception e) {
						newUrl="'https://"+url.getHost().trim()+urlValue.trim()+"'";
					}
					System.out.println("-----------------------The request has been redirected to "+newUrl);
					que.add(newUrl);
				}
			}
			if(headers.size()>0) {
				for(Map.Entry<String, String> pair:headers.entrySet()) {
					que.add("-h");
					que.add(pair.getKey()+":"+pair.getValue());
				}
			}
			if(hasFileWrite) {
				que.add("-o");
				que.add(outputFileName);
			}
			String[] args=new String[que.size()];
			args=que.toArray(args);
			execute(args);
		}else {
			System.out.println("This cannot be redirected more than 5 times.");
		}
	}
	/*
	 * To execute all the operations
	 */
	public void execute(String[] args) {
		boolean result=setValues(args);
		if(result) {
			String host=url.getHost();
			int port=url.getPort();
			if(port==-1) {
				port=80;
			}		
			try {
				send(host,port);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			/*try(Socket socket=new Socket(host,port)) {
				send(host,socket.getOutputStream());
				recieve(socket);
			}catch(Exception e) {
				e.printStackTrace();
			}*/
		}
	}
	/*
	 * The start up method
	 */
	public static void main(String[] args) throws UnknownHostException, IOException {
		Scanner scan=new Scanner(System.in);
		boolean rotate=true;
		while(rotate) {

			System.out.println("\nEnter the options:"
					+ "\n1.Run the inbuilt requests"
					+ "\n2.Enter you own request"
					+ "\n3.Exit");
			switch(scan.nextInt()) {
			case 1:
				new Thread( ()->{
					
						System.out.println();
						String str1="post 'http://localhost:9000/Users/Dhwani/eclipse-workspace/CNProj/src/file.json' -v -d \"{\"second\":1}\"";
						String vals1[]=str1.split(" ");
						new httpc().execute(vals1);
						System.out.println();
					
				}).start();
				new Thread( ()->{
					
						System.out.println();
						String str1="post 'http://localhost:9000/Users/Dhwani/eclipse-workspace/CNProj/src/file.json' -v -d \"{\"first\":1}\"";
						String vals1[]=str1.split(" ");
						new httpc().execute(vals1);
						System.out.println();
					
				}).start();
			
			
			break;
			case 2:System.out.println("Enter the request");
			scan.nextLine();
			String input=scan.nextLine();
			int index=input.indexOf("httpc");
			if(index>-1) {
				String vals[]=input.substring(index+5).split(" ");
				int indexHelp=input.indexOf("help");
				if(indexHelp>-1) {
					if(input.toLowerCase().indexOf("get")>-1) {
						System.out.println("usage: httpc get [-v] [-h key:value] URL Get executes a HTTP GET request for a given URL. -v Prints the detail of the response such as protocol, status, and headers. -h key:value Associates headers to HTTP Request with the format 'key:value'.");
					}else if(input.toLowerCase().indexOf("post")>-1) {
						System.out.println("usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL Post executes a HTTP POST request for a given URL with inline data or from file. -v Prints the detail of the response such as protocol, status, and headers. -h key:value Associates headers to HTTP Request with the format 'key:value'. -d string Associates an inline data to the body HTTP POST request. -f file Associates the content of a file to the body HTTP POST request. Either [-d] or [-f] can be used but not both.");
					}
				}else {
					new httpc().execute(vals);
				}


			}

			break;
			case 3:rotate=false;
			break;
			}
		}
		scan.close();
	}
}
