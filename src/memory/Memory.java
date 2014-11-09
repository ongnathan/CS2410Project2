package memory;
import snoopingBus.Message;
import snoopingBus.MessageType;

// this could be cache or L2

public class Memory extends Cache
{
	private int hitPenalty;
	private int missPenalty;
	
	public Memory(int cache_size, int associativity, int block_size, boolean protocolIsMSI, int hitPenalty, int missPenalty)
	{
		super(cache_size, associativity, block_size, protocolIsMSI);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	protected int evict(long address, int index)
	{
		int evictedAssociativityIndex = super.evict(address, index);
		//no one is going to read the write back stuff, just steal the message away
		super.getOutgoingMessage();
		return evictedAssociativityIndex;
	}
	
	@Override
	public boolean runInstruction(String instruction, int delaySinceIssuing)
	{
		throw new UnsupportedOperationException("No processor to run instructions on L2 Cache");
	}
	
	//NOTE: this should be the last person to receive the message.
	//If the message was a WANT TO READ or WANT TO WRITE message, and it was handled by another cache, DO NOT SEND THE MESSAGE HERE
	@Override
	public void setAndProcessIncomingMessage(Message message, int currentCycleTime)
	{
		//we have to handle it differently
		int index = (int)(message.memoryAddress / block_size) % this.cache.length;
		int associativityIndex = -1;
		//TODO need to account for block size
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != message.memoryAddress; associativityIndex++);
		
		boolean hit = true;
		switch(message.type)
		{
			//if we are expecting this, then we are adding a new address to the cache
			case ACKNOWLEDGED_PREV_MESSAGE:
				//check for secondary writeback message
				if(message.secondaryType == MessageType.WRITE_BACK)
				{
					//same as case: WRITE_BACK
				}
				break;
			case INVALIDATE:
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return;
				}
				this.state[index][associativityIndex].busInvalidate();
				//invalidate it if it exists
				break;
			case WANT_TO_READ:
				//address not found, need to get it from "memory"
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					hit = false;
					this.cache[index][associativityIndex] = message.memoryAddress;
					this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
//					return;
				}
				//make it shared once you have it
				this.state[index][associativityIndex].busRead();
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime+(hit ? this.hitPenalty : this.missPenalty));
				break;
			case WANT_TO_WRITE:
				//address not found, need to get it from "memory"
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					hit = false;
//					return;
				}
				//make it invalid if you have it
				this.state[index][associativityIndex].busWrite();
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime+(hit ? this.hitPenalty : this.missPenalty));
				break;
			case WRITE_BACK:
				
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
	}
}
