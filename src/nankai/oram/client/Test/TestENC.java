package nankai.oram.client.Test;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import nankai.oram.client.mCloud.MCloudCommInfo;
import nankai.oram.common.CommInfo;
import nankai.oram.common.SymmetricCypto;
import nankai.oram.common.Util;

public class TestENC {
 


	public static void main(String[] args) {
		KeyGenerator kg;
		SecretKey userKey = null;
		try {
			kg = KeyGenerator.getInstance("AES");
			kg.init(128);
			userKey = kg.generateKey(); 
		} catch (NoSuchAlgorithmException e) { 
			e.printStackTrace();
		}
		
		byte[] data=new byte[CommInfo.blockSize];
		for (int i=0;i<CommInfo.blockSize;i++)
			data[i]=1;

		long testTime = -1;
		long testTime1 = -1;
    	long testDoneTime = -1;
    	long testDoneTime1 = -1;
        testTime = System.currentTimeMillis(); //ms 
        //System.out.println(testTime);
        //testTime = new Date().getTime();//us
        testTime1 = System.nanoTime();//ns
        
        /*//test aes
		for (int i = 0; i < 500000; i++) {
			SymmetricCypto scp = new SymmetricCypto(CommInfo.keySize);
			scp.initEnc(userKey, null);
			scp.enc_decData(data, CommInfo.blockSize);
		}*/
 
        //test permution
		for (int i = 0; i < 500000; i++) {
			int j = Util.fpeForPermution(i, userKey, 500000);  
		}

        testDoneTime = System.currentTimeMillis(); 
        testDoneTime1 = System.nanoTime(); 
        
        double totalElapsedTime = (testDoneTime - testTime);// / 1000.0;
        double totalElapsedTime1 = (testDoneTime1 - testTime1);// / 1000.0;
        System.out.println("totalElapsedTime:"+totalElapsedTime);  
        System.out.println("totalElapsedTime1:"+totalElapsedTime1);   
	}
}
