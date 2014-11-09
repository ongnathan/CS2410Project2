import java.util.Set;

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

	private int evictions; // evicted blocks
	
	private final Processor processor;
	
	private Message message;

//	Memory l2cache; // could be L2 cache or memory; needs to be constructed
//	Set[] sets;
	private final long[][] cache;
	private final State[][] state;
	private final int[][] leastRecentlyUsedCycle;

	public Cache(int cache_size, int associativity, int block_size)
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

//		sets = new Set[cache_size / (associativity * block_size)];
		
		this.processor = new Processor();
		
		int numCacheLines = (cache_size / block_size) / this.associativity;
		this.cache = new long[numCacheLines][this.associativity];
		this.leastRecentlyUsedCycle = new int[numCacheLines][this.associativity];
		for(int i = 0; i < numCacheLines; i++)
		{
			for(int j = 0; j < this.associativity; j++)
			{
				this.cache[i][j] = -1L;
				this.leastRecentlyUsedCycle[i][j] = -1;
			}
		}
		this.state = new State[numCacheLines][this.associativity];
		
		this.message = null;

		// we need to create the cache sets

		// for (int i = 0; i < sets.length; i++)
		// sets[i] = new Set (this.associativity, this.block_size);
	}

//	public int read(int address)
//	{
//		// if the block is not in the cache
//		if (!isInCache(address)) {
//
//			find(address);
//			numReadMiss++;
//		}
//
//		// calculate block set and ask for data
//
//		Set set = sets[(address / block_size) % sets.length];
//		int block_offset = address % block_size;
//		numRead++;
//
//		return ((Memory) set).read(getTag(address), block_offset);
//
//	}
//
//	// block isn't cache, get it
//
//	public void write(int address, int data) {
//
//		if (!isInCache(address)) {
//
//			find(address);
//			numWriteMiss++;
//		}
//
//		// figure out where is the block and ask for the data
//
//		int index = (address / block_size) % sets.length;
//		int blockoffset = address % block_size;
//		Set set = sets[index];
//		numWrite++;
//		// set.write(getTag(address), blockoffset, data );
//
//	}

	// Then write the block with the data we need
	
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
				this.prepareMessage(address, MessageType.WANT_TO_READ);
			}
			else
			{
				//write miss
				this.numWriteMiss++;
				this.prepareMessage(address, MessageType.WANT_TO_WRITE);
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
					this.prepareMessage(address, MessageType.INVALIDATE);
				}
			}
		}
		return true;
		
//		else
//		{
//			this.prepareMessage(address, this.processor.isInstructionWriteCommand() ? MessageType.WANT_TO_WRITE : MessageType.WANT_TO_READ);
//		}
	}
	
	private void prepareMessage(long address, MessageType type)
	{
		this.message = new Message(address, type);
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
		switch(message.type)
		{
			case ACKNOWLEDGED_PREV_MESSAGE:
				//process message
				this.message = null;
				break;
			case INVALIDATE:
				//find address
				//invalidate it if it exists
				break;
			case WANT_TO_READ:
				//find address
				//make it shared if you have it
				//send return message if you have it
				break;
			case WANT_TO_WRITE:
				//find address
				//make it invalid if you have it
				//send return message if you have it
				break;
			default:
				throw new UnsupportedOperationException("Message Type is not handled.");
		}
	}

//	private void find(int address) {
//
//		int index = (address / block_size) % sets.length;
//		Set set = sets[index];
//
//	}
//
//	private boolean isInCache(int address)
//	{
//
//		int index = (address / block_size) % sets.length;
//		return ((Memory) sets[index]).findBlock(getTag(address)) != null; // this method should call the memory method but we still don't have memory
//
//	}
//
//	private int getTag(int address) {
//
//		return (address / (sets.length * block_size));
//	}

	// private void flush(){
	// for(int i = 0; i < sets.length; i++)
	// {
	// }
	// }

	// private void writback(Block b, int in){
	// we need method here to write write back the data
	// }

}