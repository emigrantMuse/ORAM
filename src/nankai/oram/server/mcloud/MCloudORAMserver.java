package nankai.oram.server.mcloud;

import java.io.UnsupportedEncodingException;

import nankai.oram.common.CommInfo;
import nankai.oram.common.MongDBUtil;
import nankai.oram.server.partition.PartitionORAMServer;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

public class MCloudORAMserver {
	public static MCloudORAMserver instance;
	byte cloud;
	boolean initFlag;
	int N;
	int n_levels;
	int n_partitions;
	int n_blocks;
	int n_realBlocks_p;//the real number of blocks in a partition 
	int n_capacity;//the max capacity of a partition -- need the top level 
	public MongDBUtil dbUtil;
	byte[] bBlockData;
	

	byte s_buffer[][];//shuffule buffer - a large memory 

	private MCloudORAMserver()
	{
		bBlockData=new byte[CommInfo.blockSize];
	    initFlag=false;
		dbUtil=new MongDBUtil();
	    dbUtil.connect("localhost", 27017);
	    cloud = 0;
	}
	public static MCloudORAMserver getInstance()
	{
		if (instance==null)
			instance=new MCloudORAMserver();
		return instance;		
	}
	/**
	 * This function will create the database for the ORAM
	 */
	public boolean init(int paritions, int capacity, int levels, byte cloud)
	{
		if (initFlag)
			return false; 
		n_partitions = paritions; 
	    n_capacity = capacity; 
	    n_levels = levels;
	    this.cloud = cloud;
	    
	    //Create DB and open DB
	    if (!dbUtil.createDB("MCloudPartitionORAM"+cloud))
	    	return false;
	    //init partitions: create the table/collection for the partitions
	    try {
			initPartitions();
		} catch (UnsupportedEncodingException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		    return false;
		}
	    
	    initFlag=true;
	    return true;
	}
	

	public boolean openDB(byte cloud)
	{ 
		this.cloud=cloud;
	    if (!dbUtil.openDB("MCloudPartitionORAM"+cloud))
	    	return false;
	    return true;
	}
	
	public boolean writeBlock(int p, int _id, byte[] blockData, int pos) throws UnsupportedEncodingException
	{
		System.arraycopy(blockData, pos, bBlockData, 0, CommInfo.blockSize);

		MongoCollection<Document> collection = dbUtil.getCollection("part_"+p);  
		
		String str=new String(bBlockData, "ISO-8859-1"); 

        collection.findOneAndReplace(new Document("_id", _id), new Document("_id", _id).append("data", str));
        
		return true;
	}
	

	public boolean readBlock(int p, int _id, byte[] receiveData)  {
		boolean bError = false;
		try{
		MongoCollection<Document> collection = dbUtil
				.getCollection("part_" + p);
		// Find the data and return them

		FindIterable<Document> findIterable = collection.find(new Document(
				"_id", _id));
		MongoCursor<Document> mongoCursor = findIterable.iterator();
		if (mongoCursor.hasNext()) {
			Document doc1 = mongoCursor.next(); 
 
			String bData = (String) doc1.get("data");
			
			byte[] bs = bData.getBytes("ISO-8859-1"); 

			System.arraycopy(bs, 0, receiveData, 0, CommInfo.blockSize);
			return true;
		}
		}catch(Exception ex)
		{
			bError=true;
			ex.printStackTrace();
		}finally{
			if (bError) 
				System.out.println("!!!!!!!!!!!!!!!!!readBlock mongdbError,p:"+p+"  _id:"+_id);
		}

		return false;
	}
	
	private void initPartitions() throws UnsupportedEncodingException
	{
		int i=0;
		for (i=0;i<n_partitions;i++)
		{
			dbUtil.createCollection("part_"+i);
			
			//insert all the data records into the collection

			MongoCollection<Document> collection = dbUtil.getCollection("part_"+i);
			//Each level, there are max 2^i real blocks, but more than 2^i dummy blocks
			for (int j = 0; j < n_capacity; j++) {
				/*collection.insertOne(new Document("_id", j).append("data",
						new String(bBlockData, "ISO-8859-1")));*/
				collection.insertOne(new Document("_id", j).append("data",
						bBlockData ));
			}
			/***************************************
			 * Each level, there are 2^(i+1) blocks
			 * ***********************************/
 
		}
		
	}
}
