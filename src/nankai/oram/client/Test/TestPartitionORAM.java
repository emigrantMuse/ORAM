package nankai.oram.client.Test;

import nankai.oram.client.PartitionClient;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.Util;

/**
 * Test whether it is work normally
 * @author Dell
 *
 */

public class TestPartitionORAM {

	public static void main(String[] args) { 
//		ClientUtil cli=new ClientUtil("127.0.0.1",2121);
//		cli.connect(); 
//		
//		byte[] bData=new byte[4];
//		Util.intToByte(bData, 0, 1024);
//		cli.send(CommandType.initORAM, null, 0, bData, 4, null);  
//		cli.disConnect();

		long testTime = -1;
		long testTime1 = -1;
    	long testDoneTime = -1;
    	long testDoneTime1 = -1;
		
		
		PartitionClient oram=new PartitionClient();
		

		//initialize the client
		oram.init(400);
		oram.openConnection();
		

		//initalize the server
		oram.initORAM();

		

        testTime = System.currentTimeMillis(); //ms  
        testTime1 = System.nanoTime();//ns 
		//write some data
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < 33; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) id;
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}

        testDoneTime = System.currentTimeMillis(); 
        testDoneTime1 = System.nanoTime();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        double totalElapsedTime1 = (testDoneTime1 - testTime1);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
        System.out.println("totalElapsedTime1:"+totalElapsedTime1); 
        
		System.out.println("-----ready to read the block !-----------");

        testTime = System.currentTimeMillis(); //ms  
        testTime1 = System.nanoTime();//ns 
		bData=oram.read(11); 
		

        testDoneTime = System.currentTimeMillis(); 
        testDoneTime1 = System.nanoTime();  
        totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        totalElapsedTime1 = (testDoneTime1 - testTime1);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
        System.out.println("totalElapsedTime1:"+totalElapsedTime1); 
		/******************
		 * Should be the byte 0x08
		 * *******************/
		System.out.println(bData[0]+"  "+bData[10]);
		
		oram.closeConnection();
		
	}

}
