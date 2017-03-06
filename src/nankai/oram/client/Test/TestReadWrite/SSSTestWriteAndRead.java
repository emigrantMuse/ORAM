package nankai.oram.client.Test.TestReadWrite;

import nankai.oram.client.NewMCOSORAMClient;
import nankai.oram.client.PartitionClient;
import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.Util;

public class SSSTestWriteAndRead {

	public static int N = 1024000;
	public static void main(String[] args) {  

		//writeTheBlocksToApartition(1024);
		testRandomAccess();
		
	}
	private static void testRandomAccess() {
		PartitionClient oram=new PartitionClient();

 
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();

		Util.debug = false; 
		
		writeTheBlocks(oram, 50000);
  
		accessWithSameStatus(oram, 100); 
		accessWithSameStatus(oram, 500); 
		accessWithSameStatus(oram, 1000); 
		accessWithSameStatus(oram, 2500); 
		accessWithSameStatus(oram, 5000); 
		accessWithSameStatus(oram, 7500); 
		accessWithSameStatus(oram, 10000); 
		
		
//		writeTheBlocks(oram, 10000);
//		   
//		accessWithSameStatus(oram, 5000); 
		
		
		oram.closeConnection();
	}

	private static void writeTheBlocks(PartitionClient oram, int number) {
		//write some data 
		Util.debug = false; 
        
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) 2;
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		} 
	}
	private static void accessWithSameStatus(PartitionClient oram, int number)
	{ 
		

		oram.clearSlot();
		

		Util.writeNumber = 0;
		Util.readbandwidth =0;
		Util.cloudcloudbandwidth =0;
		Util.readNumber =0;
		Util.bandwidth =0;
		Util.cloudtocloud=0;
		
		long testTime = -1; 
    	long testDoneTime = -1; 
        testTime = System.currentTimeMillis(); //ms  
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number -1 ; id++) { 
			//System.out.println(id);
			oram.read(id);
			//write to the cloud
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) 2;
			Util.intToByte(bData, 0, id+1);
			oram.write(id+1, bData); 
		}
        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        System.out.println("number is:"+number);
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
//		System.out.println("-----Shuffle numeber-----!"+Util.writeNumber+"  Util.cloudtocloud:"+ Util.cloudtocloud); 
//		System.out.println("-----ORAM CACHE-----!"+ oram.getCacheSlotSize()); 
        System.out.println("-----writeNumber  ----  "+Util.writeNumber );
        System.out.println("-----cloudtocloud  ----  "+Util.cloudtocloud );
        System.out.println("-----readNumber  ----  "+Util.readNumber );
        System.out.println("-----bandwidth  ----  "+ (Util.bandwidth) );
        System.out.println("-----cloudcloudbandwidth  ----  "+Util.cloudcloudbandwidth );
        System.out.println("-----readbandwidth  ----  "+Util.readbandwidth ); 
        
        
		

		bData = oram.read( 2 );
		System.out.println(" read 2:"+bData[8]);
		bData = oram.read( 8 );
		System.out.println(" read 8:"+bData[8]);
	}

	/**
	 * Test writing to a fix partition, until it is filled
	 * Obtain the number of write and cache size
	 * @param number
	 */
	private static void testWriteToAFixPartition(int number) {
		PartitionClient oram=new PartitionClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();
		//write some data
		 
		Util.debug = true; 
		Util.writeNumber = 0;
		long testTime = -1; 
    	long testDoneTime = -1; 

        testTime = System.currentTimeMillis(); //ms   
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (i % 128);
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}
        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0; 
        System.out.println("totalElapsedTime:"+totalElapsedTime);   
		System.out.println("-----Shuffle numeber---- -"+Util.writeNumber+"  cache size:"+oram.getCacheSlotSize()); 
		 
 
		oram.closeConnection();
	}
	private static void writeTheBlocks(int number) {
		PartitionClient oram=new PartitionClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();
		//write some data
		 
		Util.debug = false; 
        
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (i % 128);
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}
 
		oram.closeConnection();
	}
	
	private static void access(int number)
	{
		PartitionClient oram=new PartitionClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();

		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) { 
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (i % 128);
			oram.write(id, bData);
			oram.write( id+1, bData );
		}
		oram.closeConnection(); 
	}

	/**
	 * Test the special write to a fix partition and a read
	 * At the same time, to obtain the shuffle number
	 */
	private static void TestCase1() {
		long testTime = -1; 
    	long testDoneTime = -1; 
		
		
		PartitionClient oram=new PartitionClient();
		

		//initialize the client
		oram.init(N);
		oram.openConnection();
		

		//initalize the server
		oram.openORAM();

		Util.debug = true;
		Util.writeNumber = 0;

        testTime = System.currentTimeMillis(); //ms   
		//write some data
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < 256; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) id;
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}

        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0; 
        System.out.println("totalElapsedTime:"+totalElapsedTime);   
        
		System.out.println("-----ready to read the block !-----------");

        testTime = System.currentTimeMillis(); //ms   
		bData=oram.read(11); 
		

        testDoneTime = System.currentTimeMillis();  
        totalElapsedTime = (testDoneTime - testTime);// / 1000.0; 
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
		/******************
		 * Should be the byte 0x08
		 * *******************/
		System.out.println(bData[0]+"  "+bData[10]);
		
		oram.closeConnection();
	}
	/**
	 * Test the buffer size and shuffler number
	 */
	private static void TestCase2() { 
		
		
		PartitionClient oram=new PartitionClient();
		

		//initialize the client
		oram.init(N);
		oram.openConnection();
		

		//initalize the server
		oram.openORAM();

		Util.debug = false;
		Util.writeNumber = 0;
 
		//write some data
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < 51200; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) id;
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}
		
		//get the slot size
		System.out.println("-----Shuffle numeber---- -"+Util.writeNumber+"  cache size:"+oram.getCacheSlotSize()); 
 
		System.out.println("-----ready to read the block !-----------");
 
		bData=oram.read(111);  
 
		/******************
		 * Should be the byte 0x08
		 * *******************/
		System.out.println(bData[0]+"  "+bData[10]);
		
		oram.closeConnection();
	}
}
