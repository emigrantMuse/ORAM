package nankai.oram.client.mCloud;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import nankai.oram.client.NewMCOSORAMClient;
import nankai.oram.client.common.Position;
import nankai.oram.client.common.SlotObject;
import nankai.oram.client.partition.Partition;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;
import nankai.oram.interfaces.MultiCloudORAM;
import nankai.oram.interfaces.ORAM;

public class SubCloudNewORAM implements MultiCloudORAM {

	int cloud;
	int n_partitions;
	int n_capacity;//the max capacity of a partition -- need the top level
    int n_levels;	 
	int n_realBlocks_p;//the real number of blocks in a partition  
	int counter=0;//for sequneceEvict
	NewMCOSPartition[] partitions;
	Position pos_map[];//position map
	SocketClientUtil[] cli;

	private SecretKey userKey; //First onion encryption Layer
	byte s_buffer[][];
	
	public SubCloudNewORAM(int npartitions, int realBlocks_p, int levels, int capacity, int c, Position posMap[], SocketClientUtil[] cLi,byte sBuffer[][])
	{
		n_partitions=npartitions;
		n_realBlocks_p=realBlocks_p;
		n_levels=levels;
		n_capacity=capacity;
		cloud=c;
		pos_map=posMap;
		cli=cLi;
		partitions=new NewMCOSPartition[n_partitions];
		s_buffer=sBuffer;
		for (int i=0;i<n_partitions;i++)
		{
			partitions[i]=new NewMCOSPartition(realBlocks_p, levels, posMap, cLi[cloud], this,s_buffer);
			partitions[i].partition=i;
		}

	    KeyGenerator kg;
		try {
			kg = KeyGenerator.getInstance("AES");
			kg.init(128);
			userKey = kg.generateKey(); 
		} catch (NoSuchAlgorithmException e) { 
			e.printStackTrace();
		}
	}
	

	public void encryptData(byte[] data) {
//		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
//		scp.initEnc(this.userKey, null);
//		scp.enc_decData(data, CommInfo.blockSize);
	}
	
	public void decryptData(byte[] data) {
//		SymmetricCypto scp=new SymmetricCypto(CommInfo.keySize);
//		scp.initDec(this.userKey, null);
//		scp.enc_decData(data, CommInfo.blockSize);
	}

	public boolean initORAM()
	{
		byte cmd[]=new byte[14]; 
		cmd[0]=CommandType.initORAM; 
		Util.intToByte(cmd, 1, n_partitions);
		Util.intToByte(cmd, 5, n_capacity);
		Util.intToByte(cmd, 9, n_levels);
		cmd[13] = (byte) this.cloud;
		
		cli[cloud].send( cmd, 14, null , 0, null);
		
		return cli[cloud].responseType!=ResponseType.wrong;
	}

	public boolean openORAM()
	{
		byte cmd[]=new byte[2]; 
		cmd[0]=CommandType.openDB; 
		cmd[1]=(byte) this.cloud; 
		
		cli[cloud].send( cmd, 2, null , 0, null);
		
		return cli[cloud].responseType!=ResponseType.wrong;
	}
	 
	@Override
	public void writeCloud(Queue slot) {
		/*****************
		 * Random select a partition 
		 * 
		 * Select another cloud to do the shuffle operation
		 * *********************/

		//System.out.println("writeCloud begin");
    	Random rnd=new Random();  
		int p = 1;//rnd.nextInt(n_partitions); 
        if (Util.debug == false){
        	p = rnd.nextInt(n_partitions);
        	//not write to the partition with more than the pre-defined real blocks 
        	while ( partitions[p].realDataNumber >= (partitions[p].top_level_len - 2*MCloudCommInfo.evictConditionSize) )
            	p = rnd.nextInt(n_partitions);
        }

		//generate a random cloud to do the shuffle operation
		int otherCloud = rnd.nextInt(MCloudCommInfo.cloudNumber);
		if (MCloudCommInfo.cloudNumber > 1){
		    while (otherCloud == this.cloud)
			    otherCloud = rnd.nextInt(MCloudCommInfo.cloudNumber);
		}
 
		
		partitions[p].writePartition(slot, cli[cloud], otherCloud, cli[otherCloud], userKey, cloud);
	}
	
	public boolean canWrite()
	{
		int realnumber = 0;
		for (int i=0;i<this.n_partitions; i++)
		{
			realnumber += this.partitions[i].realDataNumber;
		}
		if (realnumber> ( (this.n_realBlocks_p * this.n_partitions * 11)/10 ) )
			return false;
		return true;
	}


	@Override
	public byte[] readCloud(String key) { 
		return readCloud(Integer.parseInt(key));
	}
 

	@Override
	public byte[] readCloud(int id) {
		/*****************
		 * If it is a dummy Block, random select a partition to read
		 * **********************/
		byte[] data = null;
		int p = 0;
		if (id<=CommInfo.dummyID)
		{
	    	Random rnd=new Random();  
			p = rnd.nextInt(n_partitions); 
		}else{
			p = pos_map[id].partition; 
		}
		if (p>=0){
			data = partitions[p].readPartition(id, userKey); 
		    return data;	
		}else{
			return new byte[CommInfo.blockSize];
		}
	}

	 
 

}