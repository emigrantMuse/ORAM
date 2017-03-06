package nankai.oram.common;

public interface CommandType {

	public static byte initORAM=1; 
	public static byte readBlock=2; 
	public static byte writeBlock=3; 
	public static byte readCloud=4; 
	public static byte noticeWriteCloud=5; //client notices the cloud to be ready for the write operation
	public static byte noticeShuffle=6; //client notices the cloud to be read for the shuffle operation
	public static byte shuffleData=7; //cloud sends the shuffle data to the other cloud
	public static byte backData=8;//the other cloud sends back the shuffled data
	public static byte directWriteCloud=9; //client write to cloud the unfilled level directly
	
 
	

	public static byte testTime=112;//the other cloud sends back the shuffled data
	public static byte closeThread=111; 
	

	public static byte openDB=113;//the other cloud sends back the shuffled data
}
