package nankai.oram.client.partition;

/*************
 * The data in a level of a partition is encrypted by the key for level
 * 
 * Each data is included by BID+DATA 
 * BID = REALBLOCK? BLOCKID : - OFFSET
 */

import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import nankai.oram.client.common.Position;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;

public class Partition{ 
	public static int part=0;
	int n_levels;
	
	int n_blocks;
	int n_realBlocks_p;//the real number of blocks in a partition 
	int n_capacity;//the max capacity of a partition -- need the top level
	int top_level_len;//the number of blocks in the top level
	
	int p = 0;
	int blockIDs[];//all the blockIDs. When re-shuffle, the dummyIDs will be re-filled
	int dummyNumbers[];//the dummy block numbers;  When re-shuffle, the dummyIDs will be filled
	int nextDummy[];//the dummy block counter; 
	boolean filled[];//filled level flag
	boolean flag[];//read flag
	SecretKey keys[];//level key for each partition
	

	byte s_buffer[][];//shuffule buffer - a large memory 
	Position pos_map[];//position map
	SocketClientUtil cli;

	byte cmdData[] = new byte[13]; 

	public int realDataNumber = 0;
	KeyGenerator kg;
	public Partition(int nN, int n_partitions, Position posMap[], byte sBuffer[][], SocketClientUtil cLi)
	{ 
		s_buffer=sBuffer;
		pos_map=posMap;
		cli=cLi; 
		
		n_realBlocks_p =   (int) Math.ceil(((double) nN) / n_partitions);
	    n_blocks = n_realBlocks_p * n_partitions;
	    
	    n_levels = (int) (Math.log((double) n_realBlocks_p) / Math.log(2.0)) + 1;
	    n_capacity = (int) Math.ceil(CommInfo.capacity_parameter * n_realBlocks_p);
	    top_level_len = n_capacity - (1 << n_levels) + 2;
	    
		p = part++;
		flag=new boolean[n_capacity]; 
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
			flag[i]=true;
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

	public byte[] readPartition(int block_id) {
		

		/*******************************
		 * From Each filled level, to read a block
		 * if it is in this level, read the only one real block
		 * else, read a dummy block			 * 
		 * *****************************/
		int readNumber = 0;
		byte[] data = new byte[CommInfo.blockSize]; 
		int level=-1;
		if (block_id!=CommInfo.dummyID)
			level = pos_map[block_id].level;
		for (int i=0;i<n_levels;i++)
		{
			if (filled[i])
			{
				if (level==i && block_id!=CommInfo.dummyID)
				{
					readNumber++;
					// in this level, to read a real block. get the offset
					readBlock(p, i, pos_map[block_id].offset, data);
					/*****************************
					 * Decrypt the data
					 * *************************/ 
					SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
					scp.initDec(keys[i], null);
					scp.enc_decData(data, CommInfo.blockSize); 
					
				}else{
					readNumber++;
					// not in this level, to read a dummy block, get the dummy offset

					int dID=nextDummy(i);
					if (dID == -1) // There is no dummy block can be read
						continue; 
					
					readBlock(p, i, dID, null);
				}
			}
		}
		

		Util.readNumber ++;
		Util.readbandwidth += CommInfo.blockSize + readNumber*4;
		Util.cloudcloudbandwidth += readNumber * CommInfo.blockSize *1.2 + readNumber*4;
		Util.cloudtocloud ++;
		
		return data ; 
	}
	
	public void writePartition(int block_id, byte[] bData)
	{

		int level=-1;
		for (int i=0;i<this.n_levels;i++)
		{
			if (!filled[i])
			{
				level=i-1;
				break;
			}
		}
		
		/***************************************
		 * fetch all the unread blocks from the server
		 * ***************************************/
		int realNumber=0;
		if (level != -1) { 
			for (int i = 0; i <= level; i++) {
				realNumber = fetchLevel(i, realNumber);
				filled[i]=false;
			}
			Util.cloudtocloud = Util.cloudtocloud+1;
		}
		/******************************************
		 * If block_id is dummy block
		 *    It will not be add into the shffule buffer 
		 * ***************************************/
		if (block_id!=CommInfo.dummyID)
		    System.arraycopy(bData, 0, s_buffer[realNumber++], 0, CommInfo.blockSize);

		/***********************
		 * Treate the special level, i.e., the top level
		 * ************************/
	    if (level != n_levels - 1) ++level;
	    else{
	    	System.out.println(" Filled!!!　　　　");
	    }
		/******************************************
		 * Generate the dummy blocks and add them into the buffer 
		 * ***************************************/    
		int begin =  (1 << (level + 1)) - 2;
		int sbuffer_len=0; 
	    if (level != n_levels - 1) {
	        sbuffer_len = 2 *(1 << level);
	    } else sbuffer_len = top_level_len;
		
	    for (int i=realNumber;i<sbuffer_len;i++)
	    {
	    	//generate the data
	    	Util.intToByte(s_buffer[i], 0, CommInfo.dummyID);
	    	byte[] bDummy=Util.generateDummyData(CommInfo.blockSize-4);
	    	System.arraycopy(bDummy, 0, s_buffer[i], 4, CommInfo.blockSize-4);
	    }
		/******************************************
		 * Shuffle the plian data in the shufflebuffer
		 * Then encrypt Them using the level key
		 * ***************************************/
	    psuedo_random_permute(s_buffer, sbuffer_len);
	    //Reset the key and dummyID
	    keys[level]=kg.generateKey(); 
	    nextDummy[level]=0;
		/******************************************
		 * re-encrypt all the data
		 * ***************************************/
	    int bID=0;

		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
		scp.initEnc(keys[level], null); 
		filled[level]=true;

	    for (int i=0;i<sbuffer_len;i++)
	    {
	    	//set the flag and blockIDs
	    	bID=Util.byteToInt(s_buffer[i], 0, 4);
	    	int _id = begin+i;
	    	if (bID==CommInfo.dummyID)
	    		bID=CommInfo.dummyID-i;
	    	else{ 
				//update position map
	    		pos_map[bID].partition=p;
	    		pos_map[bID].level=level;
	    		pos_map[bID].offset=i;
	    	}
			blockIDs[_id]=bID;
	    	Util.intToByte(s_buffer[i], 0, bID);
	    	flag[_id]=false;
	    	
	    	//encrypt 
			scp.enc_decData(s_buffer[i], CommInfo.blockSize);
			//partition + ID (_id) 
			cmdData[0]=CommandType.writeBlock;
			Util.intToByte(cmdData, 1, p);
			Util.intToByte(cmdData, 5, _id);
			Util.intToByte(cmdData, 9, CommInfo.blockSize);
			//store into the database
			cli.send(cmdData, 13, s_buffer[i], CommInfo.blockSize, null); 
	    }
	    for (int i=0;i<begin;i++)
	    {
			blockIDs[i]=CommInfo.dummyID; 
	    	flag[i]=true;
	    }
	    realDataNumber=0;
	    for (int i=begin;i<this.n_capacity;i++)
	    {
			if (blockIDs[i]>=0)
				realDataNumber++;
	    }
    	
	    Util.cloudcloudbandwidth += sbuffer_len*CommInfo.blockSize;

		Util.writeNumber ++;
		Util.bandwidth += CommInfo.blockSize + sbuffer_len*4;
		Util.cloudtocloud ++;
	}

	 private void psuedo_random_permute(byte[][] sBuffer, int len) {
		Util.writeNumber = Util.writeNumber+1;
			
		 Random rnd=new Random();
		 byte[] bData=new byte[CommInfo.blockSize];
	        for (int i = len- 1; i > 0; --i) {
	            int j = rnd.nextInt(i);
/*	            bData=sBuffer[i].clone();
	            sBuffer[i] = sBuffer[j].clone();
	            sBuffer[j] = bData.clone();*/ 
	            System.arraycopy(sBuffer[i], 0, bData, 0, CommInfo.blockSize);
	            System.arraycopy(sBuffer[j], 0, sBuffer[i], 0, CommInfo.blockSize);
	            System.arraycopy(bData, 0, sBuffer[j], 0, CommInfo.blockSize); 
	        }
	    }

	private int fetchLevel(int level, int length) { 
		/******************************
		 * Compute the position of begin and end
		 * **********************************/
		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
		scp.initDec(keys[level], null);

		int begin =  (1 << (level + 1)) - 2;
		int level_len=0; 
	    if (level != n_levels - 1) {
	    	level_len = 2 *(1 << level);
	    } else level_len = top_level_len;
	    
	    int _id=0;
		
		for (int j=0; j<level_len; j++)
		{
			_id=begin+j;
			if (!flag[_id] && blockIDs[_id]>=0)
			{
				readBlock(p, level, j, s_buffer[length]);
				/**************************
				 * Decrypt it and judge whether it is a dummy block
				 * *************************/
				scp.enc_decData(s_buffer[length], CommInfo.blockSize);
				length++;
			} 
			
			//reset the flag
			flag[_id]=true;
		}
		return length;
	}

	private void readBlock(int p, int level, int offset, byte[] recData) {  
	    int begin =  (1 << (level + 1)) - 2;
	    int _id = begin+offset;
		cmdData[0]=CommandType.readBlock;
		Util.intToByte(cmdData, 1, p);

		//reset the read flag as true
		flag[_id]=true;
		Util.intToByte(cmdData, 5, _id); 
			
		cli.send(cmdData, 9,  null, 0, recData); 
		
	}
	
	public int nextDummy(int level)
	{ 
	    int begin =  (1 << (level + 1)) - 2;
	    int end=0;
	    if (level != n_levels - 1) {
	        end = 2 *(1 << level);
	    } else end = top_level_len;
		//compute the position in the position map
	    for (int i=nextDummy[level];i<end;i++)
		    if (blockIDs[begin+i] <= CommInfo.dummyID)
		    {
		    	nextDummy[level]=i+1;
		    	return i;
		    }
	    
	    //Notice that, there is no dummy blocks
	    //System.out.println("No dummy !!!!!! return 0!!!!!!!!");
	    return -1;
	}
}
