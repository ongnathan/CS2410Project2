package memory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import snoopingBus.Message;
import snoopingBus.MessageType;

//This is L2

public class Memory extends Cache
{
	private final int hitPenalty;
	private final int missPenalty;
	
	private final int numberOfBanks;
	
	private final int[] bankFreeAtCycle;
	private final List<List<Message>> addressesBeingRetrieved;
	
	/**
	 * The constructor.  Same as Cache, except with added fields of hit and miss penalties.
	 * @param cache_size
	 * @param associativity
	 * @param block_size
	 * @param protocolIsMSI
	 * @param hitPenalty Number of clock cycles of delay due to a hit in the L2.
	 * @param missPenalty Number of clock cycles of delay due to a miss in the L2.
	 */
	public Memory(int cache_size, int associativity, int block_size, boolean protocolIsMSI, int hitPenalty, int missPenalty, int numberOfBanks)
	{
		super(cache_size, associativity, block_size, protocolIsMSI);
		this.hitPenalty = hitPenalty;
		this.missPenalty = missPenalty;
		this.numberOfBanks = numberOfBanks;
		this.bankFreeAtCycle = new int[this.numberOfBanks];
		this.addressesBeingRetrieved = new ArrayList<List<Message>>();
		for(int i = 0; i < this.numberOfBanks; i++)
		{
			this.addressesBeingRetrieved.add(new LinkedList<Message>());
		}
//		this.bankQueues = new ArrayList<Queue<Message>>();
//		for(int i = 0; i < this.numberOfBanks; i++)
//		{
//			this.bankQueues.add(new LinkedList<Message>());
//		}
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
		this.outGoingMessage = null;
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
		this.referenceMessage = null;
		return message;
	}
	
	/**
	 * {@inheritDoc}
	 * NOTE: this should be the last person to receive the message.
	 * If the message was a WANT_TO_READ or WANT_TO_WRITE message, and it was handled by another cache, DO NOT SEND THE MESSAGE HERE
	 */
	@Override
	public boolean setAndProcessIncomingMessage(Message message, int currentCycleTime)
	{
		//update bank queues
		this.updateBankQueues(currentCycleTime);
		//we have to handle it differently
		int index = (int)(message.memoryAddress / blockSize) % this.cache.length;
		//TODO check if this is correct
		int bankNumber = index % this.numberOfBanks;
		int associativityIndex = -1;
		//TODO need to account for block size
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != message.memoryAddress - (message.memoryAddress % this.blockSize); associativityIndex++);
		
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
					this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
				}
				break;
			case INVALIDATE:
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return true;
				}
				this.state[index][associativityIndex].busInvalidate();
				//invalidate it if it exists
				break;
			case WANT_TO_READ:
				//address not found, need to get it from "memory"
				if(this.addressInQueue(bankNumber, message.memoryAddress))
				{
					if(currentCycleTime < this.bankFreeAtCycle[bankNumber])
					{
						currentCycleTime = this.bankFreeAtCycle[bankNumber];
					}
					this.bankFreeAtCycle[bankNumber] = currentCycleTime + this.hitPenalty;
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, this.bankFreeAtCycle[bankNumber]);
					return false;
				}
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
					this.cache[index][associativityIndex] = message.memoryAddress - (message.memoryAddress % this.blockSize);
					this.addressesBeingRetrieved.get(bankNumber).add(new Message(message, (hit ? this.hitPenalty : this.missPenalty)));
					this.state[index][associativityIndex].processorExclusiveRead();
				}
				
				if(currentCycleTime < this.bankFreeAtCycle[bankNumber])
				{
					currentCycleTime = this.bankFreeAtCycle[bankNumber];
				}
				this.bankFreeAtCycle[bankNumber] = currentCycleTime + (hit ? this.hitPenalty : this.missPenalty);
				this.leastRecentlyUsedCycle[index][associativityIndex] = this.bankFreeAtCycle[bankNumber];
				
				//send return message if you have it
				if(this.state[index][associativityIndex].isExclusive())
				{
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, MessageType.RETURNING_EXCLUSIVE, this.bankFreeAtCycle[bankNumber]);
				}
				else
				{
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, this.bankFreeAtCycle[bankNumber]);
				}
				
				//make it shared once you have it
				this.state[index][associativityIndex].busRead();
				return hit;
			case WANT_TO_WRITE:
				//address not found, need to get it from "memory"
				if(this.addressInQueue(bankNumber, message.memoryAddress))
				{
					if(currentCycleTime < this.bankFreeAtCycle[bankNumber])
					{
						currentCycleTime = this.bankFreeAtCycle[bankNumber];
					}
					this.bankFreeAtCycle[bankNumber] = currentCycleTime + (hit ? this.hitPenalty : this.missPenalty);
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, this.bankFreeAtCycle[bankNumber]);
					return false;
				}
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
					this.cache[index][associativityIndex] = message.memoryAddress - (message.memoryAddress % this.blockSize);
					this.addressesBeingRetrieved.get(bankNumber).add(new Message(message, (hit ? this.hitPenalty : this.missPenalty)));
					this.state[index][associativityIndex].processorRead();
				}
				//make it invalid if you have it
				this.state[index][associativityIndex].busWrite();
				
				if(currentCycleTime < this.bankFreeAtCycle[bankNumber])
				{
					currentCycleTime = this.bankFreeAtCycle[bankNumber];
				}
				this.bankFreeAtCycle[bankNumber] = currentCycleTime + (hit ? this.hitPenalty : this.missPenalty);
				this.leastRecentlyUsedCycle[index][associativityIndex] = this.bankFreeAtCycle[bankNumber];
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, this.bankFreeAtCycle[bankNumber]);
				return hit;
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
				this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
		return true;
	}
	
	private boolean addressInQueue(int bankNumber, long memoryAddress)
	{
		List<Message> bankQueue = this.addressesBeingRetrieved.get(bankNumber);
		memoryAddress -= (memoryAddress % this.blockSize);
		for(int i = 0; i < bankQueue.size(); i++)
		{
			if(memoryAddress == (bankQueue.get(i).memoryAddress-(bankQueue.get(i).memoryAddress%this.blockSize)))
			{
				return true;
			}
		}
		return false;
	}
	
	private void updateBankQueues(int currentCycleTime)
	{
		for(int i = 0; i < this.numberOfBanks; i++)
		{
			List<Message> bankQueue = this.addressesBeingRetrieved.get(i);
			ArrayList<Message> toRemove = new ArrayList<Message>();
			for(int j = 0; j < bankQueue.size(); j++)
			{
				if(bankQueue.get(j).issueCycleTime < currentCycleTime)
				{
					toRemove.add(bankQueue.get(j));
				}
			}
			bankQueue.removeAll(toRemove);
		}
	}
}
