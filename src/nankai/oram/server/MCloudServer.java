package nankai.oram.server;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.server.mcloud.MCloudPartitionServerThread;
import nankai.oram.server.mcloud.NewMCloudPartitionServerThread;

public class MCloudServer {
    private int queueSize = 100;  
    private static int port = 2121;  
	public MCloudServer() {
		try {  
			boolean listening = true; // �Ƿ�Կͻ��˽��м��� 
			ServerSocket serverSocket = null; // ��������Socket���� 
			try {
				// ����һ��ServerSocket�ڶ˿�2121�����ͻ�����
				serverSocket = new ServerSocket(); 
				
				 //�ر�serverSocketʱ�������ͷ�serverSocket�󶨶˿��Ա�˿����ã�Ĭ��Ϊfalse  
	            serverSocket.setReuseAddress(false);  
	            //accept�ȴ����ӳ�ʱʱ��Ϊ1000���룬Ĭ��Ϊ0��������ʱ  
	            //serverSocket.setSoTimeout(10000);  
	            //Ϊ����accept�������ص�socket�������ý��ջ�������С����λΪ�ֽڣ�Ĭ��ֵ�Ͳ���ϵͳ�й�  
	            serverSocket.setReceiveBufferSize(128*1024);  
	            //�������ܲ�����������������������ֵԽ����Ӧ�Ĳ�����Ҫ��Խ�ߣ�����ʱ�䣬�ӳ٣�����  
	            serverSocket.setPerformancePreferences(3, 2, 1);  
	            //����˰����˿ڣ�10Ϊ���������������г���  
	            serverSocket.bind(new InetSocketAddress(port), queueSize); 
				System.out.println("Server starts..." + port);
			} catch (Exception e) {
				System.out.println("Can not listen to. " + e);
			}

			while (listening) { 
				// �������ͻ�����,���ݵõ���Socket����Ϳͻ��������������߳�,������֮
				//new ServerThread(server.accept()).start();
				new MCloudPartitionServerThread(serverSocket.accept()).start(); 
			}
		} catch (Exception e) {
			System.out.println("Error.... " + e);
		}
	}

	public static void main(String[] args) {
		/**
		 * To run in a computer to simulate the multiple servers, we use the parameters of main to tell the socket bind port 
		 */
		if (args.length>0){
			//the pos is cloud - 1
			int cloud = Integer.parseInt(args[0]);
			MCloudServer.port=MCloudCommInfo.port[cloud-1];
			System.out.println("cloud "+cloud+" port:"+MCloudServer.port);
		}
		new MCloudServer();
	}


}

