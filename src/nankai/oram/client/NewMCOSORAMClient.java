package nankai.oram.client;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import nankai.oram.client.common.Position;
import nankai.oram.client.common.SlotObject;
import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.client.mCloud.NewMCOSPartition;
import nankai.oram.client.mCloud.SubCloudNewORAM;
import nankai.oram.client.partition.Partition;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;
import nankai.oram.interfaces.MultiCloudORAM;
import nankai.oram.interfaces.ORAM;

public class NewMCOSORAMClient implements ORAM{
	

	boolean initFlag;
	SubCloudNewORAM[] subORAMs;
	 
	int N;
	int n_partitions; //All the partitions, for each cloud, it would be divided
	int n_partitions_cloud;//the number of partitions for a cloud
	int n_capacity;//the max capacity of a partition -- need the top level
    int n_levels;	
	int n_blocks;
	int n_realBlocks_p;//the real number of blocks in a partition 
	int top_level_len;//the number of blocks in the top level	
	int counter=0;//for sequneceEvict
	
	Position pos_map[];//position map
	
	/*************************************************/
	Queue<SlotObject> slots[]; 
	
	SocketClientUtil[] cli; 
	 
	byte s_buffer[][];
	public NewMCOSORAMClient()
	{ 
		initFlag=false;
	    cli=new SocketClientUtil[MCloudCommInfo.cloudNumber];

	}
	
	/**
	 * initialize the parameters, storage space and so on
	 * @param nN
	 */
	public boolean init(int nN)
	{
		if (initFlag==true)
		{
			System.out.println("have inited!");
			return false;
		}
		N=nN; 
		n_partitions = (int) Math.ceil(Math.sqrt(nN));
		n_partitions_cloud = (int) Math.ceil(n_partitions/MCloudCommInfo.cloudNumber) ;

		n_realBlocks_p =   (int) Math.ceil(((double) nN) / n_partitions);
	    n_blocks = n_realBlocks_p * n_partitions;
	    
	    n_levels = (int) (Math.log((double) n_realBlocks_p) / Math.log(2.0)) + 1;
	    n_capacity = (int) Math.ceil(CommInfo.capacity_parameter * n_realBlocks_p);
		   
	    pos_map=new Position[n_blocks];
	    for (int i=0;i<n_blocks; i++)
	    {
	    	pos_map[i]=new Position();
	    }
	    slots=new Queue[MCloudCommInfo.cloudNumber]; 
	    subORAMs=new SubCloudNewORAM[MCloudCommInfo.cloudNumber]; 
	    s_buffer=new byte[MCloudCommInfo.evictConditionSize*3][CommInfo.blockSize];//the real value is evicCondition*2
	    
	    //randomly generate the keys for each level of each partition 
		for (int i = 0; i < MCloudCommInfo.cloudNumber; i++)
		{
			slots[i]=new LinkedList<SlotObject>();
			cli[i]=new SocketClientUtil(MCloudCommInfo.ip[i], MCloudCommInfo.port[i]);
			subORAMs[i]=new SubCloudNewORAM(n_partitions_cloud, n_realBlocks_p, n_levels, n_capacity, i, pos_map, cli, s_buffer);
		}
					
	    counter = 0; 
	    
	    initFlag=true;
	    return true;
	}
	
	public void openConnection()
	{ 
		for (int i = 0; i < MCloudCommInfo.cloudNumber; i++)
		{
		    cli[i].connect();
		}
	}
	public void closeConnection()
	{
		for (int i = 0; i < MCloudCommInfo.cloudNumber; i++)
		{
		    cli[i].disConnect();
		}
	}
	
	public boolean initORAM()
	{ 
		boolean bRet = true;
		for (int i = 0; i < MCloudCommInfo.cloudNumber; i++)
		{
			if (subORAMs[i].initORAM()==false)
				bRet = false;
		}
		
		return bRet;
	}

	/**
	 * Notice the ORAM server to open the database
	 * @return
	 */
	public boolean openORAM()
	{
		boolean bRet = true;
		for (int i = 0; i < MCloudCommInfo.cloudNumber; i++)
		{
			if (subORAMs[i].openORAM()==false)
				bRet = false;
		}
		
		return bRet;
	}
	 
	 

	byte[] access(char op, int block_id, byte[] value)
    {
		
//		try {
//			Thread.sleep(10);
//		} catch (InterruptedException e) {
//			// TODO 自动生成的 catch 块
//			e.printStackTrace();
//		}
		try{

//			System.out.println("access begin");

			byte data[] = new byte[CommInfo.blockSize];

			int r = Util.rand_int(MCloudCommInfo.cloudNumber);

			int c = pos_map[block_id].cloud;
			int p = pos_map[block_id].partition;
			/****************
			 * Read data from slots or the server If it is in the slot
			 * readAndDel from the slot read a dummy block from server Else read
			 * the real block from the server
			 * ******************/
			if (c >= 0) {
				boolean isInSlot = false;
				Iterator itr = slots[c].iterator();
				SlotObject targetObj = null;
				while (itr.hasNext()) {
					targetObj = (SlotObject) itr.next();
					if (targetObj.id == block_id) {
						isInSlot = true;
						break;
					}
				}

				if (isInSlot) {
					// in the slot
					System.arraycopy(targetObj.value, 0, data, 0,
							CommInfo.blockSize);
					slots[c].remove(targetObj);
//					System.out.println("readdummy block_id " + block_id + " c:"
//							+ c + " p:" + p);
					subORAMs[c].readCloud(CommInfo.dummyID);
//					System.out.println("readdummy end block_id " + block_id
//							+ " c:" + c + " p:" + p);
				} else {
					/**************************
					 * Here, should send a request to the cloud server to get
					 * the data
					 * **************************/
//					System.out.println("readCloud block_id " + block_id + " c:"
//							+ c + " p:" + p);
					System.arraycopy(subORAMs[c].readCloud(block_id), 0, data,
							0, CommInfo.blockSize);
//					System.out.println("read end");
				}

			}

			pos_map[block_id].cloud = r;
			pos_map[block_id].partition = -1;
			pos_map[block_id].level = -1;
			pos_map[block_id].offset = -1;

			if (op == 'w') {
				System.arraycopy(value, 0, data, 0, CommInfo.blockSize);
			}
			writeCache(block_id, data, r);

			//System.out.println("sequentialEvict begin");
			sequentialEvict(CommInfo.v);
			//System.out.println("sequentialEvict end");

//			System.out.println("access end");
			return data;
		}catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
    }

	/****
	 * Write to the cache. With the first layer onion-encryption
	 * @param block_id
	 * @param data
	 * @param r
	 */
	private void writeCache(int block_id, byte[] data, int c) {
		//should be first onion - encryption 
		
		SlotObject newObject = new SlotObject(block_id, data);
		
    	slots[c].add(newObject);
	}


    void sequentialEvict(int vNumber)
    {   
		for (int i = 0; i < vNumber; i++) {
			counter = (counter + 1) % MCloudCommInfo.cloudNumber;
			evict(counter);
		}
    }
    void randomEvict(int vNumber)
    {   
    	Random rnd=new Random(); 
		for (int i = 0; i < vNumber; i++) {
			int r = rnd.nextInt(MCloudCommInfo.cloudNumber); 
			evict(r);
		}
    }
	void evict(int c) {

		if (slots[c].size() >= MCloudCommInfo.evictConditionSize)
		{			
			if (subORAMs[c].canWrite())
			    subORAMs[c].writeCloud(slots[c]);
		}
	}
	

	public int getCacheSlotSize()
	{
		int ret = 0;
		for (int i=0; i<slots.length; i++)
		{
			ret += slots[i].size();
		}
		return ret;
	}
	
	public void clearSlot()
	{
		for (int i=0; i<slots.length; i++)
		{
			/**************
			 * Here, do not clear directly
			 * Just remove from the slot, but the data should always stored in the database
			 * So, we should update the position map
			 * 
			 */

			if (slots[i].size()>0)
			    subORAMs[i].writeCloud(slots[i]); 
			 slots[i].clear();
		} 
	}
	
	public int getIDinDB()
	{
		for (int i=0;i<n_blocks; i++)
		{
			if (pos_map[i].partition != -1 )
				return i;
		}
		return -1;
	}

	@Override
	public void write(String idStr, byte[] value) { 
	    access('w', Integer.parseInt(idStr), value); 
	}

	@Override
	public byte[] read(String idStr) { 
	    return access('r', Integer.parseInt(idStr), null); 
	}

	@Override
	public void write(int id, byte[] value) { 
	    access('w', id, value); 
	}

	@Override
	public byte[] read(int id) { 
	    return access('r', id, null); 		
	}

}
