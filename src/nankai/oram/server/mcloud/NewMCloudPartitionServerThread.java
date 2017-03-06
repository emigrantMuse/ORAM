package nankai.oram.server.mcloud;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.crypto.SecretKey;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.Util;

public class NewMCloudPartitionServerThread extends Thread {

	Socket socket = null; // 保存与本线程相关的Socket对象
	byte recvCmd[];
	byte recvMsg[];
	byte sendMsg[];
	NewMCloudORAMserver oram;

	public NewMCloudPartitionServerThread(Socket socket) {
		this.socket = socket;
		recvCmd = new byte[29];
		recvMsg = new byte[1024];
		sendMsg = new byte[1024];
		oram = NewMCloudORAMserver.getInstance();
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
					len = in.read(recvCmd, 1, 8);
					if (len != 8) {
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
					len = in.read(recvCmd, 1, 21);
					// content: session ID | unfilled - partition | other cloud for shuffling | length of ids
					if (len != 21) {
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
				case CommandType.directWriteCloud:
				{
					len = in.read(recvCmd, 1, 12);
					// content: session ID | unfilled - partition | other cloud for shuffling | length of ids
					if (len != 12) {
						System.out.println("directWriteCloud　error data!　" + len);
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
				case CommandType.noticeShuffle: {
					len = in.read(recvCmd, 1, 28);
					// content: session ID | unfilled - partition | other cloud for shuffling | length of ids
					if (len != 28) {
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
//		System.out.println("init oram");

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
	
	/**
	 * When receive this data,  the cloud would read data and send them to the other cloud
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private void shuffleData(DataOutputStream out, DataInputStream in)
			throws IOException {
		/**************************************** 
		 * (2)Send All the data, onionkeys in slots to the otherCloud 
		 * 
		 * CommandType: shuffleData
		 * Content: session ID | unfilled levels number | 
		 *          onion keys and slots data  
		 * ************************************/
		
		byte[] sessionID = new byte[8];

		System.arraycopy(recvCmd, 1, sessionID, 0, 8); 
		int idsLen = Util.byteToInt(recvCmd, 9, 4);  

		//System.out.println("4. shuffleData " + sessionID[0]);
		/**************************************************************
		 * Get the session Object, must be received, because client first send noticeShuffle, 
		 * and then the target cloud sends the shuffled data
		 *************************************************************/ 
		SessionManager manager=SessionManager.getInstance();
		SessionObject session = manager.getObject(sessionID);
		if (session==null)
		{
			//create it 
			System.out.println("Cannot find the session OBJECT!!!!!!!!!!!!!!!!!!!!!!!"+sessionID[0]+"  "+sessionID[5]);
			return;
			//session=new SessionObject(sessionID);
		}
		//verify the length
		if (idsLen != session.idsLen)
			System.out.println("wrong ID number!!!!!!!!!!!!!!!!!!!!!!! idsLen:"+idsLen+"  "+session.idsLen);

		//System.out.println("shuffleData: idsLen "+idsLen);
		
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
			/********************  do the onion decryption ********************************/ 
			session.setBlockDataWithOnionDecryption(filledLevelLength - idsLen + i, recBlcokData, session.levels[i]);
		}
		 
		/*****************************************
		 * Finally, shuffule them
		 *******************************************/
//		System.out.println("begin psuedo_random_permute ");
		session.psuedo_random_permute();
		//re-encrypt them and send backs
		session.onionEncryption();
		 
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
			out.write(session.shffule[i], 0, CommInfo.blockSize);
			out.flush(); 
		}
		
		//The end of the session
		manager.removeObject(session);
	}
	

	private void directWriteCloud(DataOutputStream out, DataInputStream in)
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
				oram.writeBlock(partition, beginID+i, recBlcokData, 0);
			}
			//System.out.println("i: "+i+ "  "+recBlcokData[0]); 
		}
		 
		//read the sfk 
		byte[] sfk=new byte[2];
		if (in.read(sfk, 0, 2) <= 0) {
			System.out.println("ERROR read key data!!!!!!!!!!!!!!!!!!!!!!!!");
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
	private void noticeShuffle(DataOutputStream out, DataInputStream in)
			throws IOException {
		/**************************************** 
		 * (1)Send All the data, onionkeys in slots to the otherCloud 
		 * 
		 * CommandType: shuffleData
		 * Content: session ID | unfilled levels number | 
		 *          onion keys and slots data  
		 * ************************************/
		
		//System.out.println("1. noticeShuffle");
		byte[] sessionID = new byte[8];

		System.arraycopy(recvCmd, 1, sessionID, 0, 8);
		int partition = Util.byteToInt(recvCmd, 9, 4);
		int unfilledLevel = Util.byteToInt(recvCmd, 13, 4); 
		int filledLevelLength = Util.byteToInt(recvCmd, 17, 4); 
		int idsLen = Util.byteToInt(recvCmd, 21, 4);  
		int evictSize = Util.byteToInt(recvCmd, 25, 4);
//		System.out.println(" partition "+ partition +" unfilledLevel "+ unfilledLevel+" filledLevelLength "+filledLevelLength+" idsLen "+idsLen );
		int rndLength = filledLevelLength - idsLen - 2*evictSize;
		if (rndLength<0)
			System.out.println(" rndLength "+ rndLength +" filledLevelLength "+filledLevelLength+" idsLen "+idsLen+" evictSize "+evictSize );
			
		
		if (filledLevelLength <=0 )
			;//System.out.println("ERROR!!!!!!!!!!!!!!!!!!!!!!!!");
		
		/*******************
		 * Has received the write operation command?
		 * If not, create the session object
		 * *********************/
		SessionManager manager=SessionManager.getInstance();
		SessionObject session = manager.getObject(sessionID);
		if (session==null)
		{
			//create it 
			session=new SessionObject(sessionID);
		}
		
		//receive the IDs
		byte[] levels = new byte[idsLen];
		if (idsLen>0){
			// read all the ids
//			if (in.read(levels, 0, idsLen) <= 0)
//				System.out.println("ERROR read IDs data!!!!!!!!!!!!!!!!!!!!!!!!");
			 
			readSocketLongData(in, idsLen, levels); 
			
			
		}else{
			;//System.out.println("ids Len is 0!!");
		}
		session.setLevels(levels, idsLen);
		
		
		//receive each data and buffer them
		session.createShuffle(filledLevelLength); 
		byte[] recBlcokData = new byte[CommInfo.blockSize];
		for (int i=0;i< 2*evictSize ;i++)
		{
			if (in.read(recBlcokData, 0, CommInfo.blockSize) <= 0) {
				System.out.println("ERROR read data!!!!!!!!!!!!!!!!!!!!!!!!");
			}
			//System.out.println("i: "+i+ "  "+recBlcokData[0]);
			session.setBlockData(i, recBlcokData);
		}
		
		/*******************************************************
		 * read the random value and generate the dummy data 
		 ******************************************************/
		byte[] rnds=new byte[rndLength*4];
		{


			readSocketLongData(in, rndLength*4, rnds);
			 
			for (int i=0;i<rndLength;i++)
			{
				int did=Util.byteToInt(rnds, i*4, 4);
				//add the dummy block into the buffer
				session.setBlockData(i+2*evictSize, this.generateXORBlock(did));
			}
			
		}
		
		//receive the onion keys
		session.createkey(unfilledLevel);
		byte[] bkeyData = new byte[CommInfo.keySize];
		for (int i=MCloudCommInfo.severBeginLevel; i<=unfilledLevel; i++)
		{
			if (in.read(bkeyData, 0, CommInfo.keySize) <= 0) {
				System.out.println("ERROR read key data!!!!!!!!!!!!!!!!!!!!!!!!");
			}
			session.setKey(i, bkeyData);
		}
		
		//read the sfk 
		byte[] sfk=new byte[16];
		if (in.read(sfk, 0, 16) <= 0) {
			System.out.println("ERROR read key data!!!!!!!!!!!!!!!!!!!!!!!!");
		}
		
		session.setSFKey(sfk);
		manager.addObject(session);
		
		out.writeByte(ResponseType.normal);
		out.flush(); 
		
	}
	

	public byte[] generateXORBlock(int dummyID)
	{
		//Encrypt the 0x00 default value by the userkey
		byte[] data=new byte[CommInfo.blockSize];
		for (int i=0;i<CommInfo.blockSize;i++)
		{
			data[i] = 0;
		}
		
//		SecretKey userKey;
//		/****************************
//		 * The first 4 bytes is the dummmyID and the random value, to make sure it is less than 0
//		 * 
//		 * But now, to simplify the implementation, we ignore the random value for the same 0000000
//		 * ******************************/
//		
//		byte[] iv = Util.generateIV(dummyID);
		
		//encrypt		
//		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
//		scp.initEnc(userKey, iv);
//		scp.enc_decData(data, CommInfo.blockSize); 
		return data;
	}
	
	/**
	 * When receive this data,  the cloud would read data and send them to the other cloud
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private void noticeWriteCloud(DataOutputStream out, DataInputStream in)
			throws IOException {
		/****************************************
		 * (1)Notice this cloud to send the data to the otherCloud for shuffling
		 * CommandType: writeCloud
		 * content: session ID | unfilled - partition | other cloud for shuffling | length of ids 
		 * 
		 *  When receive this data,  the cloud would read data and send them to the other cloud
		 * ************************************/
		
		//System.out.println("3. noticeWriteCloud "+recvCmd[1]);
		byte[] sessionID = new byte[8];

		System.arraycopy(recvCmd, 1, sessionID, 0, 8);
		int partition = Util.byteToInt(recvCmd, 9, 4);
		int unfilledLevel = Util.byteToInt(recvCmd, 13, 4);
		int cloud = recvCmd[17];
		int idsLen = Util.byteToInt(recvCmd, 18, 4); 
		  
		byte[] ids = null;  
		// 4 ID + 1 level 
		if (idsLen > 0) {

			ids = new byte[idsLen*4];

//		    if (in.read(ids, 0, idsLen*4)!=idsLen*4)
//		    	System.out.println("ids Error!!!");
		     
			readSocketLongData(in, idsLen*4, ids);
			
			  
			 
		}else{ 
			;//System.out.println("data length is 0!"); 
		} 

		
		/************************
		 * SEND COMMAND TO OTHER CLOUD
		 * ***************************/
		byte[] cmd=new byte[13];
		cmd[0]=CommandType.shuffleData;
		System.arraycopy(sessionID, 0, cmd, 1, 8);//session ID
		Util.intToByte(cmd, 9, idsLen);//idsLen 
		
		SocketClientUtil SCU=new SocketClientUtil(MCloudCommInfo.ip[cloud], MCloudCommInfo.port[cloud]);
		SCU.connect();

//		System.out.println("Ready for shuffle data command sending!"); 
//		for (int i=0;i<13;i++)
//			System.out.print(cmd[i]+" ");
		SCU.send(cmd, 13);
		byte[] bData = new byte[CommInfo.blockSize];
		for (int i = 0; i < idsLen; i++) {
			int _id = Util.byteToInt(ids, i * 4, 4);
//			System.out.println("read _id: "+_id+" partition: "+partition);
			//IDS 4 id | 1 level

//			if (_id == 0) {
//				System.out
//						.println("noticeWriteCloud :!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!idsLen!!!!!!"
//								+ idsLen);
//				for (int z = 0; z < idsLen * 4; z++)
//					System.out.print(ids[z] + " ");
// 
//				for (int z = 0; z < idsLen ; z++)
//				{
//					int _did = Util.byteToInt(ids, z * 4, 4);
//					System.out.print(_did + " ");
//				}
//			}
			
			oram.readBlock(partition, _id, bData);
			SCU.send(bData, CommInfo.blockSize); 
		}
		
		//Then, waiting for the response
		byte[] backCmd=new byte[5];
		if (SCU.receiving(backCmd, 5)!=5 || backCmd[0]!=CommandType.backData)
			System.out.println("wrong back command!");
		int length=Util.byteToInt(backCmd, 1, 4);
		int beginID=(1 << (unfilledLevel + 1)) - 2;
		for (int i=0;i<length; i++)
		{
			if (SCU.receiving(bData, CommInfo.blockSize)!=CommInfo.blockSize)
				System.out.println("wrong back data!");
			else{
				//System.out.println("write _id: "+(beginID+i)+" beginID: "+beginID);
				//store it in the database 
				oram.writeBlock(partition, beginID+i, bData, 0);
			}
		}
		
		SCU.disConnect();

		out.writeByte(ResponseType.normal);
		out.flush(); 
	}

	private void readSocketLongData(DataInputStream in, int dataLen, byte[] ids)
			throws IOException { 
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
			    	System.out.println("noticeWriteCloud 512 Error!!!"); 
			    hasRead += 512;
			}
			if (leftLen>0){
			    if (in.read(ids, hasRead, leftLen)!=leftLen)
			    	System.out.println("noticeWriteCloud leftLen Error!!!"); 
			}
		}
	}
	

	private void readCloud(DataOutputStream out, DataInputStream in)
			throws IOException {
		/*********
		 * P + ID
		 * *********/
//		System.out.println("readCloud");

		int p = Util.byteToInt(recvCmd, 1, 4);
		int len = Util.byteToInt(recvCmd, 5, 4); // the next length of all the
													// IDs
		//System.out.println("readCloud p _id :" + p + "  len:  " + len);

		byte[] bData = new byte[CommInfo.blockSize];
	 
		if (len>0){
			byte[] ids = new byte[len * 4];

			// read all the ids 
			while (in.read(ids, 0, len * 4) > 0) {
				break;
			}

			for (int i = 0; i < CommInfo.blockSize; i++)
				bData[i] = 0;

			byte[] bRnd = new byte[CommInfo.blockSize];
			for (int i = 0; i < len; i++) {
				int _id = Util.byteToInt(ids, i * 4, 4);
				if (_id == 0)
					System.out
							.println("readCloud :!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				oram.readBlock(p, _id, bRnd);
				for (int j = 0; j < CommInfo.blockSize; j++)
					bData[j] ^= bRnd[j];
			}
		}

		out.writeByte(ResponseType.normalWithValue);
		out.flush();
		out.write(bData, 0, CommInfo.blockSize); 
		out.flush();
	}


}
