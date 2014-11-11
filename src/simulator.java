import java.util.*;
import java.io.*;
import memory.*;
import processor.*;
import snoopingBus.*;
public class simulator
{
	static int P,n1,n2,k,a1,a2,B,d2,dm,s;
	static boolean debug;
	static ArrayList<instruction> requests; //holds instructions by processor
	static HashMap<Message,Integer> whichCore; //tells which core the message came from
	static instruction [] currentRequests;
	static ArrayList<String> instructionList; //holds each instruction by ID #
	static ArrayList<iList> instructionsLeft; //when this is empty then we stop
	static ArrayList<Cache> caches;
	static ArrayList<Message> bus;
	static ArrayList<instruction> removeThisCycle;
	static Memory memory;
	static Message currentMessage;
	static int [] timeDone;
	static int [] coreDelay;
	static int [] timeMiss;
	static int [] numMiss;
	public static boolean allCoresDone()
	{
		for(iList i : instructionsLeft)
		{
			if(!i.isEmpty())
				return false;
		}
		return true;
	}
	public static void checkIfCoresDone(int time)
	{
		for(int i = 0; i < instructionsLeft.size();i++)
		{
			if(timeDone[i] <= 0 && instructionsLeft.get(i).isEmpty())
			{
				timeDone[i] = time;
			}
		}
	}
	public static void main(String [] args) throws Exception
	{
		instructionList = new ArrayList<String>();
		instructionsLeft = new ArrayList<iList>();
		removeThisCycle = new ArrayList<instruction>();
		whichCore = new HashMap<Message,Integer>();
		bus = new ArrayList<Message>();
		File file = new File(args[0]);
		Scanner input = new Scanner(file);
		debug = false;
		loadParameters(input);
		coreDelay = new int[P];
		timeMiss = new int [P];
		numMiss = new int [P];
		for(int j =0; j < P; j++)
		{
			instructionsLeft.add(new iList());
		}
		timeDone = new int[P];
		for(int i = 0; i < P; i++)
			timeDone[i] = -1;
		requests = new ArrayList<instruction>();
		
		loadTrace(args[1]);
		caches = new ArrayList<Cache>(P);
		currentRequests = new instruction [P];
		for(int i = 0; i < P; i++)
		{
			Cache curr;
			if(s==0)
			{
				 curr = new Cache((int)Math.pow(2,n1-10),(int)Math.pow(2,a1),(int)Math.pow(2,k),true);
			}
			else
			{
				 curr = new Cache((int)Math.pow(2,n1-10),(int)Math.pow(2,a1),(int)Math.pow(2,k),false);
			}
			caches.add(curr);
		}
		if(s==0)
			memory = new Memory((int)Math.pow(2,n2-10),(int)Math.pow(2,a2),(int)Math.pow(2,k),true,d2,dm,B);
		else
			memory = new Memory((int)Math.pow(2,n2-10),(int)Math.pow(2,a2),(int)Math.pow(2,k),false,d2,dm,B);

		
		//Need to add functionality to free up array if the core is now free as a result of this message execution!!
		//Need to add functionality to increase cycle time of next instructions of the core as a result of execution delay! DONE
		int cycle = 1;
		int numMessages = 0;
		while(!allCoresDone() || !bus.isEmpty())
		{
			//Thread.sleep(1000);
			if(debug && k < 4)
				System.out.println("Debugging Information for cycle: " + cycle + "\n\n-----------------------------------\n\n");
			//for each core that has a non null entry in the currentRequests array, add one to every following instructions
			boolean satisfied = false;
			int count = 0;
			Message currentMessage = null;
			while(!satisfied && count < bus.size()) //keep getting messages in case it's too early to remove the current message
			{
				satisfied = true;
				currentMessage = bus.get(count); 
				//remove if the time has come
				//get the current message and send it out to the processors!
				//check if there's an instruction to issue at the current cycle
				//issue the instruction for each core that has an instruction to issue only if that core isn't already busy
				//check if there's an outgoing message for any processor, if so send that message to another processor
				//if an instruction finishes executing, be sure to update the next instruction from that core(c2-c1 cycletime)
				if(currentMessage.type==MessageType.WANT_TO_READ || currentMessage.type == MessageType.WANT_TO_WRITE) //if no other low level cache has tihs block, then go to memory
				{
					boolean found = false;
					for(int i = 0; i < P ; i++)	
					{
						caches.get(i).setAndProcessIncomingMessage(currentMessage,cycle);
						Message response = caches.get(i).getOutgoingMessage();
						if(response!=null) //then we have the block, so we can simply give it to the cache or might need to write back
						{
							if(debug && k < 4)
							{
								System.out.println("Core " + whichCore.get(currentMessage) + " " + currentMessage.type + ", and block was found in cache " + i);
								System.out.println("Sending Response to the bus");
							}
							found = true;
							bus.add(response);
							whichCore.put(response,i);
							numMessages++;
						}
					}
					if(!found) //miss, need to go to memory
					{
						if(debug && k < 4)
						{
							System.out.println("Core " + whichCore.get(currentMessage) + " " + currentMessage.type + " at location " + currentMessage.memoryAddress + " and block was not found (miss) ");
						}
						if(memory.setAndProcessIncomingMessage(currentMessage,cycle))
						{
							if(debug  && k<4)
							{
								System.out.println("Core " + whichCore.get(currentMessage) + " " + currentMessage.type + " at location " + currentMessage.memoryAddress + " and cache hit in memory" );
							}
						}
						else
						{
							if(debug && k<4)
							{
								System.out.println("Core " + whichCore.get(currentMessage) + " " + currentMessage.type + " at location " + currentMessage.memoryAddress + " and cache miss in memory" );
							}
						}
						Message memResponse = memory.getOutgoingMessage();
						if(whichCore.containsKey(currentMessage))
						{
							int finalTime = memResponse.issueCycleTime;
							int currCore = whichCore.get(currentMessage);
							int originalTime = currentMessage.issueCycleTime;
							numMiss[currCore]++;
							timeMiss[currCore] = timeMiss[currCore] + finalTime-originalTime;
						}
						if(debug && k <4)
							System.out.println("Sending response to bus");
						bus.add(memResponse);
						whichCore.put(memResponse,-1);
						numMessages++;
					}
					
				} //Now, need to check ACKNOWLEDGE PREVIOUS, WRITE  BACK, INVALIDATE
				else if(currentMessage.type == MessageType.INVALIDATE)
				{

					int skip = -1; 
					if(whichCore.containsKey(currentMessage))
						skip = whichCore.get(currentMessage);
					if(debug && k <4)
						System.out.println("Invalidating Messages for address " + currentMessage.memoryAddress + " from core " + skip );
					for(int i = 0; i < P ; i++)
					{
						if(i!=skip)
						{
							if(!caches.get(i).setAndProcessIncomingMessage(currentMessage,cycle))
							{
								if(debug && k<4)
									System.out.println("Block: " + currentMessage.memoryAddress + " was invalidated for core " + i);
							}
						}
					}
					if(!memory.setAndProcessIncomingMessage(currentMessage,cycle))
					{
						if(debug && k <4)
							System.out.println("Block: " + currentMessage.memoryAddress + " was invalidated for L2 by core " + whichCore.get(currentMessage));
					}
				}
				else if(currentMessage.type == MessageType.WRITE_BACK)
				{
					if(debug && k < 4)
						System.out.println("Block: " + currentMessage.memoryAddress + " was written back to memory " );
					memory.setAndProcessIncomingMessage(currentMessage,cycle);
				}
				else //message type must be a ACKNOWLEDGE
				{
					if(currentMessage.secondaryType == MessageType.WRITE_BACK) //memory needs to do a write back
					{
						memory.setAndProcessIncomingMessage(currentMessage,cycle);
						if(debug && k < 4)
							System.out.println("Block: " + currentMessage.memoryAddress + " was written back to memory " ); 
					}
					if(currentMessage.issueCycleTime < cycle) //pay the proper penalties
					{
						for(int i = 0; i < P; i++)
						{
							if(caches.get(i).setAndProcessIncomingMessage(currentMessage,cycle)) //this processor can move on
							{
								if(debug && k < 4)
								{
									if(whichCore.get(currentMessage) > -1)
										System.out.println("Core " + i + " acknowledges the previous message from processor " + whichCore.get(currentMessage));
									else
										System.out.println("Core " + i + " acknowledges the previous message from memory");
								}
								//can generate a write back here
								instruction iii = currentRequests[i];
								instructionsLeft.get(iii.coreID).remove(iii);
								removeThisCycle.add(iii);
								currentRequests[i] = null;
								Message now = caches.get(i).getOutgoingMessage();
								if(now!=null)
								{
									System.out.println("And a write back request was generated and placed on the bus " );
									bus.add(now);
									whichCore.put(now,i);
									numMessages++;
								}
								
							}
						}
					}
					else
					{
						System.out.println("weren't ready for the current message, getting the next one");
						satisfied = false;
					}
				}
				if(!satisfied)
					count++;
			}
			//Update issue times if the processor isn't done with the previous request

			//remember to remove the current message from the HashMap
			if(count < bus.size()) //only remove if we actually did something with our message
			{
				whichCore.remove(currentMessage);
				bus.remove(currentMessage);
			}
			if(removeThisCycle.size() >0)
			{
				for(instruction inst: removeThisCycle)
				{
					requests.remove(inst);
				}
				removeThisCycle.clear();
			}
			for(instruction j: requests)
			{
				if(j.cycleTime > cycle)
					break;
				if((j.cycleTime + coreDelay[j.coreID]) == cycle)
				{
					if(currentRequests[j.coreID] !=null) //can't run request now because core is busy
					{
					}
					else
					{
						if(!caches.get(j.coreID).runInstruction(j.processorString(),coreDelay[j.coreID]))
						{
							System.out.println("Should never reach here");
							j.cycleTime++;
						}
						else
						{
							currentRequests[j.coreID] = j;
							if(debug && k < 4)
							{
								if(j.isRead)
									System.out.println("Core: " + j.coreID + " issues a request of read to memory address " + j.address + ", which had original time: " + j.originalCycleTime);
								else
									System.out.println("Core: " + j.coreID + " issues a request of write to memory address " + j.address + ", which had original time: " + j.originalCycleTime);
							}
							Message curr = caches.get(j.coreID).getOutgoingMessage();
							if(curr == null) //then the request is satisfied so we can remove the request and no message need be sent
							{
								if(debug && k < 4)
									System.out.println("This was a hit");
								instructionsLeft.get(j.coreID).remove(j);
								removeThisCycle.add(j);
								currentRequests[j.coreID] = null;
							}
							else if(curr.type == MessageType.INVALIDATE) //write hit, so we done with this, BIG QUESTION (Can we continue while invalidate is on bus)
							{
								if(debug && k<4)
									System.out.println("Write hit: sending invalidate message to the bus");
								instructionsLeft.get(j.coreID).remove(j);
								currentRequests[j.coreID] = null;
								removeThisCycle.add(j);
								bus.add(curr);
								whichCore.put(curr,j.coreID);
								numMessages++;
							}
							else //can't remove request until its message is satisfied by the bus
							{
								if(debug && k < 4)
									System.out.println("Miss: sending request to the bus");
								bus.add(curr);
								whichCore.put(curr,j.coreID);
								numMessages++;
							}
						}
					}
				}
			}
			for(int i = 0; i < P; i++)
			{
				if(currentRequests[i]!=null)
					coreDelay[i]++;
			}
			if(removeThisCycle.size() >0)
			{
				for(instruction inst: removeThisCycle)
				{
					requests.remove(inst);
				}
				removeThisCycle.clear();
			}
				//need to go through all messages on the bus, and for each core that has a message on the bus, we need to increment its next instructions real cycle time by 1
				//policy for choosing message priorty goes by coreID number (0 is highest priority -> P)
			//need to include code for moving the next instructions execution cycle time forward on a cache miss
			checkIfCoresDone(cycle);
			cycle++;
		}
		
		System.out.println("The total number of cycles was: " + cycle);
		for(int i = 0; i < P; i++)
		{
			System.out.println("Core " + i + " finished executing at time " + timeDone[i]);
		}
		int counter = 0;
		int sum = 0;
		int hitSum = 0;
		for(Cache c: caches)
		{
			int total = c.getNumReadHits()+c.getNumWriteHits()+c.getNumReadMiss()+c.getNumWriteMiss();
			int hits = c.getNumReadHits() + c.getNumWriteHits();
			if(total == 0)
				System.out.println("For core " + counter + ", there were no hits or misses " );
			else
				System.out.println("For core " + counter + ", the hit rate was " + hits + "/" + total + " = " + (double)hits/(double)total);
			counter++;
			sum = sum + total;
			hitSum = hitSum + hits;
		}
		System.out.println("The overall hit rate was " + hitSum + "/" + sum + " = " + (double)hitSum/(double)sum);
		System.out.println("\n----------------------------------------------");
		for(int i = 0; i < P ;i++)
		{
			if(numMiss[i]!=0)
				System.out.println("The average miss penalty at core " + i + " is " + timeMiss[i] + "/" + numMiss[i] + " = " + (double)timeMiss[i]/(double)numMiss[i]);
			else
				System.out.println("Core " + i + " did not have any L1 cache misses" );
		}
		System.out.println("\n------------------------------------------------");
		System.out.println(numMessages + " messages were exchanged on the bus.");
		
	}
	public static void loadTrace(String filename) throws Exception
	{
		int counter = 0;
		Scanner inScan = new Scanner(new File(filename));
		while(inScan.hasNextLine())
		{
			String line = inScan.nextLine();
			instructionList.add(line);
			String[] splitLine = line.split("\t");
			int core = Integer.parseInt(splitLine[1]);
			instruction currentAcc = new instruction(counter,Integer.parseInt(splitLine[0]),core,splitLine[3],Integer.parseInt(splitLine[2]));
			requests.add(currentAcc);
			iList curr = instructionsLeft.get(currentAcc.coreID);
			curr.add(currentAcc);
			counter++;
		}
	}
	//loads all of the integer parameters
	public static void loadParameters(Scanner input) throws Exception
	{
		while(input.hasNextLine())
		{
			String line = input.nextLine();
			String[] splitLine = line.split(" ");
			switch(splitLine[0])
			{
				case "P": P = Integer.parseInt(splitLine[1]);
					break;
				case "n1": n1 = Integer.parseInt(splitLine[1]);
					break;
				case "n2": n2 = Integer.parseInt(splitLine[1]);
					break;
				case "k": k = Integer.parseInt(splitLine[1]);
					break;
				case "a1": a1 = Integer.parseInt(splitLine[1]);
					break;
				case "a2": a2 = Integer.parseInt(splitLine[1]);
					break;
				case "B": B = Integer.parseInt(splitLine[1]);
					break;
				case "d2": d2 = Integer.parseInt(splitLine[1]);
					break;
				case "dm": dm = Integer.parseInt(splitLine[1]);
					break;
				case "s" : s = Integer.parseInt(splitLine[1]);
					break;
				case "debug" : debug = true;
			}
		}
	}
}
