package nankai.oram.interfaces;

import java.util.Queue;

import nankai.oram.client.common.SlotObject;

public interface MultiCloudORAM {
	 
	public byte[] readCloud(String key);

	public void writeCloud(Queue slot);
	public byte[] readCloud(int key);

}
