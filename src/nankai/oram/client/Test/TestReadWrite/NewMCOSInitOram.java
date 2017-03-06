package nankai.oram.client.Test.TestReadWrite;

import nankai.oram.client.NewMCOSORAMClient;
import nankai.oram.client.PartitionClient;

/**
 * This is the init oram program
 * Each test should after the initilization of ORAM
 * @author Dell
 *
 */

public class NewMCOSInitOram {

	public static void main(String[] args) {   
		
		
		NewMCOSORAMClient oram=new NewMCOSORAMClient(); 

		//initialize the client
		oram.init(102400);
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
