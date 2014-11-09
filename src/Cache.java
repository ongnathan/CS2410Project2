import processor.Processor;
import protocol.State;
import snoopingBus.Message;
import snoopingBus.MessageType;

public class Cache {

	public final int cache_size;	//in KB
	public final int associativity; // which is the number of blocks in each set
	public final int block_size; 	//in B, the size of block which will be the same across all caches K=2^k bytes
	
	private int numWriteHit;
	private int numReadHit;
	private int numWriteMiss;
	private int numReadMiss;

	private int numEvictions; // evicted blocks
	
	private final Processor processor;
	
	private Message message;

	private final long[][] cache;
	private final State[][] state;
	private final int[][] leastRecentlyUsedCycle;

	public Cache(int cache_size, int associativity, int block_size, boolean protocolIsMSI, int l1MissPenalty, int l2MissPenalty)
	{
		// initialize counters
		this.numWriteHit = 0;
		this.numReadHit = 0;
		this.numWriteMiss = 0;
		this.numReadMiss = 0;
		// Eviction_num=0;

		this.cache_size = cache_size; // we need to specify the initial values of the cache in KB
		this.associativity = associativity;
		this.block_size = block_size; // here too in byte
		
		this.processor = new Processor();
		
		int numCacheLines = (cache_size * 1024 / block_size) / this.associativity;
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
		
		this.message = null;
	}
	
	private void prepareMessage(long address, MessageType type, int extraDelay)
	{
		if(type == MessageType.ACKNOWLEDGED_PREV_MESSAGE)
		{
			this.message = new Message(address, type, extraDelay+1); //FIXME no delay?
		}
		this.message = new Message(address, type, extraDelay);
	}
	
	//LRU
	private int evict(long address, int index)
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
		
		this.cache[index][associativityIndexToEvict] = message.memoryAddress;
		return associativityIndexToEvict;
	}

	public int getNumReadHits()
	{
		return this.numReadHit;
	}
	
	public int getNumWriteHits()
	{
		return this.numWriteHit;
	}
	
	public int getNumReadMiss()
	{
		return this.numReadMiss;
	}
	
	public int getNumWriteMiss()
	{
		return this.numWriteMiss;
	}
	
	public int getTotalNumReads()
	{
		return this.numReadHit + this.numReadMiss;
	}
	
	public int getTotalNumWrites()
	{
		return this.numWriteHit + this.numWriteMiss;
	}
	
	public int getNumEvictions()
	{
		return this.numEvictions;
	}
	
	public boolean runInstruction(String instruction)
	{
		if(this.message != null || !this.processor.parseInstruction(instruction))
		{
			return false;
		}
		
		//find the place in cache
		long address = this.processor.getInstructionAddress();
		int index = (int)(address / block_size) % this.cache.length;
		int associativityIndex = 0;
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != address; associativityIndex++);
		
		if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
		{
			//didn't find it or is invalid, cache miss
			if(!this.processor.isInstructionWriteCommand())
			{
				//read miss
				this.numReadMiss++;
				this.prepareMessage(address, MessageType.WANT_TO_READ, 0);
			}
			else
			{
				//write miss
				this.numWriteMiss++;
				this.prepareMessage(address, MessageType.WANT_TO_WRITE, 0);
			}
		}
		else
		{
			//cache hit
			this.leastRecentlyUsedCycle[index][associativityIndex] = this.processor.getInstructionCycleNumber();
			
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
				this.state[index][associativityIndex].processorWrite();
				
				//write hit but need to invalidate everyone else
				if(!this.state[index][associativityIndex].isExclusive())
				{
					this.prepareMessage(address, MessageType.INVALIDATE, 0);
				}
			}
		}
		return true;
	}
	
	public Message getOutgoingMessage()
	{
		Message message = this.message;
		if(this.message.type == MessageType.INVALIDATE || this.message.type == MessageType.ACKNOWLEDGED_PREV_MESSAGE)
		{
			this.message = null;
		}
		return message;
	}
	
	public void setAndProcessIncomingMessage(Message message)
	{
		//find location in cache
		int index = (int)(message.memoryAddress / block_size) % this.cache.length;
		int associativityIndex = -1;
		for(associativityIndex = 0; associativityIndex < this.associativity && this.cache[index][associativityIndex] != message.memoryAddress; associativityIndex++);
		
		switch(message.type)
		{
			//if we are expecting this, then we are adding a new address to the cache
			case ACKNOWLEDGED_PREV_MESSAGE:
				//process message
				if(message.memoryAddress != this.message.memoryAddress)
				{
					//not pertinent information
					break;
				}
				boolean emptySpaceFound = false;
				for(int j = 0; j < this.associativity; j++)
				{
					if(this.cache[index][j] == -1L)
					{
						this.cache[index][j] = message.memoryAddress;
						if(this.message.type == MessageType.WANT_TO_READ)
						{
							this.state[index][j].processorRead();
						}
						else if(this.message.type == MessageType.WANT_TO_WRITE)
						{
							this.state[index][j].processorWrite();
						}
						this.leastRecentlyUsedCycle[index][j] = this.processor.getInstructionCycleNumber() + message.cycleDelay;
						emptySpaceFound = true;
					}
					this.message = null;
				}
				//need to evict if no empty space available
				if(!emptySpaceFound)
				{
					associativityIndex = this.evict(message.memoryAddress, index);
					
					this.state[index][associativityIndex].busInvalidate();
					if(this.message.type == MessageType.WANT_TO_READ)
					{
						this.state[index][associativityIndex].processorRead();
					}
					else if(this.message.type == MessageType.WANT_TO_WRITE)
					{
						this.state[index][associativityIndex].processorWrite();
					}
					this.leastRecentlyUsedCycle[index][associativityIndex] = this.processor.getInstructionCycleNumber() + message.cycleDelay;
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
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return;
				}
				//make it shared if you have it
				this.state[index][associativityIndex].busRead();
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, message.cycleDelay);
				break;
			case WANT_TO_WRITE:
				//address not found, don't care
				if(associativityIndex == this.associativity || this.state[index][associativityIndex].isInvalid())
				{
					return;
				}
				//make it invalid if you have it
				this.state[index][associativityIndex].busWrite();
				//send return message if you have it
				this.prepareMessage(message.memoryAddress, MessageType.ACKNOWLEDGED_PREV_MESSAGE, message.cycleDelay);
				break;
			case WRITE_BACK:
				//ignore
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
	}
}