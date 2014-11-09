package snoopingBus;

public class Message
{
	private static volatile Integer messageNumberCounter = 0;
	
	public final int messageNumber;
	public final long memoryAddress;
	public final MessageType type;
	
	public Message(long memoryAddress, MessageType type)
	{
		synchronized(messageNumberCounter)
		{
			messageNumber = messageNumberCounter;
			messageNumberCounter++;
		}
		this.memoryAddress = memoryAddress;
		this.type = type;
	}
}
