package nankai.oram.client.mCloud;

public interface MCloudCommInfo { 
	
 
//	public static int severBeginLevel = 3;//the default level of each partition in the server
//	public static int clientEndLevel = 2;//the default level of each partition in the client cache 
//	public static int evictConditionSize = 8;//1+2+4+8+16=31
//	public static int cloudNumber = 2; //the cloud number
	
//	public static int severBeginLevel = 4;//the default level of each partition in the server
//	public static int clientEndLevel =3;//the default level of each partition in the client cache 
//	public static int evictConditionSize = 16;//1+2+4=6
//	public static int cloudNumber = 2; //the cloud number
	
	public static int severBeginLevel = 2;//the default level of each partition in the server
	public static int clientEndLevel =1;//the default level of each partition in the client cache 
	public static int evictConditionSize = 4;//1+2+4=6
	public static int cloudNumber = 2; //the cloud number
	
//	public static int severBeginLevel = 5;//the default level of each partition in the server
//	public static int clientEndLevel = 4;//the default level of each partition in the client cache 
//	public static int evictConditionSize = 32;//1+2+4+8=15
//	public static int cloudNumber = 2; //the cloud number

	//public static String[] ip={ "114.215.26.85", "114.215.26.85"}; 
	public static String[] ip={ "localhost", "localhost"}; 
	public static int[] port={ 2121, 2122}; 
}
