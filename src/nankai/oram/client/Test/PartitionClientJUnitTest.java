package nankai.oram.client.Test;

import static org.junit.Assert.*;
import nankai.oram.client.PartitionClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PartitionClientJUnitTest {

	PartitionClient cliORAM=new PartitionClient(); 
	@Before
	public void setUp() throws Exception {
		System.out.println("setUp");
		cliORAM.init(10240);
		cliORAM.openConnection();
		
	}

	@After
	public void tearDown() throws Exception {
		System.out.println("tearDown");
		cliORAM.closeConnection();
	}
 
	@Test
	public void testInitORAM() {
		if (!cliORAM.initORAM())
		    fail("ипн╢й╣ож");
	}

}
