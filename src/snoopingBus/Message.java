package snoopingBus;

public class Message
{
	private static volatile Integer messageNumberCounter = 0;
	
	public final int messageNumber;
	public final long memoryAddress;
	public final MessageType type;
	public final MessageType secondaryType;
	public final int issueCycleTime;
	
	public Message(Message oldMessage, int cycleTimeDelay)
	{
		this(oldMessage.memoryAddress, oldMessage.type, oldMessage.secondaryType, oldMessage.issueCycleTime + cycleTimeDelay);
	}
	
	public Message(long memoryAddress, MessageType type, int issueCycleTime)
	{
		this(memoryAddress, type, null, issueCycleTime);
	}
	
	public Message(long memoryAddress, MessageType type, MessageType secondaryType, int issueCycleTime)
	{
		synchronized(messageNumberCounter)
		{
			messageNumber = messageNumberCounter;
			messageNumberCounter++;
		}
		this.memoryAddress = memoryAddress;
		this.type = type;
		this.secondaryType = secondaryType;
		this.issueCycleTime = issueCycleTime;
	}
	public String toString()
	{
		return messageNumber + "\n" + memoryAddress + "\n" + type + "\n" + secondaryType + "\n" + issueCycleTime;
	}
}
