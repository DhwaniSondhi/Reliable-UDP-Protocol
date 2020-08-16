package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import addedFiles.UDPSender;

public class ServerImpl{
	String[] inputList=null;
	OutputStream outputStream=null;
	HashMap<String,String> headers=new HashMap<String,String>();
	boolean methodNotRight=false;
	boolean notFound=false;
	boolean badRequest=false;
	InetAddress peerAddress;
	int peerPort;
	/*true:  inline
	  false: attachment
	 */
	boolean contentDisposition=false;
	String outputData="";

	public ServerImpl(){}
	public ServerImpl(String[] inLineList,InetAddress peerAddress,int peerport){
		this.peerAddress=peerAddress;
		this.peerPort=peerport;
		this.inputList=inLineList;
		int loop=0;
		for(String line:inLineList) {
			inputList[loop]=line;
			loop++;
		}
	}

	public void get(String[] firstLines) throws IOException {
		File fileOrFolder=new File(firstLines[1].trim());
		boolean fileExists=fileOrFolder.exists();
		if(fileExists  &&  fileOrFolder.isDirectory()) {
			for(File file:fileOrFolder.listFiles()) {
				outputData+=file.getName()+"\n";
			}
			headers.put("Content-Type", "text");
		}else if(fileExists  &&  fileOrFolder.isFile()) {
			BufferedReader reader=new BufferedReader(new FileReader(fileOrFolder));
			String fileLine="";
			while((fileLine=reader.readLine())!=null) {
				outputData+=fileLine+"\n";
			}
			int extensionIndex=fileOrFolder.getName().lastIndexOf(".")+1;
			String extension=fileOrFolder.getName().substring(extensionIndex);
			if(extension.toUpperCase().equalsIgnoreCase("JSON")) {
				headers.put("Content-Type", "Json");
			}else if(extension.toUpperCase().equalsIgnoreCase("XML")) {
				headers.put("Content-Type", "XML");
			}else if(extension.toUpperCase().equalsIgnoreCase("TXT")) {
				headers.put("Content-Type", "text");
			}else if(extension.toUpperCase().equalsIgnoreCase("HTML")) {
				headers.put("Content-Type", "html");
			}else {
				headers.put("Content-Type", extension.toUpperCase());
			}
		}else if(!fileExists) {
			notFound=true;
		}
		headers.put("Content-Length", String.valueOf(outputData.trim().length()));
	}
	public synchronized void post(String[] firstLines,String saveData) throws IOException {
		headers.put("Content-Length", "0");
		File file=new File(firstLines[1].trim());
		if(!file.exists()) {
			file.getParentFile().mkdirs();
		}
		FileWriter fileWriter=new FileWriter(file,false);
		fileWriter.write(saveData);
		fileWriter.close();

	}

	public void receiveData(){
		String[] firstLines=inputList[0].trim().split(" ");
		boolean get=firstLines[0].trim().toUpperCase().equalsIgnoreCase("GET");
		boolean post=firstLines[0].trim().toUpperCase().equalsIgnoreCase("POST");
		boolean bool2=firstLines[1].trim().indexOf(HttpServer.filePath)>-1;

		if(get  &&  HttpServer.printDebugMessage) {
			System.out.println("The client asked for a get request with the path: "+firstLines[1].trim());
		}else if(post  &&  HttpServer.printDebugMessage) {
			System.out.println("The client asked for a post request with the path: "+firstLines[1].trim());
		}

		int loop=1;
		for(;loop<inputList.length;loop++) {
			String[] header=inputList[loop].trim().split(":");
			headers.put(header[0].trim(), header[1].trim());
			if(inputList[loop].toUpperCase().indexOf("HOST")>-1) {
				break;
			}
			if(header[0].equalsIgnoreCase("Content-Disposition")) {
				contentDisposition=true;
			}
		}
		if(!contentDisposition) {
			headers.put("Content-Disposition", "inline");
		}

		if((get  ||  post)  &&  bool2) {
			String inputData="";
			String saveData="";
			try{
				if(get) {
					get(firstLines);
				}else if(post) {
					++loop;
					for(;loop<inputList.length;loop++) {
						if(inputList[loop].trim().length()>0) {
							saveData+=inputList[loop]+"\n";
						}
					}
					post(firstLines,saveData);
				}
			}catch(IOException e) {
				badRequest=true;
			}

		}else {
			methodNotRight=true;
		}
		
	}
	public String sendData() throws IOException {
		String sendData="";
		for(Map.Entry<String, String> pair:headers.entrySet()) {
			boolean error=methodNotRight  ||  badRequest  ||  notFound;
			if(error &&  (pair.getKey().equalsIgnoreCase("Content-Length")  ||  pair.getKey().equalsIgnoreCase("Content-Type"))) {
				continue;
			}
			if(!pair.getKey().toUpperCase().equalsIgnoreCase("HOST")) {
				sendData+=pair.getKey()+": "+pair.getValue()+"\r\n";
			}

		}
		String msg="";
		if(methodNotRight) {
			msg="-----Method not allowed is returned.";
			String addingData="<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\r\n" 
					+"<title>405 Method Not Allowed</title>\r\n" 
					+"<h1>Method Not Allowed</h1>\r\n" 
					+"<p>The method is not allowed for the requested URL.</p>";
			sendData="HTTP/1.1 405 METHOD NOT ALLOWED\r\n"
					+sendData
					+"Content-Length: "+addingData.length()+"\r\n"
					+"Content-Type: html"+"\r\n"
					+"Host: "+headers.get("Host")+"\r\n"
					+"\r\n"
					+addingData;
		}else if(notFound){
			msg="-----Not found is returned.";
			String addingData="<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\r\n"  
					+"<title>404 Not Found</title>\r\n"  
					+"<h1>Not Found</h1>\r\n"  
					+"<p>The requested URL was not found on the server.  If you entered the URL manually please check your spelling and try again.</p>\r\n"; 
			sendData="HTTP/1.1 404 NOT FOUND\r\n"
					+sendData
					+"Content-Length: "+addingData.length()+"\r\n"
					+"Content-Type: html"+"\r\n"
					+"Host: "+headers.get("Host")+"\r\n"
					+"\r\n"
					+addingData;
		}else if(badRequest){
			msg="-----Bad request is returned.";
			sendData="HTTP/1.1 400 BAD_REQUEST\r\n"
					+sendData
					+"Content-Length: 0"+"\r\n"
					//+"Content-Type: html"+"\r\n"
					+"Host: "+headers.get("Host")+"\r\n"
					+"\r\n";
		}else {
			msg="-----Ok message is returned.";
			sendData="HTTP/1.1 200 OK\r\n"
					+sendData
					+"Host: "+headers.get("Host")+"\r\n"
					+"\r\n"
					+outputData;
		}
		if(HttpServer.printDebugMessage) {
			System.out.println(msg);
		}		
		return sendData;

	}
	public String getAns() {

		try {
			receiveData();
			String sendData=sendData();
			if(peerAddress!=null) {
				System.out.println(sendData);
				return sendData;
				//udpSender.execute(sendData,peerAddress,peerPort);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
