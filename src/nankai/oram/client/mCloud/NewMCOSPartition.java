package nankai.oram.client.mCloud;

/*************
 * The data in a level of a partition is encrypted by the key for level
 * 
 * Each data is included by BID+DATA 
 * BID = REALBLOCK? BLOCKID : - OFFSET
 */

import java.security.NoSuchAlgorithmException;
import java.util.Queue;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import nankai.oram.client.common.Position;
import nankai.oram.client.common.SlotObject;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;

public class NewMCOSPartition{
	public static int part=0;
	int n_levels;
	 
	int n_realBlocks_p;//the real number of blocks in a partition 
	int n_capacity;//the max capacity of a partition -- need the top level
	int top_level_len;//the number of blocks in the top level
	
	int partition = 0;
	int blockIDs[];//all the blockIDs. When re-shuffle, the dummyIDs will be re-filled
	int dummyNumbers[];//the dummy block numbers;  When re-shuffle, the dummyIDs will be filled
	int nextDummy[];//the dummy block counter; 
	boolean filled[];//filled level flag
	boolean readFlag[];//read flag
	SecretKey keys[];//level key for each partition
	
	byte s_buffer[][];
 
	Position pos_map[];//position map
	SocketClientUtil cli;

	SubCloudNewORAM subOram;

	KeyGenerator kg;
	
	public int realDataNumber = 0;
	
	public NewMCOSPartition(int realBlocks_p, int levels, Position posMap[], SocketClientUtil cLi, SubCloudNewORAM soram,byte sBuffer[][])
	{  
		subOram=soram;
		pos_map=posMap;
		cli=cLi; 
		s_buffer = sBuffer;
		n_realBlocks_p =   realBlocks_p; 
	    
	    n_levels = levels;
	    n_capacity = (int) Math.ceil(CommInfo.capacity_parameter * n_realBlocks_p);
	    top_level_len = n_capacity - (1 << n_levels) + 2;
	     
		readFlag=new boolean[n_capacity]; 
		keys=new SecretKey[n_levels];
		blockIDs=new int[n_capacity];
		nextDummy=new int[n_levels];
		dummyNumbers=new int[n_levels];
		filled=new boolean[n_levels];

		for (int i=0;i<n_levels;i++)
		{
			dummyNumbers[i]=0;
			nextDummy[i]=0;
			filled[i]=false;
		}
		for (int i=0;i<n_capacity;i++)
		{ 
			readFlag[i]=true;
			blockIDs[i]=CommInfo.dummyID;
		}
		
		initKeys();
	}
	
	
	private void initKeys()
	{ 
		try {
			kg = KeyGenerator.getInstance("AES");
			kg.init(128);
			for (int j = 0; j < n_levels; j++)
				keys[j] = kg.generateKey();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	} 
	
	public byte[] getKey(int pos)
	{
		return keys[pos].getEncoded();
	}

	public byte[] readPartition(int block_id, SecretKey userKey) {
		
		/*******************************
		 * Gnerate the OFFSETs
		 * 
		 * Length + ID1 ...... + ID2......
		 * *****************************/
		byte[] data = new byte[CommInfo.blockSize]; 
		byte[] xorZero = new byte[CommInfo.blockSize]; 
		byte[] ids = new byte[ n_levels * 4 ];  
		int[] levels = new int[n_levels];
		int level = -1;
		if (block_id!=CommInfo.dummyID)
			level = pos_map[block_id].level;
		int length = 0;
		int realDataID = -1;
		for (int i=MCloudCommInfo.severBeginLevel;i<n_levels;i++)
		{
			if (filled[i])
			{ 
			    int _id =  (1 << (i + 1)) - 2; 
				
				if (level==i && block_id!=CommInfo.dummyID)
				{
					_id += pos_map[block_id].offset;
					realDataID = _id;
				}else{
					// not in this level, to read a dummy block, get the dummy offset
					int dID=nextDummy(0, i);
					if (dID == -1) // There is no dummy block can be read
						continue;
					_id += dID;//nextDummy(i); 
				}
				readFlag[_id]=true;
				Util.intToByte(ids, length*4, _id); 
				levels[length] = i;
				length ++; 
			}
		}
		if (length==0)
			return null;
		
		/*************************
		 * Send to the cloud
		 * 
		 * Command: readCloud
		 * content: partition + length 
		 * offsets 
		 * **************************/
		//System.out.println("read partition length:"+length);
		byte[] cmd=new byte[9];
		cmd[0]=CommandType.readCloud;
		Util.intToByte(cmd, 1, partition);
		Util.intToByte(cmd, 5, length);
		 
		cli.send(cmd, 9, ids, length*4, data);
		
		

		Util.readbandwidth += 9+length*4+CommInfo.blockSize; //
		Util.readNumber ++ ; //
		/*************************************
		 * Decrypt Data based on XOR technique
		 * 
		 * Enc-level-key and Enc-enc-key
		 * **********************************/
		if (block_id != CommInfo.dummyID) {
			SymmetricCypto scp = new SymmetricCypto(CommInfo.keySize);
			for (int i = 0; i < length; i++) {
				// XOR the data 000000
				int _id = Util.byteToInt(ids, i * 4, 4);
				if (_id >= 0)
					continue;
				byte[] iv = Util.generateIV(_id);
				for (int j = 0; j < CommInfo.blockSize; j++)
					xorZero[j] = 0;

//				// first onion encrypt userkey
//				scp.initEnc(userKey, iv);
//				scp.enc_decData(xorZero, CommInfo.blockSize);
//				// second onion encrypt level key
//				scp.initEnc(keys[levels[i]], null);
//				scp.enc_decData(xorZero, CommInfo.blockSize);

				// XOR
				for (int j = 0; j < CommInfo.blockSize; j++)
					data[j] ^= xorZero[j];

			}

//			// First onion decrypt by level key
//			scp.initDec(keys[level], null);
//			scp.enc_decData(data, CommInfo.blockSize);
			
			// Second, onion decrypt by the user key
			subOram.decryptData(data);
		}
		

		//update all the real numbers
		realDataNumber=0;
		int levelBegin = (1 << (MCloudCommInfo.severBeginLevel + 1)) - 2;
		for (int i=levelBegin; i<this.n_capacity; i++)
		{
			if (blockIDs[i]>0 && readFlag[i]==false)
				realDataNumber++;
		} 
		return data ; 
	}

	public byte[] generateXORBlock(int p, int _id, SecretKey userKey)
	{
		//Encrypt the 0x00 default value by the userkey
		byte[] data=new byte[CommInfo.blockSize];
		for (int i=0;i<CommInfo.blockSize;i++)
		{
			data[i] = 0;
		}
		/****************************
		 * The first 4 bytes is the dummmyID and the random value, to make sure it is less than 0
		 * 
		 * But now, to simplify the implementation, we ignore the random value for the same 0000000
		 * ******************************/
		int dummyID = CommInfo.dummyID - Util.rand_int(n_capacity);
		blockIDs[_id]=dummyID; 
		
//		byte[] iv = Util.generateIV(dummyID);
		
		//encrypt		
//		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
//		scp.initEnc(userKey, iv);
//		scp.enc_decData(data, CommInfo.blockSize); 
		return data;
	}
	
	public void writePartition(Queue slot, SocketClientUtil cli, int otherCloud, SocketClientUtil cliOtherCloud, SecretKey userKey, int cloud)
	{
		//System.out.println("write begin!!");
		int evictSize = MCloudCommInfo.evictConditionSize; 
		if (slot.size()<evictSize) {
			evictSize = slot.size(); 
		}


		int unfilledLevel = -1;
		for (int i = MCloudCommInfo.severBeginLevel; i < this.n_levels; i++) {
			if (!filled[i]) {
				unfilledLevel = i ;
				break;
			}
		}
		

		if (unfilledLevel == MCloudCommInfo.severBeginLevel){
			//CASE 2
	    	writeCase1(slot, userKey, cloud, evictSize, unfilledLevel); 
		}else{
		

			try {
				writeCase2(slot, cli, otherCloud, cliOtherCloud, userKey,
						cloud, evictSize, unfilledLevel);
			} catch (Exception e) {
				System.out.println("error!!");
			}
		}

		//System.out.println("write ok!!");
		
  
	}


	private void writeCase2(Queue slot, SocketClientUtil cli, int otherCloud,
			SocketClientUtil cliOtherCloud, SecretKey userKey, int cloud,
			int evictSize, int unfilledLevel) {
		// System.out.println(" writeCase2 ");
		 
		byte[] bSessionID = Util.generateSessionData(8);   
		int unreadRealDataNumber = 0;
		/***************************************************
		 * COMPUTE the range of the levels
		 * ********************************************/
		int begin =  (1 << (MCloudCommInfo.severBeginLevel + 1)) - 2;
		int end =  (1 << (unfilledLevel + 1)) - 2;
		boolean bSpecialCase = false;
		if (unfilledLevel == -1){
			bSpecialCase = true;
			//All Filled levels, special treatment
			unfilledLevel = n_levels - 1;
	        end = this.n_capacity;
		}
		
		for (int i=begin; i<end; i++)
			if (readFlag[i] == false)
			{ 
				if (blockIDs[i]>=0)
					unreadRealDataNumber++;
			}
		
		/**********************************************
		 * GEI the maxinum number of blocks in this level
		 * *****************************************/
        int filledLevelLength = 0; 
	    if (unfilledLevel != n_levels - 1) {
	    	filledLevelLength = 2 *(1 << unfilledLevel);
	    } else filledLevelLength = top_level_len;
	     
	    int dataNumber=unreadRealDataNumber;

		byte[] ids=new byte[dataNumber*4];//id in partition , for reading 
		int[] intIDs=new int[dataNumber];//id 
		byte[] levels=new byte[dataNumber];//store each id's level, for onion decryption
	    
		int pos = 0;
		for (int level = MCloudCommInfo.severBeginLevel; level < this.n_levels; level++) {
			if (filled[level]) {

				begin = (1 << (level + 1)) - 2;
				int datalen = 0;
				if (level != n_levels - 1) {
					datalen = 2 * (1 << level);
				} else
					datalen = top_level_len;

				for (int _id = begin; _id < begin + datalen; _id++) {
					if (this.readFlag[_id] == false  && blockIDs[_id]>=0 ) {
						Util.intToByte(ids, pos * 4, _id);
						levels[pos] = (byte) level;
						intIDs[pos++] = blockIDs[_id]; 
					}
				}
			} else {
				break;
			}
		}
		
		if (pos!=unreadRealDataNumber)
		    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		
		/****************************************
		 * (1)Notice Shuffle , Send All the data, onionkeys in slots to the otherCloud 
		 * 
		 * CommandType: noticeShuffle
		 * Content: session ID | unfilled levels number | 
		 *          onion keys and slots data  
		 *          
		 * (2)Notice this cloud to send the data to the otherCloud for shuffling
		 * CommandType: writeCloud
		 * content: session ID | unfilled - partition | other cloud for shuffling | length of ids
		 * 
		 *          
		 * (3)Send back the shuffled Data  -- cloud to cloud, but not the client
		 * CommandType: backData
		 * ************************************/

		//System.out.println("write partition filledLevelLength:"+filledLevelLength+" dataNumber:"+dataNumber+" unreadRealDataNumber:"+unreadRealDataNumber+" bSessionID:"+bSessionID[0]);
 

		int rndLength = filledLevelLength - dataNumber - 2*evictSize; 
		if (rndLength<0){
			//System.out.println(" rndLength "+ rndLength +" filledLevelLength "+filledLevelLength+" dataNumber "+dataNumber+" evictSize "+evictSize );
			return;
		}
		
		
		/***********************  First command:  noticeShuffle  ***************************/
		byte[] cmd=new byte[29];
		cmd[0]=CommandType.noticeShuffle;
		System.arraycopy(bSessionID, 0, cmd, 1, 8); 
		Util.intToByte(cmd, 9, partition);
		Util.intToByte(cmd, 13, unfilledLevel);
		Util.intToByte(cmd, 17, filledLevelLength);
		Util.intToByte(cmd, 21, dataNumber);
		Util.intToByte(cmd, 25, evictSize);
		


		cliOtherCloud.send(cmd, 29);
		
 
		//send the ids to notice how to onion decryption and encryption
		cliOtherCloud.send(  levels, dataNumber );
		
//		if (dataNumber*4>512)
//			System.out.println("----512-----");
		/****************************************************
		 * Send the data blocks to the shuffled cloud
		 * We sends them directly here, in fact, we can permute them before sending them
		 *********************************************/
		//send the slots data, the number is filled
		for (int _id=0;_id<evictSize;_id++)
		{
		    SlotObject targetObj = (SlotObject)slot.poll(); 
		    blockIDs[_id]=targetObj.id;
		    subOram.encryptData(targetObj.value); 
		    cliOtherCloud.send( targetObj.value, CommInfo.blockSize );
		    //System.out.println(" _id:"+_id+"  value:"+targetObj.value[0]);
		}

		for (int _id = evictSize; _id < 2*evictSize; _id++) {
			byte[] bDummyData = generateXORBlock(partition, _id, userKey);
		    cliOtherCloud.send( bDummyData, CommInfo.blockSize ); 
		}
 

		/****************************
		 * generate random value for dummy blocks 
		 * **************************************/	
		//generate random value
		Random rnd=new Random();
		
		int[] rnds=new int[rndLength];
		byte[] rndBytes=new byte[rndLength*4];
		for (int i=0; i<rndLength; i++)
		{
			rnds[i]= - rnd.nextInt( this.n_capacity ); //
			blockIDs[ i+2*evictSize ] = rnds[i];
			Util.intToByte(rndBytes, i*4, rnds[i]);
		}
		for (int _id=0;_id<dataNumber;_id++)
		{ 
			blockIDs[_id+2*evictSize+rndLength]=intIDs[_id];  
		}
		
 
		cliOtherCloud.send(rndBytes, rndLength*4);
		
		//send the onion keys
		/********************************************************************
		 * Notice that: must send the unfilledlevel key to the server, 
		 * because the special case, the unfilled level is the top level, the onion decryption will be used
		 ****************************************************************/

		Util.bandwidth +=  rndLength*4 + MCloudCommInfo.evictConditionSize*CommInfo.blockSize + (unfilledLevel-MCloudCommInfo.severBeginLevel+1)*CommInfo.keySize;
		
		Util.writeNumber ++;

		Util.cloudtocloud++;
		Util.cloudcloudbandwidth += (filledLevelLength) * CommInfo.blockSize +  dataNumber*CommInfo.blockSize; 


		
		for (int i=MCloudCommInfo.severBeginLevel; i<=unfilledLevel; i++)
		{
			byte[] bkeyData = getKey(i);
			cliOtherCloud.send( bkeyData, CommInfo.keySize );
		}
		
		//send the shuffle key and wait for the response
		/******************************
		 * Here, the client can receive the shffule key 
		 * But, in our implmenetation, we generate the key in the client
		 * it is also OK
		 * ************************************
		 * 
		 * The client must wait for the response of the cloud
		 * and cloud should finish the shuffle operation 
		 *
		 * Notice, we use the onion key as the shuffle key, it is also OK
		 * *********************************/

		//reset some status in the unfilled level
	    keys[unfilledLevel]=kg.generateKey(); 
	    nextDummy[unfilledLevel]=0;
		filled[unfilledLevel]=true;
		
		byte[] shufflekey = getKey(unfilledLevel);//Util.generateDummyData(MCloudCommInfo.keySize); 
		cliOtherCloud.sendAndReceive(shufflekey, 16); 
		
		//System.out.println("noticeshufule ok cloud: "+cloud+" session "+ bSessionID[0]+"  dataNumber "+dataNumber+" filledLevelLength:"+filledLevelLength);
		

		/***********************  Second command:  noticeWriteCloud  ***************************/
		/////////////////////////First, command and keys
		byte[] cmd1=new byte[22];
		cmd1[0]=CommandType.noticeWriteCloud;
		System.arraycopy(bSessionID, 0, cmd1, 1, 8);
		Util.intToByte(cmd1, 9, partition);
		Util.intToByte(cmd1, 13, unfilledLevel);
		cmd1[17] =  (byte) otherCloud;
		Util.intToByte(cmd1, 18, dataNumber);

		cli.send(cmd1, 22, ids, dataNumber*4, null);

 	
		/***************************
		 *  Final, reset the status in the client
		 *  (1) shuffle the blockIDs
		 *  (2) reset the filled and read flag
		 * *******************************/
	    psuedo_random_permute( shufflekey, filledLevelLength);
	    
	    for (int i=MCloudCommInfo.severBeginLevel;i<unfilledLevel; i++)
	    {
	    	nextDummy[i]=0;
	    	filled[i]=false; 
	    }
	    //update the status in the un-filled level
	    /******************
	     * Save to a temp int array
	     * For the worst case
	     * ******************/
	    int[] bIDS = new int[filledLevelLength];
	    for (int i=0; i<filledLevelLength; i++)
	    {
	    	bIDS[i]=blockIDs[i];
	    }
	    int levelBegin = (1 << (unfilledLevel + 1)) - 2;
	    for (int i=0;i<filledLevelLength;i++)
		{
			int bID=bIDS[i];
			int _id = i+levelBegin;
			blockIDs[_id] = bID;
			readFlag[_id]=false;
			if (bID>=0)
			{
				//update the position map
				pos_map[bID].cloud = cloud;
				pos_map[bID].partition = this.partition;
				pos_map[bID].level = unfilledLevel;
				pos_map[bID].offset = i;
			}
		}
		/*for (int i=filledLevelLength-1;i>=0;i--)
		{
			int bID=blockIDs[i];
			int _id = i+levelBegin;
			if (bID>=0)
			{
				//update the position map
				pos_map[bID].cloud = cloud;
				pos_map[bID].partition = this.partition;
				pos_map[bID].level = unfilledLevel;
				pos_map[bID].offset = i;
			}
			blockIDs[_id] = bID;
			readFlag[_id]=false;
		}*/
		//update the status of consecutive levels
		for (int i=0; i<levelBegin; i++)
		{
			blockIDs[i] = CommInfo.dummyID;
			readFlag[i]=true;
		}
		//update all the real numbers
		realDataNumber=0;
		for (int i=levelBegin; i<this.n_capacity; i++)
		{
			if (blockIDs[i]>0 && readFlag[i]==false)
				realDataNumber++;
		}
		

		if (realDataNumber > this.n_realBlocks_p)
			realDataNumber=realDataNumber;
	}


	private void writeCase1(Queue slot, SecretKey userKey, int cloud,
			int evictSize, int unfilledLevel) {
		
		
		int filledLevelLength = 2 * (1 << unfilledLevel);

		// System.out.println(" writeCase1 ");
		if (evictSize > filledLevelLength )
			 System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ");
		// shuffle in client and then write to cloud rightly
		for (int _id = 0; _id < evictSize; _id++) {
			SlotObject targetObj = (SlotObject) slot.poll();
			blockIDs[_id] = targetObj.id;
			subOram.encryptData(targetObj.value);
			System.arraycopy(targetObj.value, 0, s_buffer[_id], 0,
					CommInfo.blockSize);
			// System.out.println(" _id:"+_id+"  value:"+targetObj.value[0]);
		}
		for (int _id = evictSize; _id < filledLevelLength; _id++) {
			byte[] bDummyData = generateXORBlock(partition, _id, userKey);
			System.arraycopy(bDummyData, 0, s_buffer[_id], 0,
					CommInfo.blockSize);
			// System.out.println(" _id:"+_id+"  value:"+bDummyData[0]);
		}
		// shuffle first and then send them to the clouds
		psuedo_random_permute(s_buffer, filledLevelLength);

		keys[unfilledLevel] = kg.generateKey();
		nextDummy[unfilledLevel] = 0;
		filled[unfilledLevel] = true;

		// onion encrypt them using the level key

	    int[] bIDS = new int[filledLevelLength];
	    for (int i=0; i<filledLevelLength; i++)
	    {
	    	bIDS[i]=blockIDs[i];
	    }
		// reset the status
		int levelBegin = (1 << (unfilledLevel + 1)) - 2;
		for (int i = 0; i< filledLevelLength; i++ ) {
			int bID = bIDS[i];
			int _id = i + levelBegin;
			if (bID >= 0) {
				// update the position map
				pos_map[bID].cloud = cloud;
				pos_map[bID].partition = this.partition;
				pos_map[bID].level = unfilledLevel;
				pos_map[bID].offset = i;
			}
			blockIDs[_id] = bID;
			readFlag[_id] = false;
		}
		
		// update the status of consecutive levels
		for (int i = 0; i < levelBegin; i++) {
			blockIDs[i] = CommInfo.dummyID;
			readFlag[i] = true;
		}

		Util.writeNumber ++;
		Util.bandwidth +=  filledLevelLength*CommInfo.blockSize ;
		
		//write to cloud
		byte[] cmd1=new byte[13];
		cmd1[0]=CommandType.directWriteCloud; 
		Util.intToByte(cmd1, 1, partition);
		Util.intToByte(cmd1, 5, unfilledLevel); 
		Util.intToByte(cmd1, 9, filledLevelLength);

		cli.send(cmd1, 13);
		

		for (int _id = 0; _id < filledLevelLength; _id++) {
			cli.send( s_buffer[_id], CommInfo.blockSize );
		}
		//send over and receive
		cmd1[0]=0;
		cmd1[1]=0;
		cli.sendAndReceive(cmd1, 2); 

		//update all the real numbers
		realDataNumber=0;
		for (int i=levelBegin; i<this.n_capacity; i++)
		{
			if (blockIDs[i]>0 && readFlag[i]==false)
				realDataNumber++;
		} 
	}
	


	 private void psuedo_random_permute(byte[][] sBuffer, int len) {			
		 Random rnd=new Random();
		 int id =0;
		 byte[] bData=new byte[CommInfo.blockSize];
	        for (int i = len- 1; i > 0; --i) {
	            int j = rnd.nextInt(i);

	            System.arraycopy(sBuffer[i], 0, bData, 0, CommInfo.blockSize);
	            id=blockIDs[i];
	            System.arraycopy(sBuffer[j], 0, sBuffer[i], 0, CommInfo.blockSize);
	            blockIDs[i] =blockIDs[j];
	            System.arraycopy(bData, 0, sBuffer[j], 0, CommInfo.blockSize); 
	            blockIDs[j]=id;
	        }
	    }

	/***
	 * Permute the blockIDs in the client based on the key
	 * @param shufflekey
	 */
	private  void psuedo_random_permute(byte[] shufflekey, int filledLevelLength) {
		Util.writeNumber = Util.writeNumber+1;
		

//		for (int i = 0; i < filledLevelLength; i++) {
//			System.out.println("i: "+i+"  "+blockIDs[i]); 
//		}
		
		SecretKey sfk = new SecretKeySpec(shufflekey, "AES"); 

		for (int i = 0; i < filledLevelLength; i++) {
			int j = Util.fpeForPermution(i, sfk, filledLevelLength);
			int t = blockIDs[i];
			blockIDs[i] = blockIDs[j];
			blockIDs[j] = t; 
			
//			System.out.println("permute: "+i+"  "+j);
		}
	}
 

	public int nextDummy(int type, int level)
	{ 
	    int begin =  (1 << (level + 1)) - 2;
	    int end=0;
	    if (level != n_levels - 1) {
	        end = 2 *(1 << level);
	    } else end = top_level_len;
		//compute the position in the position map
	    for (int i=nextDummy[level];i<end;i++)
		    if (blockIDs[begin+i]<= CommInfo.dummyID)
		    {
		    	nextDummy[level]=i+1;
		    	return i;
		    }
	    
	    //Notice that, there is no dummy blocks
//	    System.out.println("No dummy !!!!!! return 0!!!!!p:"+this.partition+"  !!!type:"+type+" level:"+level+" nextDummy[level]"+nextDummy[level]);
	    return -1;
	}
}
