package protocol;

public interface CoherenceProtocolState
{
	public boolean doesExclusiveExist();
	public boolean isInvalid();
	public boolean isShared();
	public boolean isModified();
	public boolean isExclusive();
}
