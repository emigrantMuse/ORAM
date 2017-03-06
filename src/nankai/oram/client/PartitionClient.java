package nankai.oram.client;
 

import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import nankai.oram.client.common.Position;
import nankai.oram.client.common.SlotObject;
import nankai.oram.client.partition.Partition;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SocketClientUtil;
import nankai.oram.common.CommandType;
import nankai.oram.common.ResponseType;
import nankai.oram.common.Util;
import nankai.oram.interfaces.ORAM;


public class PartitionClient implements ORAM{
 
	boolean initFlag;
	int N;
	int n_partitions;
	int n_capacity;//the max capacity of a partition -- need the top level
    int n_levels;	
	int n_blocks;
	int n_realBlocks_p;//the real number of blocks in a partition  
	int counter=0;//for sequneceEvict
	
	byte s_buffer[][];//shuffule buffer - a large memory 
	Position pos_map[];//position map
	
	/*************************************************/
	Queue<SlotObject> slots[];
	Partition partions[];

	
	SocketClientUtil cli; 
	
	
	
	public PartitionClient()
	{ 
		initFlag=false;
	    cli=new SocketClientUtil("localhost",2121);
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

		n_realBlocks_p =   (int) Math.ceil(((double) nN) / n_partitions);
	    n_blocks = n_realBlocks_p * n_partitions;
	    
	    n_levels = (int) (Math.log((double) n_realBlocks_p) / Math.log(2.0)) + 1;
	    n_capacity = (int) Math.ceil(CommInfo.capacity_parameter * n_realBlocks_p);
		 
	    partions=new Partition[n_partitions];
 
	    s_buffer=new byte[n_capacity][CommInfo.blockSize]; 
	    pos_map=new Position[n_blocks];
	    for (int i=0;i<n_blocks; i++)
	    {
	    	pos_map[i]=new Position();
	    }
	    slots=new Queue[n_partitions]; 
	    
	    //randomly generate the keys for each level of each partition 
		for (int i = 0; i < n_partitions; i++)
		{
			slots[i]=new LinkedList<SlotObject>();
			partions[i]=new Partition(N, n_partitions, pos_map, s_buffer, cli); 
		}
					
	    counter = 0; 
	    
	    initFlag=true;
	    return true;
	}
	
	public void openConnection()
	{
		cli.connect();
	}
	public void closeConnection()
	{
		cli.disConnect();
	}
	
	public boolean initORAM()
	{
		byte cmd[]=new byte[5]; 
		cmd[0]=CommandType.initORAM;
		Util.intToByte(cmd, 1, N);
		
		cli.send( cmd, 5, null , 0, null);
		
		return cli.responseType!=ResponseType.wrong;
	}

	/**
	 * Notice the ORAM server to open the database, will use the created database
	 * @return
	 */
	public boolean openORAM()
	{
		byte cmd[]=new byte[5]; 
		cmd[0]=CommandType.openDB; 
		
		cli.send( cmd, 1, null , 0, null);
		
		return cli.responseType!=ResponseType.wrong;
	}
	 

	byte[] access(char op, int block_id, byte[] value)
    {
		byte data[] = new byte[CommInfo.blockSize];
		
		int r = 1;
    	if (Util.debug==false){
            r = Util.rand_int(n_partitions); 
        	//not write to the partition with more than the pre-defined real blocks
        	while ( partions[r].realDataNumber >= this.n_realBlocks_p )
        		r = Util.rand_int(n_partitions); 
    	}
    	
        int p = pos_map[block_id].partition; 
        /****************
         * Read data from slots or the server
         * If it is in the slot
         *    readAndDel from the slot
         *    read a dummy block from server
         * Else
         *    read the real block from the server
         * ******************/
		if (p >= 0) {
			boolean isInSlot = false; 
			Iterator itr = slots[p].iterator();
			SlotObject targetObj = null;
			while (itr.hasNext())
			{
				targetObj = (SlotObject)itr.next();
				if (targetObj.id==block_id)
				{
					isInSlot=true;
					break;
				}
			}
			 
			if ( isInSlot ) {
				// in the slot
				System.arraycopy(targetObj.value, 0, data, 0, CommInfo.blockSize); 
				slots[p].remove(targetObj);
				partions[p].readPartition(CommInfo.dummyID);
			} else {
				/**************************
				 * Here, should send a request to the cloud server to get the
				 * data
				 * **************************/ 
				byte[] bReadData=partions[p].readPartition(block_id);
				System.arraycopy(bReadData, 0, data, 0, CommInfo.blockSize); 
			}
		}
       
        pos_map[block_id].partition = r;
        pos_map[block_id].level = -1;
        pos_map[block_id].offset = -1;
        
        if (op == 'w')
        { 
        	data=value;
        }
        SlotObject newObject = new SlotObject(block_id, data);
    	slots[r].add(newObject);

//    	if (p>=0)
//            evict(p);

    	randomEvict(CommInfo.v); 
        return data;
    }

    void sequentialEvict(int vNumber)
    {   
		for (int i = 0; i < vNumber; i++) {
			counter = (counter + 1) % n_partitions;
			evict(counter);
		}
    }
    void randomEvict(int vNumber)
    {   
    	Random rnd=new Random(); 
		for (int i = 0; i < vNumber; i++) {
			int r = rnd.nextInt(n_partitions); 
			evict(r);
		}
    }
	void evict(int p) {

	    if (slots[p].isEmpty()) { 
	    	partions[p].writePartition(CommInfo.dummyID, null);  
	    } else {
	    	//pop a data in slots
	    	SlotObject obj=slots[p].poll();  
	    	partions[p].writePartition( obj.id, obj.value);
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

	/**
	 * Suggest the client to call this, when it will be closed
	 */
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

			while (slots[i].size()>0){
		    	SlotObject obj=slots[i].poll();  
				partions[i].writePartition(obj.id, obj.value);
			}
			slots[i].clear();
		} 
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
