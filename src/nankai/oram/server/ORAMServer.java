package nankai.oram.server;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import nankai.oram.server.partition.PartitionServerThread;

public class ORAMServer {

    private int queueSize = 10;  
    private int port = 2121;  
	public ORAMServer() {
		try {  
			boolean listening = true; // 是否对客户端进行监听 
			ServerSocket serverSocket = null; // 服务器端Socket对象 
			try {
				// 创建一个ServerSocket在端口2121监听客户请求
				serverSocket = new ServerSocket(); 
				
				 //关闭serverSocket时，立即释放serverSocket绑定端口以便端口重用，默认为false  
	            serverSocket.setReuseAddress(true);  
	            //accept等待连接超时时间为1000毫秒，默认为0，永不超时  
	            //serverSocket.setSoTimeout(10000);  
	            //为所有accept方法返回的socket对象设置接收缓存区大小，单位为字节，默认值和操作系统有关  
	            serverSocket.setReceiveBufferSize(128*1024);  
	            //设置性能参数，可设置任意整数，数值越大，相应的参数重要性越高（连接时间，延迟，带宽）  
	            serverSocket.setPerformancePreferences(3, 2, 1);  
	            //服务端绑定至端口，10为服务端连接请求队列长度  
	            serverSocket.bind(new InetSocketAddress(port), queueSize); 
				System.out.println("Server starts...");
			} catch (Exception e) {
				System.out.println("Can not listen to. " + e);
			}

			while (listening) { 
				// 监听到客户请求,根据得到的Socket对象和客户计数创建服务线程,并启动之
				//new ServerThread(server.accept()).start();
				new PartitionServerThread(serverSocket.accept()).start(); 
			}
		} catch (Exception e) {
			System.out.println("Error.... " + e);
		}
	}

	public static void main(String[] args) {
		// TODO 自动生成的方法存根
		new ORAMServer();
	}

}
