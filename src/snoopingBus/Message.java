package snoopingBus;

public class Message
{
	private static volatile Integer messageNumberCounter = 0;
	
	public final int messageNumber;
	public final long memoryAddress;
	public final MessageType type;
	public final int cycleDelay;
	
	public Message(long memoryAddress, MessageType type, int cycleDelay)
	{
		synchronized(messageNumberCounter)
		{
			messageNumber = messageNumberCounter;
			messageNumberCounter++;
		}
		this.memoryAddress = memoryAddress;
		this.type = type;
		this.cycleDelay = cycleDelay;
	}
}
