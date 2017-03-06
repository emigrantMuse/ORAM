package nankai.oram.server.mcloud;

import java.util.Vector;

public class SessionManager {
	public static SessionManager instance;
	
	public Vector objects ;
	public synchronized static SessionManager getInstance()
	{
		if (instance==null)
			instance=new SessionManager();
		return instance;
	}
	
	private SessionManager()
	{
		objects = new Vector();
	}
	
	public synchronized  SessionObject getObject(byte[] sessionID)
	{
		SessionObject obj = null;
		int size = objects.size(); 
		for (int i=0; i<size; i++)
		{
			obj = (SessionObject) objects.get(i);
			if (obj.isEqual(sessionID) )
				return obj;
		}
		return null;
	}

	public synchronized void addObject(SessionObject obj)
	{
		objects.add(obj);
		//System.out.println("2. add --------------------size: "+objects.size());
	}
	public synchronized void removeObject(SessionObject obj)
	{
		objects.remove(obj);
		//System.out.println("4. remove -----------------size: "+objects.size());
	}

}
