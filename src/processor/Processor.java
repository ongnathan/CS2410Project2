package processor;

/**
 * 
 * @author Nathan Ong
 *
 */
public class Processor
{
	private static int processorNumberCounter = 0;
	public final int processorNumber;
	
	public int cycleNumber;
	private boolean isWriteCommand;
	private long address;
	
	public Processor()
	{
		this.processorNumber = processorNumberCounter;
		this.cycleNumber = -1;
		this.isWriteCommand = false;
		this.address = -1L;
		processorNumberCounter++;
	}
	
	/**
	 * 
	 * @param instruction
	 * @return Returns whether or not the instruction could be parsed.  Returns false if the instruction is not meant for this processor.
	 */
	public boolean parseInstruction(String instruction)
	{
		//TODO check if tabs are the delimiter or not
		String[] tokens = instruction.trim().split("\t");
		if(Integer.parseInt(tokens[1]) != this.processorNumber)
		{
			return false;
		}
		this.cycleNumber = Integer.parseInt(tokens[0]);
		this.isWriteCommand = Integer.parseInt(tokens[2]) == 1;
		//
		this.address = Long.parseLong(tokens[3].substring(2,tokens[3].length()), 16);
		return true;
	}
	
	public int getInstructionCycleNumber()
	{
		return this.cycleNumber;
	}
	
	public boolean isInstructionWriteCommand()
	{
		return this.isWriteCommand;
	}
	
	public long getInstructionAddress()
	{
		return this.address;
	}
}//end class
