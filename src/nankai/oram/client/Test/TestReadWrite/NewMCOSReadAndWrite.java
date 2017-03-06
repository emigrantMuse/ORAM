package nankai.oram.client.Test.TestReadWrite;

import java.util.Random;

import nankai.oram.client.NewMCOSORAMClient;
import nankai.oram.client.PartitionClient;
import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.Util;

public class NewMCOSReadAndWrite {
	
	public static int N = 102400;

	public static void main(String[] args) {  


		//testRead();
		
		
		//testMultiWrite();

		//testSameAccess();
		testRandomAccess();
	}

	private static void testMultiWrite() {
		//testWrite(5);
		testWrite(10);
		testWrite(25);
		testWrite(50);
		testWrite(75);
		testWrite(100);
		testWrite(150);
		testWrite(1000); 
	}

	private static void testRead() {
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();
		
		writeTheBlocksToApartition(oram, 1000);
		 
		
		readABlock(oram, 10);
		readABlock(oram, 50);
		readABlock(oram, 100);
		readABlock(oram, 150);
//		readABlock(oram, 40);
//		readABlock(oram, 50);
//		readABlock(oram, 60);
//		readABlock(oram, 70);
//		readABlock(oram, 80);
//		readABlock(oram, 90);
		//readABlock(oram, 70); 
		//readABlock(oram, 100); 
		//readABlock(oram, 150); 
		readABlock(oram, 250); 
		readABlock(oram, 500); 
		
		oram.closeConnection();
	}

	/************************
	 * After the initOram 
	 * This test can be performed
	 * 
	 * This test is used to test the execution time of random access, the number of shuffle between clouds and the write operation
	 **********************/
	private static void testRandomAccess() {
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();

		Util.debug = false; 
		

		writeTheBlocks(oram, N);
  
  
		accessWithRandomStatus(oram, 500);  
		accessWithRandomStatus(oram, 1000);  
		accessWithRandomStatus(oram, 5000);   
		accessWithRandomStatus(oram, 10000);   
		accessWithRandomStatus(oram, 50000);     
		accessWithRandomStatus(oram, 100000);    
//		
		
		oram.closeConnection();
	}
	private static void testSameAccess() {
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();

		Util.debug = false; 
		
		writeTheBlocks(oram, 10000);
  

//		//accessWithSameStatus(oram, 5); 
//		accessWithSameStatus(oram, 10); 
//		//accessWithSameStatus(oram, 25); 
//		accessWithSameStatus(oram, 50); 
//		//accessWithSameStatus(oram, 75); 
		accessWithSameStatus(oram, 100); 
//		//accessWithSameStatus(oram, 150);  
//		accessWithSameStatus(oram, 200); 
		
		accessWithSameStatus(oram, 500); 
		//accessWithSameStatus(oram, 500); 
		accessWithSameStatus(oram, 1000); 
		//accessWithSameStatus(oram, 2500); 
		accessWithSameStatus(oram, 5000);  
//		accessWithSameStatus(oram, 7500); 
		accessWithSameStatus(oram, 10000); 
		
//		writeTheBlocks(oram, 10000);
//		   
//		accessWithSameStatus(oram, 5000); 
//		
		
		oram.closeConnection();
	}

	private static void writeTheBlocks(NewMCOSORAMClient oram, int number) {
		//write some data 
        
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (i % 100);
			//Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		} 
		
		bData = oram.read( 2 );
		System.out.println(" read 2:"+bData[2]);
	}
	

	private static void accessWithRandomStatus(NewMCOSORAMClient oram, int number)
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
		Random rnd=new Random();
		for (int id = 0; id < number ; id++) { 
			//System.out.println("read "+id);
			int _id= rnd.nextInt(N);
			oram.read(_id);
			_id= rnd.nextInt(N);
			oram.read(_id);
			_id= rnd.nextInt(N);
			//write to the cloud
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) ((_id) % 100);
			//System.out.println("write "+ (id+1) );
			oram.write(_id, bData); 
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
        System.out.println("-----bandwidth  ----  "+  Util.bandwidth  );
        System.out.println("-----cloudcloudbandwidth  ----  "+Util.cloudcloudbandwidth );
        System.out.println("-----readbandwidth  ----  "+Util.readbandwidth ); 
        
        
		

		bData = oram.read( 2 );
		System.out.println(" read 2:"+bData[2]);
		bData = oram.read( 8 );
		System.out.println(" read 8:"+bData[2]);
		 
	}
	
	private static void accessWithSameStatus(NewMCOSORAMClient oram, int number)
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
		for (int id = 0; id < number -10 ; id++) { 
			//System.out.println("read "+id);
			oram.read(id);
			//write to the cloud
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) ((id+1) % 100);
			//System.out.println("write "+ (id+1) );
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
        System.out.println("-----bandwidth  ----  "+  Util.bandwidth  );
        System.out.println("-----cloudcloudbandwidth  ----  "+Util.cloudcloudbandwidth );
        System.out.println("-----readbandwidth  ----  "+Util.readbandwidth ); 
        
        
		

		bData = oram.read( 2 );
		System.out.println(" read 2:"+bData[2]);
		bData = oram.read( 8 );
		System.out.println(" read 8:"+bData[2]);
		 
	}

	private static void writeTheBlocksToApartition(NewMCOSORAMClient oram, int number) { 
		 
		Util.debug = true; //debug is ture, to write into a fix partiton
		
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

		byte[] bdata = oram.read(2);
		System.out.println(" data :"+bdata[8]);
	}
	private static void readABlock(NewMCOSORAMClient oram, int number) { 
		 
		Util.debug = true; 
		
		Util.writeNumber = 0;
		
		long testTime = -1; 
    	long testDoneTime = -1; 

        testTime = System.currentTimeMillis(); //ms   

        for (int i=0; i<number;i++)
        {
            oram.read(oram.getIDinDB() );
        }

        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0; 
        System.out.println("number is:"+number);
        System.out.println("totalElapsedTime:"+totalElapsedTime);   
		System.out.println("-----Shuffle numeber---- -"+Util.writeNumber+"  cache size:"+oram.getCacheSlotSize()); 
		  
		byte[] bdata = oram.read(2);
		System.out.println(" data :"+bdata[8]);
	}
	
	
	private static void access(int number)
	{
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();
		

		long testTime = -1; 
    	long testDoneTime = -1; 
        testTime = System.currentTimeMillis(); //ms  
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < number; id++) { 
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (i % 128);
			oram.read(id);
			oram.write(id+1, bData);
		}
        testDoneTime = System.currentTimeMillis();  
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
		System.out.println("-----Shuffle numeber---- -"+Util.writeNumber+"  cache size:"+oram.getCacheSlotSize()); 
		
		oram.closeConnection(); 
	}

	/**
	 * Test the special write to a fix partition 
	 * 
	 * if Util.debug set true, fix partition
	 * For a fix partition, test the number less than 1100
	 * but if false, random partition
	 */
	private static void testWrite(int number) {
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(N);
		oram.openConnection();
		//initalize the server
		oram.openORAM();
		//write some data
		
		Util.debug = true;
		Util.writeNumber = 0;
		Util.readbandwidth =0;
		Util.cloudcloudbandwidth =0;
		Util.readNumber =0;
		Util.bandwidth =0;
		Util.cloudtocloud=0;

		byte[] bData = new byte[CommInfo.blockSize];
		
        System.out.println("number is:"+number);
		long testTime = -1; 
		long testTime1 = -1; 
    	long testDoneTime = -1; 
    	long testDoneTime1 = -1; 
        testTime = System.currentTimeMillis(); //ms  
        testTime1 = System.nanoTime();//ns 
        
		for (int id = 0; id < number; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) (id % 100); 
			oram.write(id, bData);
		}

        testDoneTime = System.currentTimeMillis();  
        testDoneTime1 = System.nanoTime(); 
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        
        double totalElapsedTime1 = (testDoneTime1 - testTime1);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
//        System.out.println("totalElapsedTime222:"+totalElapsedTime1); 
        
        System.out.println("-----cloudtocloud  ----  "+Util.cloudtocloud );
        System.out.println("-----writeNumber  ----  "+Util.writeNumber );
        System.out.println("-----bandwidth  ----  "+Util.bandwidth );
        System.out.println("-----cloudcloudbandwidth  ----  "+Util.cloudcloudbandwidth );
        System.out.println("-----readNumber  ----  "+Util.readNumber );
        System.out.println("-----readbandwidth  ----  "+Util.readbandwidth ); 
        
		byte[] data = oram.read(8);
		System.out.println("-----read 8--------- : " + data[8]); 
		data = oram.read(9);
		System.out.println("-----read 9--------- : " + data[8]); 
		
		oram.closeConnection();
	}
	 
}
