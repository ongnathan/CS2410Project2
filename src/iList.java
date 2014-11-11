import java.util.*;

public class iList
{
	private ArrayList<instruction> list;
	public iList()
	{
		list = new ArrayList<instruction>();
	}
	public boolean isEmpty()
	{
		return list.size() == 0;
	}
	public int size()
	{
		return list.size();
	}
	public void add(instruction t)
	{
		if(list.size() == 0)
		{
			list.add(t);
			return;
		}
		for(int i = list.size()-1; i >= 0;i--)
		{
			if(t.cycleTime > list.get(i).cycleTime)
			{
				list.add(i,t);
				return;
			}
		}
		list.add(0,t);
		return;
	}
	public instruction get(int index)
	{
		return list.get(index);
	}
	
	public instruction remove()
	{
		instruction i = list.remove(0);
		return i;
	}
	public void remove(instruction i)
	{
		list.remove(i);
	}
	public String toString()
	{
		return list.toString();
	}
}
