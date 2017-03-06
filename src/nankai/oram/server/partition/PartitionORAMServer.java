package nankai.oram.server.partition;

import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import nankai.oram.common.CommInfo;
import nankai.oram.common.CommandType;
import nankai.oram.common.MongDBUtil;
import nankai.oram.common.Util;

public class PartitionORAMServer {
	public static PartitionORAMServer instance;
	boolean initFlag; 
	int n_partitions; 
	int n_capacity;//the max capacity of a partition -- need the top level 
	public MongDBUtil dbUtil;
	byte[] bBlockData;

	private PartitionORAMServer()
	{
		bBlockData=new byte[CommInfo.blockSize];
	    initFlag=false;
		dbUtil=new MongDBUtil();
	    dbUtil.connect("localhost", 27017);
	}
	public static PartitionORAMServer getInstance()
	{
		if (instance==null)
			instance=new PartitionORAMServer();
		return instance;		
	}
	
	public boolean openDB()
	{ 
	    if (!dbUtil.openDB("PartitionORAM"))
	    	return false;
	    return true;
	}
	/**
	 * This function will create the database for the ORAM
	 */
	public boolean init(int nN)
	{
		if (initFlag)
			return false; 
		n_partitions = (int) Math.ceil(Math.sqrt(nN));
		int n_realBlocks_p =   (int) Math.ceil(((double) nN) / n_partitions); 
 
	    n_capacity = (int) Math.ceil(CommInfo.capacity_parameter * n_realBlocks_p); 
	    
	    //Create DB and open DB
	    if (!dbUtil.createDB("PartitionORAM"))
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
	
	public boolean writeBlock(int p, int _id, byte[] blockData, int pos) throws UnsupportedEncodingException
	{
		System.arraycopy(blockData, pos, bBlockData, 0, CommInfo.blockSize);

		MongoCollection<Document> collection = dbUtil.getCollection("part_"+p);  
		
		String str=new String(bBlockData, "ISO-8859-1"); 

        collection.findOneAndReplace(new Document("_id", _id), new Document("_id", _id).append("data", str));
        
		return true;
	}
	

	public boolean readBlock(int p, int _id, byte[] receiveData) throws UnsupportedEncodingException {
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
