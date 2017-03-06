package nankai.oram.client.Test;

import nankai.oram.client.NewMCOSORAMClient;
import nankai.oram.client.PartitionClient;
import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.Util;

public class TestNewMCOSFunction {

	public static void main(String[] args) {  
		
		NewMCOSORAMClient oram=new NewMCOSORAMClient();
		//initialize the client
		oram.init(65536);
		oram.openConnection();
		//initalize the server
		oram.initORAM();
		//write some data
		byte[] bData = new byte[CommInfo.blockSize];
		for (int id = 0; id < 5000; id++) {
			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = (byte) id;
			Util.intToByte(bData, 0, id);
			oram.write(id, bData);
		}
		System.out.println("-----ready to read the block !-----------");
		bData=oram.read(19); 
		
		/******************
		 * Should be the byte 0x08
		 * *******************/
		System.out.println(bData[0]+"  "+bData[10]);
		
		oram.closeConnection();
		
	}
}
