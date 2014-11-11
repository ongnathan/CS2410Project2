package memory;
import processor.*;
import protocol.State;
import snoopingBus.Message;
import snoopingBus.MessageType;

//TODO handle block sizes
public class Cache {

	public final int cacheSize;	//in KB
	public final int associativity; // which is the number of blocks in each set
	public final int blockSize; 	//in B, the size of block which will be the same across all caches K=2^k bytes
	
	private int numWriteHit;
	private int numReadHit;
	private int numWriteMiss;
	private int numReadMiss;

	private int numEvictions; // evicted blocks
	
	private final Processor processor;
	
	protected Message outGoingMessage;
	protected Message referenceMessage;

	protected final long[][] cache;
	protected final State[][] state;
	protected final int[][] leastRecentlyUsedCycle;
	
	/**
	 * The constructor.
	 * @param cacheSize How big is the cache in KB?
	 * @param associativity What is the associativity factor of the cache?
	 * @param blockSize What is the block size in B?
	 * @param protocolIsMSI Is the protocol MSI or not?  (If it isn't, it's MESI)
	 */
	public Cache(int cacheSize, int associativity, int blockSize, boolean protocolIsMSI)
	{
		// initialize counters
		this.numWriteHit = 0;
		this.numReadHit = 0;
		this.numWriteMiss = 0;
		this.numReadMiss = 0;
		// Eviction_num=0;

		this.cacheSize = cacheSize; // we need to specify the initial values of the cache in KB
		this.associativity = associativity;
		this.blockSize = blockSize; // here too in byte
		this.processor = new Processor();
		
		int numCacheLines = (cacheSize * 1024 / blockSize) / this.associativity;
		this.cache = new long[numCacheLines][this.associativity];
		this.leastRecentlyUsedCycle = new int[numCacheLines][this.associativity];
		this.state = new State[numCacheLines][this.associativity];
		for(int i = 0; i < numCacheLines; i++)
		{
			for(int j = 0; j < this.associativity; j++)
			{
				this.cache[i][j] = -1L;
				this.leastRecentlyUsedCycle[i][j] = -1;
				this.state[i][j] = new State(protocolIsMSI);
			}
		}
		
		this.outGoingMessage = null;
		this.referenceMessage = null;
	}
	
	/**
	 * Prepares a message for the bus.
	 * (Protected method, so no need to worry about this one).
	 * @param address The address you are referencing.
	 * @param type What type of message is it?  See the MessageType class.
	 * @param secondaryType Is there another type?  Usually this is only for WRITE_BACK.
	 * @param issueTime When was this message issued?
	 */
	protected void prepareMessage(long address, MessageType type, MessageType secondaryType, int issueTime)
	{
		if(secondaryType != MessageType.WRITE_BACK && secondaryType != MessageType.RETURNING_EXCLUSIVE && secondaryType != null)
		{
			throw new UnsupportedOperationException("Secondary Message type must be a write back");
		}
//		if(type == MessageType.ACKNOWLEDGED_PREV_MESSAGE)
//		{
//			this.message = new Message(address, type, issueTime+1); //FIXME no delay?
//		}
		this.outGoingMessage = new Message(address, type, issueTime);
	}
	
	/**
	 * Prepares a message for the bus.
	 * (Protected method, so no need to worry about this one).
	 * @param address The address you are referencing.
	 * @param type What type of message is it?  See the MessageType class.
	 * @param issueTime When was this message issued?
	 */
	protected void prepareMessage(long address, MessageType type, int issueTime)
	{
		this.prepareMessage(address, type, null, issueTime);
	}
	
	//LRU
	/**
	 * Uses the Least Recently Used method to evict a block from cache.
	 * (Protected method, so no need to worry about this one).
	 * @param address The address you want to REPLACE the evicted block.
	 * @param index The index you want to evict from.
	 * @return Returns the associativity index of the block that was evicted.
	 */
	protected int evict(long address, int index)
	{
		this.numEvictions++;
		int minCycleTime = Integer.MAX_VALUE;
		int associativityIndexToEvict = -1;
		for(int j = 0; j < this.associativity; j++)
		{
			if(this.leastRecentlyUsedCycle[index][j] < minCycleTime)
			{
				minCycleTime = this.leastRecentlyUsedCycle[index][j];
				associativityIndexToEvict = j;
			}
		}
		
		//prepare writeback message if the thing is modified
		if(this.state[index][associativityIndexToEvict].isModified())
		{
			this.prepareMessage(address, MessageType.WRITE_BACK, 0);
		}
		
		this.cache[index][associativityIndexToEvict] = address - (address % this.blockSize);
		return associativityIndexToEvict;
	}
	
	/**
	 * Gets the number of read hits in this cache.
	 * @return Returns the number of read hits.
	 */
	public int getNumReadHits()
	{
		return this.numReadHit;
	}
	
	/**
	 * Gets the number of write hits in this cache.
	 * @return Returns the number of write hits.
	 */
	public int getNumWriteHits()
	{
		return this.numWriteHit;
	}
	
	/**
	 * Gets the number of read misses in this cache.
	 * @return Returns the number of read misses.
	 */
	public int getNumReadMiss()
	{
		return this.numReadMiss;
	}
	
	/**
	 * Gets the number of write misses in this cache.
	 * @return Returns the number of write misses.
	 */
	public int getNumWriteMiss()
	{
		return this.numWriteMiss;
	}
	
	/**
	 * Gets the total number of reads in this cache.
	 * @return Returns the number of reads.
	 */
	public int getTotalNumReads()
	{
		return this.numReadHit + this.numReadMiss;
	}
	
	/**
	 * Gets the total number of writes in this cache.
	 * @return Returns the number of writes.
	 */
	public int getTotalNumWrites()
	{
		return this.numWriteHit + this.numWriteMiss;
	}
	
	/**
	 * Gets the number of evictions in this cache.
	 * @return Returns the number of evictions.
	 */
	public int getNumEvictions()
	{
		return this.numEvictions;
	}
	
	/**
	 * Runs an instruction from file.  All you need to do is pass in the one-line instruction at the correct time.
	 * This method does not account for correct cycle time.
	 * @param instruction One line of the instruction from the file, delimited by tabs.
	 * @param delaySinceIssuing The number of cycles that have passed since the reported instruction time of execution
	 * @return Returns whether or not running the instruction was successful.  Returns false if the instruction is meant for a different core or if the core is waiting for a return message from the bus.
	 */
	public boolean runInstruction(String instruction, int delaySinceIssuing)
	{
		if(this.referenceMessage != null || this.outGoingMessage != null || !this.processor.parseInstruction(instruction))
		{
			return false;
		}
		
		//find the place in cache
		long address = this.processor.getInstructionAddress();
		int index = (int)(address / this.blockSize) % this.cache.length;
		int associativityIndex = -1;
		//TODO need to account for block size
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != address - (address % this.blockSize); associativityIndex++);
		
		if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
		{
			//didn't find it or is invalid, cache miss
			if(!this.processor.isInstructionWriteCommand())
			{
				//read miss
				this.numReadMiss++;
				this.prepareMessage(address, MessageType.WANT_TO_READ, this.processor.getInstructionCycleNumber()+delaySinceIssuing);
			}
			else
			{
				//write miss
				this.numWriteMiss++;
				this.prepareMessage(address, MessageType.WANT_TO_WRITE, this.processor.getInstructionCycleNumber()+delaySinceIssuing);
			}
		}
		else
		{
			//cache hit
			this.leastRecentlyUsedCycle[index][associativityIndex] = this.processor.getInstructionCycleNumber()+delaySinceIssuing;
			
			if(!this.processor.isInstructionWriteCommand())
			{
				//read hit
				this.numReadHit++;
				this.state[index][associativityIndex].processorRead();
				return true;
			}
			else
			{
				//write hit
				this.numWriteHit++;
				
				//write hit but need to invalidate everyone else
				if(!this.state[index][associativityIndex].isExclusive())
				{
					this.prepareMessage(address, MessageType.INVALIDATE, this.processor.getInstructionCycleNumber()+delaySinceIssuing);
				}
				this.state[index][associativityIndex].processorWrite();
			}
		}
		return true;
	}
	
	/**
	 * The bus should call this method to get the message that needs to be sent elsewhere via the bus.
	 * @return Returns the Message that needs to be sent.  Returns null if no message needs to be sent.
	 */
	public Message getOutgoingMessage()
	{
		Message message = this.outGoingMessage;
		if(message == null)
			return null;
		if(this.outGoingMessage.type == MessageType.WANT_TO_READ || this.outGoingMessage.type == MessageType.WANT_TO_WRITE)
		{
			this.referenceMessage = message;
		}
		this.outGoingMessage = null;
		return message;
	}
	
	//made a change to return boolean!
	/**
	 * The bus should call this method to give any message to this processor.
	 * @param message The message that needs to be read by this processor.
	 * @param currentCycleTime The cycle time of the incoming message.
	 */
	public boolean setAndProcessIncomingMessage(Message message, int currentCycleTime)
	{
		boolean found = true;
		//find location in cache
		int index = (int)(message.memoryAddress / blockSize) % this.cache.length;
		int associativityIndex = -1;
		//TODO need to account for block size
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != message.memoryAddress - (message.memoryAddress % this.blockSize); associativityIndex++);
		
		switch(message.type)
		{
			//if we are expecting this, then we are adding a new address to the cache
			case ACKNOWLEDGED_PREV_MESSAGE:
				//process message
				if(this.referenceMessage == null)
					return false;
				if(message.memoryAddress != this.referenceMessage.memoryAddress)
				{
					found = false;
					//not pertinent information
					break;
				}
				boolean emptySpaceFound = false;
				for(int j = 0; j < this.associativity; j++)
				{
					if(this.cache[index][j] == -1L)
					{
						this.cache[index][j] = message.memoryAddress - (message.memoryAddress % this.blockSize);
						if(this.referenceMessage.type == MessageType.WANT_TO_READ)
						{
							if(message.secondaryType == MessageType.RETURNING_EXCLUSIVE)
							{
								this.state[index][j].processorExclusiveRead();
							}
							else
							{
								this.state[index][j].processorRead();
							}
						}
						else if(this.referenceMessage.type == MessageType.WANT_TO_WRITE)
						{
							this.state[index][j].processorWrite();
						}
						this.leastRecentlyUsedCycle[index][j] = currentCycleTime;
						emptySpaceFound = true;
					}
				}
				//need to evict if no empty space available
				if(!emptySpaceFound)
				{
					associativityIndex = this.evict(message.memoryAddress, index);
					
					this.state[index][associativityIndex].busInvalidate();
					if(this.referenceMessage.type == MessageType.WANT_TO_READ)
					{
						if(message.secondaryType == MessageType.RETURNING_EXCLUSIVE)
						{
							this.state[index][associativityIndex].processorExclusiveRead();
						}
						else
						{
							this.state[index][associativityIndex].processorRead();
						}
					}
					else if(this.referenceMessage.type == MessageType.WANT_TO_WRITE)
					{
						this.state[index][associativityIndex].processorWrite();
					}
					this.leastRecentlyUsedCycle[index][associativityIndex] = currentCycleTime;
				}
				this.referenceMessage = null;
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
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return true;
				}
				
				//send return message if you have it
				if(this.state[index][associativityIndex].isModified())
				{
					//have to do a write back too
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, MessageType.WRITE_BACK, currentCycleTime);
				}
				else
				{
					//send only return message
					this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime);
				}
				
				//make it shared if you have it
				this.state[index][associativityIndex].busRead();
				
				break;
			case WANT_TO_WRITE:
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return true;
				}
				
				//no need a write back here because the next guy is going to have it and it will be modified on his end.
//				if(this.state[index][associativityIndex].isModified())
//				{
//					
//				}
				//make it invalid if you have it
				this.state[index][associativityIndex].busWrite();
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, currentCycleTime);
				break;
			case WRITE_BACK:
				//ignore
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
		return found;
	}
}
