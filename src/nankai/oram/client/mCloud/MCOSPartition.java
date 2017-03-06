package nankai.oram.client.mCloud;

import java.security.NoSuchAlgorithmException;
import java.util.Queue;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import nankai.oram.client.common.Position;
import nankai.oram.client.common.SlotObject;
import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;

public class MCOSPartition { 
	int n_levels;
	 
	int n_realBlocks_p;//the real number of blocks in a partition 
	int n_capacity;//the max capacity of a partition -- need the top level
	int top_level_len;//the number of blocks in the top level
	
	public int partition = 0;
	int blockIDs[];//all the blockIDs. When re-shuffle, the dummyIDs will be re-filled
	int dummyNumbers[];//the dummy block numbers;  When re-shuffle, the dummyIDs will be filled
	int nextDummy[];//the dummy block counter; 
	boolean filled[];//filled level flag
	int clouds[];//filled level flag
	boolean readFlag[];//read flag
	SecretKey keys[];//level key for each partition
	
 
	Position pos_map[];//position map
	SocketClientUtil[] cli;

 
	KeyGenerator kg;
	
	public int realDataNumber = 0;
	
	public MCOSPartition(int realBlocks_p, int levels, Position posMap[], SocketClientUtil[] cLi, int p )
	{   
		partition=p;
		pos_map=posMap;
		cli=cLi; 
		
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
		clouds=new int[n_levels];

		for (int i=0;i<n_levels;i++)
		{
			dummyNumbers[i]=0;
			nextDummy[i]=0;
			filled[i]=false;
			clouds[i]=-1;
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
	 
	 

	public void readForShuffle(int block_id, SocketClientUtil cli, byte[] bSessionID, byte[] bSfk, int levels ) {
		
		/*******************************
		 * Gnerate the OFFSETs 
		 * *****************************/ 
		byte[] ids = new byte[ n_levels * 4 ];   
		int level = -1;
		if (block_id!=CommInfo.dummyID)
			level = pos_map[block_id].level;
		int length = 0; 
		for (int i=0;i<n_levels;i++)
		{
			if (filled[i])
			{ 
			    int _id =  (1 << (i + 1)) - 2; 
				
				if (level==i && block_id!=CommInfo.dummyID)
				{
					_id += pos_map[block_id].offset; 
				}else{
					// not in this level, to read a dummy block, get the dummy offset
					int dID=nextDummy(0, i);
					if (dID == -1) // There is no dummy block can be read
						continue;
					_id += dID;//nextDummy(i); 
				}
				readFlag[_id]=true;
				Util.intToByte(ids, length*4, _id);  
				length ++; 
			}
		}
		
		byte[] cmd=new byte[37];
		cmd[0]=CommandType.noticeShuffle;
		System.arraycopy(bSessionID, 0, cmd, 1, 8);  
		System.arraycopy(bSfk, 0, cmd, 9, 16); 
		
		Util.intToByte(cmd, 25, partition);
		Util.intToByte(cmd, 29, length);
		Util.intToByte(cmd, 33, levels);
		
		if (length==0) 
		{
			//should send a key to the cloud to shuffle 
			cli.send(cmd, 37, null, 0, null); 
		}else{ 
			cli.send(cmd, 37, ids, length*4, null); 
		}
	}

	public synchronized byte[] readPartition(int block_id ) { 
		/***********************************************
		 * Decide which cloud need to do shuffle or to read 
		 *********************************************/
		int realLevel = -1;
		int realCloud = -1;
		if (block_id!=CommInfo.dummyID){
		    realLevel =	this.pos_map[block_id].level;
		    realCloud =	this.pos_map[block_id].level;
		}
		int levelnumbers1=0;
		int levelnumbers2=0;  
		for (int i=0;i<this.n_levels;i++)
		{
			if (this.filled[i]==true)
			{ 
				if (this.clouds[i]==0)
					levelnumbers1++;
				if (this.clouds[i]==1)
					levelnumbers2++;
			}
		}
		int shuffleCloud =0;
		if (levelnumbers1 == 0 && levelnumbers2 ==0 )
			return null;
		else{
		    if (levelnumbers1 < levelnumbers2)
			    shuffleCloud=1;
		}
		
		int otherCloud=(shuffleCloud==0)?1:0; 

//		/***********************************************
//		 * get the first unfilled level
//		 *********************************************/ 
//		int level=-1;
//		for (int i=0;i<this.n_levels;i++)
//		{
//			if (this.filled[i]==false)
//			{
//				level=i;
//				break;
//			}
//		}
		
		/******************************************** 
		 * Session ID and shuffle key
		 ***************************************/
		byte[] bSessionID = Util.generateSessionData(8);  
		byte[] bSfk=null;
		SecretKey sfk =null;
		KeyGenerator kg;
		try {
			kg = KeyGenerator.getInstance("AES");
			kg.init(128);
			sfk = kg.generateKey(); 
			bSfk= sfk.getEncoded();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		/******************************************** 
		 * Get the real block position and compute its position after shuffle
		 ***************************************/
		//Get which level the real block is in
		int realPos = 0;
		if (realLevel == -1)
		{
			//if dummy block,  random select
			Random rnd=new Random();
			realPos=rnd.nextInt(levelnumbers1 + levelnumbers2); 
		}else{
			//first the shuffle cloud will read the levels in it, and then received the data from other clouds
			//the position in this sequence is the original real position of blockID
			boolean bFind=false;
			for (int i = 0; i < n_levels; i++) {
				if (filled[i] == true && clouds[i]==shuffleCloud)
				{
					realPos++;
					if (realLevel==i){
						bFind=true;
						break;
					}
				}
			} 
			if (bFind==false)
			{
				for (int i = 0; i <= realLevel; i++) {
					if (filled[i] == true && clouds[i] == otherCloud)
					{
						realPos++;
					}
				}
			}
		}
		//get the permute result
		if (realPos>0) realPos--;
		int targetPos = realPos;
				//psuedo_random_permute( levelnumbers1 + levelnumbers2,   sfk,   realPos);
		//Util.fpeForPermution(realPos, sfk, levelnumbers1 + levelnumbers2); 
		
		//System.out.println(" realPos: "+realPos+"  targetPos: "+targetPos);
		/**********************************************************************************
		 * read for shuffle
		 *******************************************************************************/
		/*******************************
		 * Generate the OFFSETs in shuffle cloud
		 * And the ids read in other cloud
		 * *****************************/ 
		byte[] ids = new byte[ n_levels * 4 ];
		byte[] idsOther = new byte[ n_levels * 4 ];
		int length = 0; 
		int lengthOther = 0; 
		for (int i=0;i<n_levels;i++)
		{
			if (filled[i])
			{ 
			    int _id =  (1 << (i + 1)) - 2; 
				
				if (realLevel==i)
				{
					_id += pos_map[block_id].offset; 
				}else{
					// not in this level, to read a dummy block, get the dummy offset
					int dID=nextDummy(0, i);
					if (dID == -1) // There is no dummy block can be read
						continue;
					_id += dID;//nextDummy(i); 
				}
				readFlag[_id]=true;
				if (clouds[i]==shuffleCloud){
				    Util.intToByte(ids, length*4, _id);  
				    length ++; 
				}
				if (clouds[i]==otherCloud){
				    Util.intToByte(idsOther, lengthOther*4, _id);  
				    lengthOther ++; 
				}
			}
		}
		 
		byte[] cmd=new byte[37];
		cmd[0]=CommandType.noticeShuffle;
		System.arraycopy(bSessionID, 0, cmd, 1, 8);  
		System.arraycopy(bSfk, 0, cmd, 9, 16);  
		
		Util.intToByte(cmd, 25, partition);
		Util.intToByte(cmd, 29, length);
		Util.intToByte(cmd, 33, levelnumbers1 + levelnumbers2);
		
		cli[shuffleCloud].send(cmd, 37); 
		if (length > 0) 
		{ 
			cli[shuffleCloud].send( ids, length*4 ); 
		}

		byte[] cmd1=new byte[2];
		cmd1[0]=0;
		cmd1[1]=0;
		cli[shuffleCloud].sendAndReceive(cmd1, 2); 

		/**********************************************************************************
		 * read for cloud
		 *******************************************************************************/ 
		cmd[0]=CommandType.readCloud;
		System.arraycopy(bSessionID, 0, cmd, 1, 8);    
		Util.intToByte(cmd, 9, partition); 
		Util.intToByte(cmd, 13, targetPos);
		Util.intToByte(cmd, 17, lengthOther);    
		Util.intToByte(cmd, 21, shuffleCloud); 
		
		byte[] bData=new byte[CommInfo.blockSize];
		if (lengthOther==0) 
		{ 
			cli[otherCloud].send(cmd, 25, null, 0, bData); 
		}else{
			// get the data  
			 
			cli[otherCloud].send(cmd, 25, idsOther, lengthOther*4, bData);
		}
		

		//generate the information for analysis
		Util.readNumber ++; 
		Util.readbandwidth += 37 + 25+ (length+lengthOther)*4 + CommInfo.blockSize; 
		Util.cloudtocloud++;

		Util.cloudcloudbandwidth += (lengthOther + length + lengthOther) * CommInfo.blockSize ;


		return bData; 
	}
	 

	public int psuedo_random_permute(int filledLevelLength, SecretKey sfk, int realPos) { 
		int[] shffule = new int[filledLevelLength];

		for (int i = 0; i <filledLevelLength; i++) { 
			shffule[i]=i;
		}
 
		
		for (int i = 0; i <filledLevelLength; i++) {  
			int j = Util.fpeForPermution(i, sfk, filledLevelLength); 
			int t = shffule[i];
			shffule[i] = shffule[j];
			shffule[j] = shffule[i]; 
		}
		return shffule[realPos];
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
	
	public synchronized void writePartition(SlotObject targetObj )
	{

		/******************************
		 * Select the cloud which contains the consecutive levels
		 **********************************/
		//should tell the cloud unread block IDs, but we ignore here 
		int unreadDataNumber = 0;  // the un-read data number
		int filledLevel =-1;
		int cloud = 0;//the cloud to do merge 
		
		/*******************
		 * 1. judge which cloud to be written
		 * The algorithm is that cloud = Filled level - -1 +cloud % 2
		 * This is which cloud should be written
		 * **************************/   
		for (int i=0; i<this.n_levels; i++)
		{
			if (filled[i]==true)
			{
				filledLevel=i;
				break;
			}
		}
		if (filledLevel ==-1 )
		    cloud = 0;
		else
			if (filledLevel == 0 )
			    cloud = clouds[0];
			else
				cloud = (filledLevel-1+clouds[filledLevel])%2;
		/**************************************
		 * cloud is the target cloud to be written
		 * The unfilled level is not 0, then written to other cloud
		 *************************************/

		/*******************
		 * 2. get the unfilled level
		 * **************************/  
		int unfilledLevel=-1;
		for (int i=0; i<this.n_levels; i++)
		{
			if (filled[i]==false )
			{
				unfilledLevel=i;
				break;
			}
		}

		int otherCloud = cloud ;
		if (unfilledLevel>0)
			otherCloud =(cloud==0)?1:0;
		/***************************************************
		 * COMPUTE the range of the levels
		 * ********************************************/
		int begin =  0;
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
				unreadDataNumber++; 
			}
		
		/**********************************************
		 * GEI the maxinum number of blocks in this level
		 * *****************************************/
        int filledLevelLength = 0; 
	    if (unfilledLevel != n_levels - 1) {
	    	filledLevelLength = 2 *(1 << unfilledLevel);
	    } else filledLevelLength = top_level_len;


	    /***********************
	     * Special case, special treatment
	     * **********************/
		
		byte[] ids=new byte[unreadDataNumber*4];//id in partition , for reading  
 
		/*****************
		 * Special case: un-read data number is bigger than filledlevel length
		 * ********************/
	    if (unreadDataNumber > filledLevelLength-2)
	    {
	    	unreadDataNumber=filledLevelLength-2;
	    	//read the real block and then the dummy blocks
			int pos = 0;
			//first , real block
			for (int level = 0; level < this.n_levels; level++) {
				if (filled[level]) {

					begin = (1 << (level + 1)) - 2;
					int datalen = 0;
					if (level != n_levels - 1) {
						datalen = 2 * (1 << level);
					} else
						datalen = top_level_len;

					for (int _id = begin; _id < begin + datalen; _id++) {
						if (this.readFlag[_id] == false && this.blockIDs[_id]>=0) {
							Util.intToByte(ids, pos * 4, _id);
							pos++;
						}
					}
				} else {
					break;
				}
			}
			//second , dummy block
			for (int level = 0; level < this.n_levels; level++) {
				if (filled[level]) {

					begin = (1 << (level + 1)) - 2;
					int datalen = 0;
					if (level != n_levels - 1) {
						datalen = 2 * (1 << level);
					} else
						datalen = top_level_len;

					for (int _id = begin; _id < begin + datalen; _id++) {
						if (pos>unreadDataNumber)
							break;
						if (this.readFlag[_id] == false && this.blockIDs[_id]<0) {
							Util.intToByte(ids, pos * 4, _id);
							pos++; 
						}
					}
				} else {
					break;
				}
			}
	    	
	    }else{
			int pos = 0;
			for (int level = 0; level < this.n_levels; level++) {
				if (filled[level]) {

					begin = (1 << (level + 1)) - 2;
					int datalen = 0;
					if (level != n_levels - 1) {
						datalen = 2 * (1 << level);
					} else
						datalen = top_level_len;

					for (int _id = begin; _id < begin + datalen; _id++) {
						if (this.readFlag[_id] == false) {
							Util.intToByte(ids, pos * 4, _id);
							pos++;
						}
					}
				} else {
					break;
				}
			}
	    }

		/**********************************************
		 * Send to server
		 * *****************************************/
		byte[] cmd1=new byte[25];
		cmd1[0]=CommandType.noticeWriteCloud; 
		Util.intToByte(cmd1, 1, partition); 
		Util.intToByte(cmd1, 5, cloud); 
		Util.intToByte(cmd1, 9, unfilledLevel); 
		Util.intToByte(cmd1, 13, filledLevelLength); 
		Util.intToByte(cmd1, 17, unreadDataNumber); 
		Util.intToByte(cmd1, 21, otherCloud); 
		
		

		cli[cloud].send(cmd1, 25);
		
        cli[cloud].send(targetObj.value, CommInfo.blockSize ); 
        
        
        if (unreadDataNumber>0)
            cli[cloud].send(ids, unreadDataNumber*4 ); 
        
        //key? shuffle ?- for later
        keys[unfilledLevel]=kg.generateKey(); 
		filled[unfilledLevel]=false; 
		
		byte[] shufflekey = getKey(unfilledLevel);//Util.generateDummyData(MCloudCommInfo.keySize); 
		cli[cloud].send(shufflekey, 16); 

		
		

		//generate the information for analysis
		Util.writeNumber ++; 
		Util.bandwidth +=   CommInfo.blockSize + 25 + unreadDataNumber*4 + CommInfo.keySize + 4*(filledLevelLength-unreadDataNumber-1);
		if (otherCloud != cloud) { 
			Util.cloudtocloud++;
			Util.cloudcloudbandwidth += (filledLevelLength) * CommInfo.blockSize ;
		} 
		

	    int[] bIDS = new int[filledLevelLength]; 
	    bIDS[0]=targetObj.id;
        for (int i=0;i<unreadDataNumber;i++)
        {
        	int _id=Util.byteToInt(ids, i*4, 4);
        	bIDS[i+1]=blockIDs[_id];
        }
        for (int i=unreadDataNumber;i<filledLevelLength-1;i++)
        {
        	bIDS[i+1]=CommInfo.dummyID;
        }
        

	    psuedo_random_permute( shufflekey, filledLevelLength, bIDS);
		//send over and receive
		cmd1[0]=0;
		cmd1[1]=0;
		cli[cloud].sendAndReceive(cmd1, 2); 

		/**********************************************
		 * reset client status
		 * *****************************************/
		//reset status. will be filled to another level
		for (int i=0; i<unfilledLevel;i++)
		{
		    nextDummy[i]=0;
			filled[i]=false;
			clouds[i]=-1;
		}
		//set this cloud as null ,but the other unfilled level as true 
		nextDummy[unfilledLevel]=0;
		filled[unfilledLevel]=true;
		clouds[unfilledLevel]=otherCloud;
 
	    int levelBegin = (1 << (unfilledLevel + 1)) - 2;
		for (int i=0; i<levelBegin; i++)
		{
			blockIDs[i] = CommInfo.dummyID;
			readFlag[i]=true;
		}
	    for (int i=0;i<filledLevelLength;i++)
		{
			int bID=bIDS[i];
			int _id = i+levelBegin; 
			blockIDs[_id] = CommInfo.dummyID;
			readFlag[_id] = false;
			if (bID>=0)
			{
				blockIDs[_id] = bID;
				//update the position map
				pos_map[bID].cloud = otherCloud; //store other cloud
				pos_map[bID].partition = this.partition;
				pos_map[bID].level = unfilledLevel;
				pos_map[bID].offset = i;
			}
		}
	}
	

	/***
	 * Permute the blockIDs in the client based on the key
	 * @param shufflekey
	 */
	private  void psuedo_random_permute(byte[] shufflekey, int filledLevelLength, int[] bIDS) {
		Util.writeNumber = Util.writeNumber+1;
		

//		for (int i = 0; i < filledLevelLength; i++) {
//			System.out.println("i: "+i+"  "+blockIDs[i]); 
//		}
		
		SecretKey sfk = new SecretKeySpec(shufflekey, "AES"); 

		for (int i = 0; i < filledLevelLength; i++) {
			int j = Util.fpeForPermution(i, sfk, filledLevelLength);
			int t = bIDS[i];
			bIDS[i] = bIDS[j];
			bIDS[j] = t; 
			
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
	    //System.out.println("No dummy !!!!!! return 0!!!!!p:"+this.partition+"  !!!type:"+type+" level:"+level+" nextDummy[level]"+nextDummy[level]);
	    return -1;
	}
}
