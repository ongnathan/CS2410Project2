// this could be cache or L2

public class Memory extends Cache {

	public Memory(int cache_size, int associativity, int block_size, boolean protocolIsMSI)
	{
		super(cache_size, associativity, block_size, protocolIsMSI);
		// TODO Auto-generated constructor stub
	}

	static int MEMORYSIZE; // memory or L2 size
	int[] data;

	public Object findBlock(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	public int read(int tag, int block_offset) {
		// TODO Auto-generated method stub
		return 0;
	}

}
