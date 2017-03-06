package nankai.oram.server.mcloud;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.Util;

public class MCloudPartitionServerThread extends Thread {

	Socket socket = null; // 保存与本线程相关的Socket对象
	byte recvCmd[];
	byte recvMsg[];
	byte sendMsg[];
	MCloudORAMserver oram;

	public MCloudPartitionServerThread(Socket socket) {
		this.socket = socket;
		recvCmd = new byte[37];
		recvMsg = new byte[1024];
		sendMsg = new byte[1024];
		oram = MCloudORAMserver.getInstance();
		//System.out.println(" new connection ......... ");
	}

	public void run() {
		DataInputStream in = null;
		DataOutputStream out = null;
		try {

			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());

			byte type = 0;
			int len = 0;

			while (true) {
				type = 0;
				len = 0;
				while ((len = in.read(recvCmd, 0, 1)) > 0) {
					type = recvCmd[0];
//					System.out
//							.println("-----------------------------------------------------------");
//					System.out.println("receive a command type:" + type + "  len  "
//							+ len);
					break;
				}
				
				//close the thread
				if (type == CommandType.closeThread)
					break;

				switch (type) {
				case CommandType.openDB:
				{
					in.read(recvCmd, 1, 1);
					oram.openDB(recvCmd[1]);
					out.writeByte(ResponseType.normal);
					out.flush(); 
					
					break;
				}
				case CommandType.initORAM: {
					len = in.read(recvCmd, 1, 13);
					if (len != 13) {
						System.out.println("initORAM　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else
						initORAM(out);
					break;
				} 
				case CommandType.readCloud: { 
					len = in.read(recvCmd, 1, 24);
					if (len != 24) {
						System.out.println("readCloud　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						/**************************************
						 * partition+_id
						 * **********************************/
						readCloud(out, in);
					}
					break;
				}
				case CommandType.noticeWriteCloud: { 
					len = in.read(recvCmd, 1, 24);
					// content: session ID | unfilled - partition | other cloud for shuffling | length of ids
					if (len != 24) {
						System.out.println("noticeWriteCloud　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						/**************************************
						 * partition+_id
						 * **********************************/
						noticeWriteCloud(out, in);
					}
					
					break; 
				}
				case CommandType.noticeShuffle: { 
					len = in.read(recvCmd, 1, 36);
					// content: session ID | unfilled - partition | other cloud for shuffling | length of ids
					if (len != 36) {
						System.out.println("noticeWriteCloud　error data!　" + len);
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						/**************************************
						 * partition+_id
						 * **********************************/
						noticeShuffle(out, in);
					}
					
					break; 
				} 
				case CommandType.directWriteCloud:{ 
					len = in.read(recvCmd, 1, 12);
					if (len != 12) {
						System.out.println("directWriteCloud　error data!　" + len);
						for (int i=0;i<len;i++)
							System.out.print(recvCmd[i]+" ");
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						/**************************************
						 * partition+_id
						 * **********************************/
						directWriteCloud(out, in);    
					}
					
					break; 
				} 
				case CommandType.shuffleData:{ 
					len = in.read(recvCmd, 1, 12);
					if (len != 12) {
						System.out.println("shuffleData　error data!　" + len);
						for (int i=0;i<len;i++)
							System.out.print(recvCmd[i]+" ");
						out.writeByte(ResponseType.wrong);
						out.flush();
					} else {
						/**************************************
						 * partition+_id
						 * **********************************/
						shuffleData(out, in);    
					}
					
					break; 
				} 
				}

			}

		} catch (Exception e) {
			System.out.println("Error.?>>>>> " + e);
			e.printStackTrace();
		} finally {
			try {
				out.close(); // 关闭Socket输出流
				in.close();// 关闭Socket输入流
				socket.close(); // 关闭Socket
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		//System.out.println("closing............................ ");
	}

	private void initORAM(DataOutputStream out) throws IOException {
		/*********
		 * CMD 9, CMDTYPE | PARTITIONS | CAPACITY
		 * *********/
		//System.out.println("init oram");

		int paritions = Util.byteToInt(recvCmd, 1, 4);
		int capacity = Util.byteToInt(recvCmd, 5, 4);
		int levels = Util.byteToInt(recvCmd, 9, 4);
		byte cloud = recvCmd[13];
//		System.out.println("init paritions capacity:" + paritions + "  "
//				+ capacity);
		oram.init(paritions, capacity, levels, cloud);

//		System.out.println("init OK.");
		out.writeByte(ResponseType.normal);
		out.flush();
	}
	

	private synchronized void shuffleData(DataOutputStream out, DataInputStream in )
			throws IOException {
		
		byte[] sessionID = new byte[8];

		System.arraycopy(recvCmd, 1, sessionID, 0, 8); 
		int idsLen = Util.byteToInt(recvCmd, 9, 4);  
		
		/**************************************************************
		 * Get the session Object, must be received, because client first send noticeShuffle, 
		 * and then the target cloud sends the shuffled data
		 *************************************************************/ 
		SessionManager manager=SessionManager.getInstance();
		//System.out.print("3. ->shuffleData  ");
		SessionObject session = manager.getObject(sessionID);
		if (session==null)
		{
			//create it 
			System.out.println("Cannot find the session OBJECT!!!!!!!!!!!!!!!!!!!!!!!");
			return;
			//session=new SessionObject(sessionID);
		}

		
		int filledLevelLength = session.filledLevelLength;
		/***********************************************
		 * Receive the block data from other cloud 
		 * 
		 * Firstly, onion decrypt them
		 *******************************************/
		byte[] recBlcokData = new byte[CommInfo.blockSize];
		for (int i=0;i<idsLen; i++)
		{ 
			if (in.read(recBlcokData, 0, CommInfo.blockSize) <= 0) {
				System.out.println("shuffleData: ERROR id block data!!!!!!!!!!!!!!!!!!!!!!!!");
			}
			
			//System.out.println("i: "+i+"  "+recBlcokData[8]);
			session.setBlockData(recBlcokData); 
		}
		 
		/*****************************************
		 * Finally, shuffule them
		 *******************************************/
//		System.out.println("begin psuedo_random_permute ");
		//session.psuedo_random_permute();

		 
		/***********************
		 * return back the data 
		 * ********************************/ 
		byte[] backCmd=new byte[5];
		backCmd[0]=CommandType.backData;
		Util.intToByte(backCmd, 1, session.filledLevelLength);
		
		out.write(backCmd, 0, 5);
		out.flush(); 
		for (int i=0;i<session.filledLevelLength;i++)
		{
			//System.out.println("after shuffle i: "+i+"  "+session.shffule[i][8]);
			out.write(session.shffule[i], 0, CommInfo.blockSize);
			out.flush(); 
		}
		
		//The end of the session
		manager.removeObject(session);
		 
	}
	
	private synchronized void directWriteCloud(DataOutputStream out, DataInputStream in )
			throws IOException {
		/**************************************** 
		 * (1)Send All the data, onionkeys in slots to the otherCloud 
		 * 
		 * CommandType: shuffleData
		 * Content: session ID | unfilled levels number | 
		 *          onion keys and slots data  
		 * ************************************/
		
		//System.out.println("directWriteCloud"); 
 
		int partition = Util.byteToInt(recvCmd, 1, 4);
		int unfilledLevel = Util.byteToInt(recvCmd, 5, 4); 
		int filledLevelLength = Util.byteToInt(recvCmd, 9, 4);   ;
		  
		/*******************
		 * Has received the write operation command?
		 * If not, create the session object
		 * *********************/  
		byte[] recBlcokData = new byte[CommInfo.blockSize];
		int beginID=(1 << (unfilledLevel + 1)) - 2;
		for (int i=0;i<filledLevelLength;i++)
		{
			if (in.read(recBlcokData, 0, CommInfo.blockSize) <= 0) {
				System.out.println("ERROR read data!!!!!!!!!!!!!!!!!!!!!!!!");
			}else{ 
				//System.out.println("writeBlock " + recBlcokData[2]); 
				oram.writeBlock(partition, beginID+i, recBlcokData, 0);
			}
			//System.out.println("i: "+i+ "  "+recBlcokData[0]); 
		} 
	 
		out.writeByte(ResponseType.normal);
		out.flush(); 
		 
	}
	
	/**
	 * When receive this data,  the cloud would read data and send them to the other cloud
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private synchronized void readCloud(DataOutputStream out, DataInputStream in )
			throws IOException {


		byte[] bretData = new byte[CommInfo.blockSize]; 
		
		int partition = Util.byteToInt(recvCmd, 9, 4); 
		int targetPos = Util.byteToInt(recvCmd, 13, 4); 
		int length= Util.byteToInt(recvCmd, 17, 4); 
		int shuffleCloud= Util.byteToInt(recvCmd, 21, 4);   
		
		//read all the ids and then send them 
		byte[] ids = new byte[length * 4];
		if (length > 0) { 
			if (in.read(ids, 0, length * 4) != length * 4) {
				System.out.println("error 1");
			}  
		} 
		

		//System.out.println("readcloud - shuffleCloud:"+shuffleCloud+" session "+recvCmd[1]);   

		SocketClientUtil SCU=new SocketClientUtil(MCloudCommInfo.ip[shuffleCloud], MCloudCommInfo.port[shuffleCloud]);
		SCU.connect();
		byte[] cmd=new byte[13];
		cmd[0]=CommandType.shuffleData; 
		System.arraycopy(recvCmd, 1, cmd, 1, 8);  
		Util.intToByte(cmd, 9, length);//   

		SCU.send(cmd, 13);
		
		byte[] bData = new byte[CommInfo.blockSize];
		
		if (length > 0) { 
			for (int i = 0; i < length; i++) {
				int _id = Util.byteToInt(ids, i * 4, 4);

				oram.readBlock(partition, _id, bData);
				//System.out.println("_id: "+_id+"  i: "+i+"  "+bData[8]);
				SCU.send(bData, CommInfo.blockSize);
			}
		} 

		//Then, waiting for the response
		byte[] backCmd=new byte[5];
		if (SCU.receiving(backCmd, 5)!=5 || backCmd[0]!=CommandType.backData)
			System.out.println("wrong back command!");
		int len=Util.byteToInt(backCmd, 1, 4); 
		for (int i=0; i<len; i++)
		{
			//receive the data block and add it to the shuffle
			if (SCU.receiving(bData, CommInfo.blockSize)!=CommInfo.blockSize){
				System.out.println("wrong back data!"); 
			}

			if (targetPos==i){
				System.arraycopy(bData, 0, bretData, 0, CommInfo.blockSize); 
			}		
		}

		SCU.disConnect();

		out.writeByte(ResponseType.normalWithValue);
		out.flush();
		out.write(bretData, 0, CommInfo.blockSize); 
		out.flush();  
		
	}
	
	/**
	 * When receive this data,  the cloud would read data and send them to the other cloud
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private synchronized void noticeShuffle(DataOutputStream out, DataInputStream in )
			throws IOException {
		/**************************************** 
		 * (1)Send All the data, onionkeys in slots to the otherCloud 
		 * 
		 * CommandType: shuffleData
		 * Content: session ID | unfilled levels number | 
		 *          onion keys and slots data  
		 * ************************************/
		
		//System.out.println("noticeShuffle");
		byte[] sessionID = new byte[8];
		byte[] sfk = new byte[16];

		System.arraycopy(recvCmd, 1, sessionID, 0, 8);
		System.arraycopy(recvCmd, 9, sfk, 0, 16); 
 
		
		int partition = Util.byteToInt(recvCmd, 25, 4);
		int idsLen = Util.byteToInt(recvCmd, 29, 4); 
		int levels = Util.byteToInt(recvCmd, 33, 4); 
 		
		/*******************
		 * Has received the write operation command?
		 * If not, create the session object
		 * *********************/
		SessionManager manager=SessionManager.getInstance();
		//System.out.print("1. ->noticeShuffle  ");
		SessionObject session = new SessionObject(sessionID); 
		session.setSFKey(sfk);
		
		//receive the IDs
		byte[] ids = new byte[idsLen*4];
		if (idsLen>0){
			// read all the ids
			if (in.read(ids, 0, idsLen*4) <= 0)
				System.out.println("ERROR read IDs data!!!!!!!!!!!!!!!!!!!!!!!!");
		}else{
			;//System.out.println("ids Len is 0!!");
		}
		 
		//read the block and set to buffer
		session.createShuffle(levels); 
		byte[] bData = new byte[CommInfo.blockSize]; 
		for (int i=0;i<idsLen;i++)
		{
			//Read form the dabase to get the target block
			int _id = Util.byteToInt(ids, i * 4, 4); 
 
			oram.readBlock(partition, _id, bData);
			//System.out.println("noticeShuffle i: "+i+ "  "+bData[8]);
			session.setBlockData(i, bData);
		}
		
		manager.addObject(session);
		

		if (in.read(recvCmd,0,2)!=2)
			System.out.println("error 2");
		
		out.writeByte(ResponseType.normal);
		out.flush(); 
		
	}
	/**
	 * When receive this data,  the cloud would read data and send them to the other cloud
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private synchronized void noticeWriteCloud(DataOutputStream out, DataInputStream in )
			throws IOException {
		/****************************************
		 * (1)Notice this cloud to send the data to the otherCloud for shuffling
		 * CommandType: writeCloud
		 * content: session ID | unfilled - partition | other cloud for shuffling | length of ids 
		 * 
		 *  When receive this data,  the cloud would read data and send them to the other cloud
		 * ************************************/
		 
 
		int partition = Util.byteToInt(recvCmd, 1, 4); 
		int cloud = Util.byteToInt(recvCmd, 5, 4); 
		int unfilledLevel= Util.byteToInt(recvCmd, 9, 4); 
		int filledLevelLength= Util.byteToInt(recvCmd, 13, 4); 
		int unreadDataNumber= Util.byteToInt(recvCmd, 17, 4);  
		int otherCloud= Util.byteToInt(recvCmd, 21, 4);  
		  
		byte[][] s_buffer=new byte[filledLevelLength][CommInfo.blockSize];
 
		byte[] bData = new byte[CommInfo.blockSize];
		int nowPos = 0;
		if (in.read(bData, 0, CommInfo.blockSize)!=CommInfo.blockSize)
		{
			System.out.println("error 3");
		}else{
			System.arraycopy(bData, 0, s_buffer[nowPos++], 0, CommInfo.blockSize);
		}
		
		byte[] ids=new byte[unreadDataNumber*4 ];
		//
		if (unreadDataNumber > 0) { 
//			if (in.read(ids, 0, unreadDataNumber * 4) != unreadDataNumber * 4) {
//				System.out.println("error 4");
//			} 
			
			int dataLen = unreadDataNumber*4; 
			if (dataLen<512){
			    if (in.read(ids, 0, dataLen)!=dataLen)
			    	System.out.println("ids Error!!!");
			}else{ 
				int num = dataLen / 512  ;
				int leftLen = dataLen % 512;
				int hasRead = 0;
				for (int i=0;i<num;i++)
				{ 

				    if (in.read(ids, hasRead, 512)!=512)
				    	System.out.println("noticeShuffle 512 Error!!!"); 
				    hasRead += 512;
				}
				if (leftLen>0){
				    if (in.read(ids, hasRead, leftLen)!=leftLen)
				    	System.out.println("noticeShuffle leftLen Error!!!"); 
				}
			}
			
			
			
			for (int i = 0; i < unreadDataNumber; i++) {
				int _id = Util.byteToInt(ids, i * 4, 4);

				oram.readBlock(partition, _id, bData);
				System.arraycopy(bData, 0, s_buffer[nowPos++], 0,
						CommInfo.blockSize);
			}
		}
  
		
		//generate dummy block
		for (int i=nowPos; i<filledLevelLength;i++)
		{
			byte[] bDummy=Util.generateDummyData(CommInfo.blockSize);
			System.arraycopy(bDummy, 0, s_buffer[nowPos++], 0, CommInfo.blockSize);
		}
		

		byte[] sfk=new byte[16];
		if (in.read(sfk, 0, 16) <= 0) {
			System.out.println("ERROR read key data!!!!!!!!!!!!!!!!!!!!!!!!");
		}
		

		if (in.read(recvCmd,0,2)!=2)
			System.out.println("error 2");
		
		//send to other cloud and storage
		
		psuedo_random_permute(sfk, s_buffer, filledLevelLength);
		
		if (otherCloud != cloud){
			// Then send to another cloud to write
			SocketClientUtil SCU = new SocketClientUtil(
					MCloudCommInfo.ip[otherCloud],
					MCloudCommInfo.port[otherCloud]);
			SCU.connect();
			byte[] cmd = new byte[13];
			cmd[0] = CommandType.directWriteCloud;
			Util.intToByte(cmd, 1, partition);//
			Util.intToByte(cmd, 5, unfilledLevel);//
			Util.intToByte(cmd, 9, filledLevelLength);//

			SCU.send(cmd, 13);
			for (int i = 0; i < filledLevelLength; i++) {
				SCU.send(s_buffer[i], CommInfo.blockSize);
			}

			//System.out.println("direct write cloud ok!");
			// Then, waiting for the response
			byte[] backCmd = new byte[1];
			if (SCU.receiving(backCmd, 1) != 1
					|| backCmd[0] != ResponseType.normal)
				System.out.println("wrong back command!");

			SCU.disConnect();
		}else{
			//If it is not the other cloud, will be written here
			int beginID=(1 << (unfilledLevel + 1)) - 2;			
			for (int i = 0; i < filledLevelLength; i++) {
				oram.writeBlock(partition, beginID+i, s_buffer[i], 0);
			}
		}
		 

		out.writeByte(ResponseType.normal);
		out.flush(); 
	}
	
	public void psuedo_random_permute(byte[] sfk1, byte[][] shffule, int filledLevelLength) { 
		byte[] bData = new byte[CommInfo.blockSize];
		
        SecretKey  sfk = new SecretKeySpec(sfk1, "AES");
//		for (int i = 0; i < filledLevelLength; i++) {
//			System.out.println("i: "+i+"  "+shffule[i][0]); 
//		}
		
		for (int i = 0; i <filledLevelLength; i++) { 
			
			int j = Util.fpeForPermution(i, sfk, filledLevelLength); 
			System.arraycopy(shffule[i], 0, bData, 0, CommInfo.blockSize);
			System.arraycopy(shffule[j], 0, shffule[i], 0, CommInfo.blockSize);
			System.arraycopy(bData, 0, shffule[j], 0, CommInfo.blockSize);

//			System.out.println("permute: "+i+"  "+j);
		}
	} 
 
}
