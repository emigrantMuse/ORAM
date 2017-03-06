package nankai.oram.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketClientUtil {
	String ip;
	int port;
	
	Socket socket;
	DataInputStream  in;  
	DataOutputStream out; 

    byte recvMsg[];
    byte sendMsg[];
    
    public static int responseType;//the returned value from the cloud server

	public SocketClientUtil(String ip, int port) {
		this.ip = ip;
		this.port = port;
		recvMsg=new byte[1024];//1k
		sendMsg=new byte[4];//100k
		responseType=0;
	}
	 

	public boolean connect() {
		try {
			// 向本机的2121端口发出客户请求
			socket = new Socket(ip, port);
//			System.out.println("Established a connection...");   
			
			socket.setKeepAlive(true);
			out = new DataOutputStream(socket.getOutputStream()); 
			in = new DataInputStream(socket.getInputStream());
			// 由Socket对象得到输出流,并构造PrintWriter对象
		} catch (Exception e) {
			System.out.println("Error connect.  " + e);
			return false;
		}
//		System.out.println("success for connection. " );
		return true;
	}

	public void disConnect() {
		try { 			
			out.write(CommandType.closeThread);
			out.flush();
			out.close(); // 关闭Socket输出流
			in.close(); // 关闭Socket输入流
			socket.close(); // 关闭Socket 
		} catch (Exception e) {
			System.out.println("Error. " + e);
		}
	}
	

	/**
	 * 
	 * @param type  request command type
	 * @param cmdInfo some information for specific command 
	 * @param cmdLen the length of the cmdInfo
	 * @param sendData  the data which contains the concrete command information
	 * @param dataLen   the data length
	 * @param receiveData TODO
	 * @param retValue the returned data
	 * @return
	 */
	public void send(byte[] cmdInfo, int cmdLen, byte[] sendData, int dataLen, byte[] receiveData) {
		try { 
			/************************
			 * Send 
			 * (1) the request - command
			 * CommandType | len | data
			 * And (2) the message content
			 * *************************/  
			
			out.write(cmdInfo, 0, cmdLen);  
			out.flush();
			if (dataLen > 0) {
//			    out.write(sendData, 0, dataLen);  
//			    out.flush();
 	    
				if (dataLen<512)
				{
				    out.write(sendData, 0, dataLen);  
				    out.flush();
				}else{ 
					int num = dataLen / 512  ;
					int leftLen = dataLen % 512;
					int hasSend = 0;
					for (int i=0;i<num;i++)
					{ 
						out.write(sendData, hasSend, 512);
						out.flush();
						hasSend += 512;
					}
					if (leftLen>0){
					    out.write(sendData, hasSend, leftLen);
					    out.flush();
					}
				}
				/*********************
				 * Send them with different block
				 * *********************/
			}
  
			/*************************
			 * Receive the response
			 * ResponseType | len | data
			 * *********************/
			while (in.read(recvMsg, 0, 1024)  > 0) {
				break;
			}
			responseType=recvMsg[0]; 
		    //System.out.println("server return：" + responseType); 
			if (responseType==ResponseType.wrong){
				System.out.println(cmdInfo[0]+"  "+cmdLen+"  "+dataLen+"........");

				for (int i=0;i<cmdLen;i++)
					System.out.print(cmdInfo[i]+" "); 
				System.out.println();
				
				for (int i=0;i<3;i++)
					System.out.print(sendData[i]+" "); 
				System.out.println();
				
				
			    System.out.println("Return error value!");
			}else{
			    //System.out.println("server return success：" + responseType); 
				if (responseType == ResponseType.normalWithValue)
				{ 
					int retLen=0;
					while ( (retLen = in.read(recvMsg, 0, 1024))  > 0) {
						break;
					}
					
					if (retLen != CommInfo.blockSize)
					    System.out.println("Wrong length" ); 
					
					if (receiveData!=null && retLen == CommInfo.blockSize)
					    System.arraycopy(recvMsg, 0, receiveData, 0, retLen);  
				}
			}
		} catch (IOException e) {
			e.printStackTrace(); 
		}
	}
	

	public void send(byte[] sendData, int dataLen) {
		try { 
//		    out.write(sendData, 0, dataLen);  
//		    out.flush(); 
			if (dataLen<512)
			{
			    out.write(sendData, 0, dataLen);  
			    out.flush();
			}else{ 
				int length = dataLen / 512;
				int leftLen = dataLen % 512;
				int pos = 0;

				for (int i = 0; i < length; i++) {
					out.write(sendData, pos, 512);
					out.flush();
					pos += 512;
				}
				if (leftLen > 0) {
					out.write(sendData, pos, leftLen);
					out.flush();
				}
			}
		} catch (IOException e) {
			e.printStackTrace(); 
		}
	}
	

	public int receiving(byte[] recvData, int len) {
		int retLen = -1;
		try {   
			retLen = in.read(recvData, 0, len) ;
		} catch (IOException e) {
			e.printStackTrace(); 
		}
		return retLen;
	}
	

	public void sendAndReceive(byte[] cmdInfo, int cmdLen) {
		try {  

			out.write(cmdInfo, 0, cmdLen);  
			out.flush();
			
			/*************************
			 * Receive the response
			 * ResponseType | len | data
			 * *********************/
			while (in.read(recvMsg, 0, 1024)  > 0) {
				break;
			}
			responseType=recvMsg[0]; 
		    //System.out.println("server return：" + responseType); 
			if (responseType==ResponseType.wrong){
				System.out.println(cmdInfo[0]+"  "+cmdLen+"........");
			}
		} catch (IOException e) {
			e.printStackTrace(); 
		}
	}

}
