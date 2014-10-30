package protocol;

public enum MSI implements CoherenceProtocolState
{
	MODIFIED, SHARED, INVALID;

	@Override
	public boolean doesExclusiveExist()
	{
		return false;
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
		return false;
	}
}
