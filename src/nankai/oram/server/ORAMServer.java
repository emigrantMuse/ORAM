package nankai.oram.server;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import nankai.oram.server.partition.PartitionServerThread;

public class ORAMServer {

    private int queueSize = 10;  
    private int port = 2121;  
	public ORAMServer() {
		try {  
			boolean listening = true; // �Ƿ�Կͻ��˽��м��� 
			ServerSocket serverSocket = null; // ��������Socket���� 
			try {
				// ����һ��ServerSocket�ڶ˿�2121�����ͻ�����
				serverSocket = new ServerSocket(); 
				
				 //�ر�serverSocketʱ�������ͷ�serverSocket�󶨶˿��Ա�˿����ã�Ĭ��Ϊfalse  
	            serverSocket.setReuseAddress(true);  
	            //accept�ȴ����ӳ�ʱʱ��Ϊ1000���룬Ĭ��Ϊ0��������ʱ  
	            //serverSocket.setSoTimeout(10000);  
	            //Ϊ����accept�������ص�socket�������ý��ջ�������С����λΪ�ֽڣ�Ĭ��ֵ�Ͳ���ϵͳ�й�  
	            serverSocket.setReceiveBufferSize(128*1024);  
	            //�������ܲ�����������������������ֵԽ����Ӧ�Ĳ�����Ҫ��Խ�ߣ�����ʱ�䣬�ӳ٣�����  
	            serverSocket.setPerformancePreferences(3, 2, 1);  
	            //����˰����˿ڣ�10Ϊ���������������г���  
	            serverSocket.bind(new InetSocketAddress(port), queueSize); 
				System.out.println("Server starts...");
			} catch (Exception e) {
				System.out.println("Can not listen to. " + e);
			}

			while (listening) { 
				// �������ͻ�����,���ݵõ���Socket����Ϳͻ��������������߳�,������֮
				//new ServerThread(server.accept()).start();
				new PartitionServerThread(serverSocket.accept()).start(); 
			}
		} catch (Exception e) {
			System.out.println("Error.... " + e);
		}
	}

	public static void main(String[] args) {
		// TODO �Զ����ɵķ������
		new ORAMServer();
	}

}
