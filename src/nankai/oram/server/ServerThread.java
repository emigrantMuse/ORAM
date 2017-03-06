package nankai.oram.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.server.partition.PartitionORAMServer;

public class ServerThread extends Thread {

	Socket socket = null; // �����뱾�߳���ص�Socket����
    byte recvCmd[]; 

	public ServerThread(Socket socket) { 
		this.socket = socket; 
		recvCmd=new byte[1024];  
	}

	public void run() {  
		DataInputStream in=null;
		DataOutputStream out=null;
		try {

			in=new DataInputStream(socket.getInputStream());
			out=new DataOutputStream(socket.getOutputStream());
			 
			// ��Socket����õ������,������PrintWriter����
			byte type=0;
			int len=0;

			while (true) {

				for (int i = 0; i < 1024; i++) {
					if ( (len = in.read(recvCmd, 0, 1024))>0)
						;//System.out.println("received"+len);
				}
				//System.out.println("over"+len);
				break;
//				type = 0;
//				len = 0;
//				while ((len = in.read(recvCmd, 0, 13)) > 0) {
//					type = recvCmd[0]; 
//					break;
//				}
//
//				if (type == CommandType.testTime) {
//					System.out.println("return - ");
//					out.writeByte(ResponseType.normal);
//					out.flush();
//				}

			}
 

		} catch (Exception e) {
			System.out.println("Error.?>>>>> " + e);
			e.printStackTrace();
		}finally{ 
			try {
				out.close(); // �ر�Socket�����
				in.close();// �ر�Socket������ 
				socket.close(); // �ر�Socket
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.println("closing........ ");
		}
	}
}
