package nankai.oram.client.Test;

import nankai.oram.common.CommandType;
import nankai.oram.common.SocketClientUtil;

public class TestSocket {

	public static void main(String[] args) { 
		
		SocketClientUtil cli=new SocketClientUtil("114.215.26.85",2121);
		long testTime = -1;
		long testTime1 = -1;
    	long testDoneTime = -1;
    	long testDoneTime1 = -1;
        
		cli.connect(); 

		byte[] bData=new byte[1024];
		for (int i=0;i<1024;i++)
			bData[i]=1;
        testTime = System.currentTimeMillis(); //ms  
        testTime1 = System.nanoTime();//ns 
		//bData[0]=CommandType.testTime; 
		
		for (int i=0;i<1024;i++)
		    cli.send(bData, 1024);  
		

        testDoneTime = System.currentTimeMillis(); 
        testDoneTime1 = System.nanoTime(); 
		
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        double totalElapsedTime1 = (testDoneTime1 - testTime1);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
        System.out.println("totalElapsedTime1:"+totalElapsedTime1);  
		cli.disConnect();
		
	}
}
