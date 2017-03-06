package nankai.oram.client.Test.TestReadWrite;

import nankai.oram.client.PartitionClient;
import nankai.oram.common.CommInfo;
import nankai.oram.common.Util;


/**
 * This is the init oram program
 * Each test should after the initilization of ORAM
 * @author Dell
 *
 */
public class SSSInitOram {

	public static void main(String[] args) {   
		
		
		PartitionClient oram=new PartitionClient();
		

		//initialize the client
		oram.init(1024000);
		oram.openConnection();
		

		long testTime = -1; 
    	long testDoneTime = -1; 
        testTime = System.currentTimeMillis(); //ms  
		//initalize the server
		oram.initORAM(); 

        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
		
		oram.closeConnection();
		
	}
}
