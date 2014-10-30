package protocol;

public enum MESI implements CoherenceProtocolState
{
	MODIFIED, EXCLUSIVE, SHARED, INVALID;

	@Override
	public boolean doesExclusiveExist()
	{
		return true;
	}

	@Override
	public boolean isInvalid()
	{
		return this == INVALID;
	}

	@Override
	public boolean isShared()
	{
		return this == SHARED;
	}

	@Override
	public boolean isModified()
	{
		return this == MODIFIED;
	}

	@Override
	public boolean isExclusive()
	{
		return this == EXCLUSIVE;
	}
}
