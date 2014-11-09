package memory;

import snoopingBus.Message;
import snoopingBus.MessageType;

//This is L2

public class Memory extends Cache
{
	private int hitPenalty;
	private int missPenalty;
	
	/**
	 * The constructor.  Same as Cache, except with added fields of hit and miss penalties.
	 * @param cache_size
	 * @param associativity
	 * @param block_size
	 * @param protocolIsMSI
	 * @param hitPenalty Number of clock cycles of delay due to a hit in the L2.
	 * @param missPenalty Number of clock cycles of delay due to a miss in the L2.
	 */
	public Memory(int cache_size, int associativity, int block_size, boolean protocolIsMSI, int hitPenalty, int missPenalty)
	{
		super(cache_size, associativity, block_size, protocolIsMSI);
		// TODO Auto-generated constructor stub
	}
	
	//NOTE: inheritDoc means see Cache's version of the documentation
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int evict(long address, int index)
	{
		int evictedAssociativityIndex = super.evict(address, index);
		//no one is going to read the write back stuff, just steal the message away
		this.message = null;
		return evictedAssociativityIndex;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean runInstruction(String instruction, int delaySinceIssuing)
	{
		throw new UnsupportedOperationException("No processor to run instructions on L2 Cache");
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Message getOutgoingMessage()
	{
		Message message = super.getOutgoingMessage();
		//the L2 cache never listens for return messages
		this.message = null;
		return message;
	}
	
	/**
	 * {@inheritDoc}
	 * NOTE: this should be the last person to receive the message.
	 * If the message was a WANT_TO_READ or WANT_TO_WRITE message, and it was handled by another cache, DO NOT SEND THE MESSAGE HERE
	 */
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
					boolean emptySpaceFound = false;
					for(int j = 0; j < this.associativity; j++)
					{
						if(this.cache[index][j] == -1L)
						{
							associativityIndex = j;
							emptySpaceFound = true;
							break;
						}
					}
					//need to evict if no empty space available
					if(!emptySpaceFound)
					{
						associativityIndex = this.evict(message.memoryAddress, index);
					}
					this.cache[index][associativityIndex] = message.memoryAddress;
					this.state[index][associativityIndex].busInvalidate();
					this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime+(emptySpaceFound ? 0 : this.missPenalty);
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
					boolean emptySpaceFound = false;
					for(int j = 0; j < this.associativity; j++)
					{
						if(this.cache[index][j] == -1L)
						{
							associativityIndex = j;
							emptySpaceFound = true;
							break;
						}
					}
					//need to evict if no empty space available
					if(!emptySpaceFound)
					{
						associativityIndex = this.evict(message.memoryAddress, index);
						
					}
					this.cache[index][associativityIndex] = message.memoryAddress;
					this.state[index][associativityIndex].processorRead();
				}
				//make it shared once you have it
				this.state[index][associativityIndex].busRead();
				this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime+(hit ? this.hitPenalty : this.missPenalty));
				break;
			case WANT_TO_WRITE:
				//address not found, need to get it from "memory"
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					hit = false;
					boolean emptySpaceFound = false;
					for(int j = 0; j < this.associativity; j++)
					{
						if(this.cache[index][j] == -1L)
						{
							associativityIndex = j;
							emptySpaceFound = true;
							break;
						}
					}
					//need to evict if no empty space available
					if(!emptySpaceFound)
					{
						associativityIndex = this.evict(message.memoryAddress, index);
						
					}
					this.cache[index][associativityIndex] = message.memoryAddress;
					this.state[index][associativityIndex].processorRead();
				}
				//make it invalid if you have it
				this.state[index][associativityIndex].busWrite();
				this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime+(hit ? this.hitPenalty : this.missPenalty));
				break;
			case WRITE_BACK:
				boolean emptySpaceFound = false;
				for(int j = 0; j < this.associativity; j++)
				{
					if(this.cache[index][j] == -1L)
					{
						associativityIndex = j;
						emptySpaceFound = true;
						break;
					}
				}
				//need to evict if no empty space available
				if(!emptySpaceFound)
				{
					associativityIndex = this.evict(message.memoryAddress, index);
				}
				this.cache[index][associativityIndex] = message.memoryAddress;
				this.state[index][associativityIndex].processorExclusiveRead();
				this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime+(emptySpaceFound ? 0 : this.missPenalty);
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
	}
}
