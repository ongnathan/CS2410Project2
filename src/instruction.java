public class instruction
{
	public int cycleTime;
	public int originalCycleTime;
	public int coreID;
	public String address;
	public boolean isRead;
	public int num;
	
	public instruction (int instructionID, int time, int core, String ad, int read)
	{
		num = instructionID;
		originalCycleTime = time;
		cycleTime = time;
		coreID = core;
		address = ad;
		if(read == 0)
			isRead = true;
		else
			isRead = false;
	}
	public String toString()
	{
		String x = "Time: " + cycleTime + "\n" + "coreID: " + coreID + "\n" + "address: " + address + "\n" + "isRead: " + isRead + "\n";
		return x;
	}
	
	//easily processible for the processor
	public String processorString()
	{
		String read;
		if(isRead)
			read = "0";
		else
			read = "1";
		return cycleTime + "\t" + coreID + "\t" + read + "\t" + address;
	}
	
}
