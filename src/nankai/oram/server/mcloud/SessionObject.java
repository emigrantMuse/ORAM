package nankai.oram.server.mcloud;

import java.util.Random;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;

public class SessionObject {
	
	public byte[] sessionID;
	public byte[] levels;
	public int idsLen = 0; 
	byte[][] shffule;
	public int filledLevelLength;
	public SecretKey key[];
	public int unfilledLevel;
	SecretKey sfk; //onion level key and shuffle key
	SymmetricCypto scp;
	
	int bPos =0;
	
	public SessionObject(byte[] sid)
	{
		scp =new SymmetricCypto(CommInfo.keySize);
		//System.out.print("2. add session ");
		sessionID = new byte[8];
		for(int i=0;i<8;i++){
			//System.out.print(sid[i]+" ");
			sessionID[i]=sid[i];
		}
		//System.out.println();
	}
	
	public void setLevels(byte[] ls, int len)
	{
		if (ls!=null)
			;//System.out.println("has received IDs and levels Data!!");
		idsLen=len; 
		levels=new byte[idsLen]; 
		System.arraycopy(ls, 0, levels, 0, len); 
	}
	
	public boolean isEqual(byte[] sid)
	{
		for(int i=0;i<8;i++)
		{
			if (sessionID[i]!=sid[i])
				return false;
		}
		return true;
	}
	
	public void createShuffle(int filledLevelLength)
	{
		this.filledLevelLength = filledLevelLength; 
        shffule=new byte[filledLevelLength][CommInfo.blockSize]; 
        bPos = 0;
	}
	
	public void createkey(int unfilledLevel)
	{
		this.unfilledLevel = unfilledLevel;
		key = new SecretKey[unfilledLevel+1];
	}
	
	public void setKey(int level, byte[] bdata)
	{
		key[level] = new SecretKeySpec(bdata, "AES");
	}
	
	public void setSFKey( byte[] bdata)
	{

//		System.out.print("setSFKey   " );
//		for (int i=0;i<6;i++)
//			System.out.print ( bdata[i]+" " );
//		System.out.println(  );
		
		sfk = new SecretKeySpec(bdata, "AES");
	}
	
	public void setBlockData(int pos, byte[] bdata)
	{
		bPos++;
		System.arraycopy(bdata, 0, shffule[pos], 0, CommInfo.blockSize);
	}
	
	public void setBlockData( byte[] bdata)
	{ 
		System.arraycopy(bdata, 0, shffule[bPos++], 0, CommInfo.blockSize);
	}
	public void setBlockDataWithOnionDecryption(int pos, byte[] bdata, int level)
	{
//		System.out.println("shuffleData: onion decryption: -> level:"+level+" pos:"+pos);
//		scp.initDec(key[level], null);
//		scp.enc_decData(bdata, CommInfo.blockSize); 
		
		System.arraycopy(bdata, 0, shffule[pos], 0, CommInfo.blockSize);
	}

	public void psuedo_random_permute() { 
		byte[] bData = new byte[CommInfo.blockSize];
		

//		for (int i = 0; i < filledLevelLength; i++) {
//			System.out.println("i: "+i+"  "+shffule[i][0]); 
//		}
		
		for (int i = 0; i <filledLevelLength; i++) { 
			
			int j = Util.fpeForPermution(i, sfk, filledLevelLength); 
			System.arraycopy(shffule[i], 0, bData, 0, CommInfo.blockSize);
			System.arraycopy(shffule[j], 0, shffule[i], 0, CommInfo.blockSize);
			System.arraycopy(bData, 0, shffule[j], 0, CommInfo.blockSize);

//			System.out.println("permute: "+i+"  "+j);
		}
	}
	 

	public void onionEncryption() {  
//		for (int i = 0; i <filledLevelLength; i++) {  
//			scp.initEnc(sfk, null);	
//			scp.enc_decData(shffule[i], CommInfo.blockSize);
//		}
	}
}
