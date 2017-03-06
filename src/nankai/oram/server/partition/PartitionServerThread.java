package nankai.oram.server.partition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.Util;

public class PartitionServerThread extends Thread {
 

	Socket socket = null; // 保存与本线程相关的Socket对象
    byte recvCmd[];
    byte recvMsg[];
    byte sendMsg[];
    PartitionORAMServer oram;

	public PartitionServerThread(Socket socket) { 
		this.socket = socket; 
		recvCmd=new byte[13]; 
		recvMsg=new byte[10240];
		sendMsg=new byte[10240]; 
		oram=PartitionORAMServer.getInstance();
	}

	public void run() {  
		DataInputStream in=null;
		DataOutputStream out=null;
		try {

			in=new DataInputStream(socket.getInputStream());
			out=new DataOutputStream(socket.getOutputStream());
			 
			// 由Socket对象得到输出流,并构造PrintWriter对象
			byte type=0;
			int len=0;

			while (true)
			{ 
				type=0; 
				len=0;
				while ( (len = in.read(recvCmd, 0, 13)) >0) {
					type = recvCmd[0];
//					System.out.println("-----------------------------------------------------------");
//					System.out.println("receive a command type:"+  type+"  "+ recvCmd[1]);
					break;
				}

				if (type == CommandType.closeThread)
					break;

				switch (type)
				{
				case CommandType.openDB:
				{
					System.out.println("open DB");
					if (oram.openDB()==false)
						System.out.println("open DB ERROR");
					out.writeByte(ResponseType.normal);
					out.flush(); 
					
					break;
				}
				case CommandType.testTime:
				{ 
						out.writeByte(ResponseType.normal);
						out.flush(); 
					break;
				}
				case CommandType.initORAM:
				{
					if (len!=5){
//						System.out.println("initORAM　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					}else
					    initORAM(out);
					break;
				}
				case CommandType.readBlock:
				{ 
					if (len != 9) {
						System.out.println("readBlock　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else { 
						/**************************************
						 * partition+_id 
						 * **********************************/
						readBlock(out);
					}
					break;
				}
				case CommandType.writeBlock:
				{ 
					if (len != 13) {
						System.out.println("writeBlock　error data!　" + len);

						for (int i=0;i<len;i++)
							System.out.print(recvCmd[i]+" ");
						System.out.println();
						
						while (in.read(recvMsg, 0, 10240) > 0) {
							break;
						}

						for (int i=0;i<3;i++)
							System.out.print(recvMsg[i]+" ");
						System.out.println();
						
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						while (in.read(recvMsg, 0, 10240) > 0) {
							break;
						}
						/**************************************
						 * partition+_id+length+block
						 * **********************************/
						writeBlock(out);
					}
					break;
				}
				
				}

			}
 

		} catch (Exception e) {
			System.out.println("Error.?>>>>> " + e);
			e.printStackTrace();
		}finally{ 
			try {
				out.close(); // 关闭Socket输出流
				in.close();// 关闭Socket输入流 
				socket.close(); // 关闭Socket
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.println("closing........ ");
		}
	}

	private void initORAM(DataOutputStream out) throws IOException {
		/*********
		 * LEN = 4, N value
		 * *********/ 
//		System.out.println("init oram");

		int N = Util.byteToInt(recvCmd, 1, 4);
//		System.out.println("init N blocks:" + N);
		oram.init(N);

//		System.out.println("init OK."); 
		out.writeByte(ResponseType.normal);
		out.flush();
	}
	
	private void readBlock(DataOutputStream out ) throws IOException {
		/*********
		 * P + ID
		 * *********/
		//System.out.println("readBlock");
 
		int p = Util.byteToInt(recvCmd, 1, 4);
		int _id = Util.byteToInt(recvCmd, 5, 4); 
		//System.out.println("p _id :" + p + "  " + _id );

	    oram.readBlock(p, _id, sendMsg);

		out.writeByte(ResponseType.normalWithValue);
		out.flush();
		out.write(sendMsg, 0, CommInfo.blockSize);
		//System.out.println("return back data:" + sendMsg[0]+"  " +sendMsg[5]);
		out.flush();
	}

	private void writeBlock(DataOutputStream out ) throws IOException {

		//System.out.println("writeBlock");
 
		int p = Util.byteToInt(recvCmd, 1, 4);
		int _id = Util.byteToInt(recvCmd, 5, 4);
		int len = Util.byteToInt(recvCmd, 9, 4);
		//System.out.println("p _id  len:" + p + "  " + _id + " "
		//		+ len);
		//System.out.println(recvMsg[0]+"  "+recvMsg[1]+"........");
		if (len!=CommInfo.blockSize)
			System.out.println("Error Length");
		
        oram.writeBlock(p, _id, recvMsg, 0);

		//System.out.println("write OK."); 
		out.writeByte(ResponseType.normal);
		out.flush();
	}
}
