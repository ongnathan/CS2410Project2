package protocol;

//Every memory location must have one of these to keep the state
public class State
{
	private final boolean usesMSI;
	private CoherenceProtocolState state;

	public State(boolean usesMSI)
	{
		this.usesMSI = usesMSI;
		if(this.usesMSI)
		{
			this.state = MSI.INVALID;
		}
		else
		{
			this.state = MESI.INVALID;
		}
	}
	
	public CoherenceProtocolState getState()
	{
		return this.state;
	}
	
	public void processorRead()
	{
		if(this.usesMSI)
		{
			switch((MSI)this.state)
			{
				case INVALID:
					this.state = MSI.SHARED;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
		else
		{
			switch((MESI)this.state)
			{
				case EXCLUSIVE:
					//do nothing
					break;
				case INVALID:
					//FIXME depends on exclusivity
					this.state = MESI.SHARED;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
	}
	
	public void processorExclusiveRead()
	{
		if(this.usesMSI)
		{
			switch((MSI)this.state)
			{
				case INVALID:
					this.state = MSI.SHARED;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
		else
		{
			switch((MESI)this.state)
			{
				case EXCLUSIVE:
					//do nothing
					break;
				case INVALID:
					//FIXME depends on exclusivity
					this.state = MESI.EXCLUSIVE;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
	}
	
	public void processorWrite()
	{
		if(this.usesMSI)
		{
			switch((MSI)this.state)
			{
				case INVALID:
					this.state = MSI.MODIFIED;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					this.state = MSI.MODIFIED;
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
		else
		{
			switch((MESI)this.state)
			{
				case EXCLUSIVE:
					this.state = MESI.MODIFIED;
					break;
				case INVALID:
					this.state = MESI.MODIFIED;
					break;
				case MODIFIED:
					//do nothing
					break;
				case SHARED:
					this.state = MESI.MODIFIED;
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
	}
	
	public void processorEvict()
	{
		this.busInvalidate();
	}
	
	public void busRead()
	{
		if(this.usesMSI)
		{
			switch((MSI)this.state)
			{
				case INVALID:
					//do nothing
					break;
				case MODIFIED:
					//FIXME need to flush
					this.state = MSI.SHARED;
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
		else
		{
			switch((MESI)this.state)
			{
				case EXCLUSIVE:
					this.state = MESI.SHARED;
					break;
				case INVALID:
					//do nothing
					break;
				case MODIFIED:
					//FIXME need to flush
					this.state = MESI.SHARED;
					break;
				case SHARED:
					//do nothing
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
	}
	
	public void busWrite()
	{
		this.busInvalidate();
//		if(this.usesMSI)
//		{
//			switch((MSI)this.state)
//			{
//				case INVALID:
//					//do nothing
//					break;
//				case MODIFIED:
//					//FIXME need to flush
//					this.state = MSI.INVALID;
//					break;
//				case SHARED:
//					this.state = MSI.INVALID;
//					break;
//				default:
//					throw new UnsupportedOperationException("State not handled");
//			}
//		}
//		else
//		{
//			switch((MESI)this.state)
//			{
//				case EXCLUSIVE:
//					this.state = MESI.INVALID;
//					break;
//				case INVALID:
//					//do nothing
//					break;
//				case MODIFIED:
//					//FIXME need to flush
//					this.state = MESI.INVALID;
//					break;
//				case SHARED:
//					this.state = MESI.INVALID;
//					break;
//				default:
//					throw new UnsupportedOperationException("State not handled");
//			}
//		}
	}
	
	public void busReadWrite()
	{
		this.busInvalidate();
//		if(this.usesMSI)
//		{
//			switch((MSI)this.state)
//			{
//				case INVALID:
//					//do nothing
//					break;
//				case MODIFIED:
//					//FIXME need to flush
//					this.state = MSI.INVALID;
//					break;
//				case SHARED:
//					this.state = MSI.INVALID;
//					break;
//				default:
//					throw new UnsupportedOperationException("State not handled");
//			}
//		}
//		else
//		{
//			switch((MESI)this.state)
//			{
//				case EXCLUSIVE:
//					this.state = MESI.INVALID;
//					break;
//				case INVALID:
//					//do nothing
//					break;
//				case MODIFIED:
//					//FIXME need to flush
//					this.state = MESI.INVALID;
//					break;
//				case SHARED:
//					this.state = MESI.INVALID;
//					break;
//				default:
//					throw new UnsupportedOperationException("State not handled");
//			}
//		}
	}
	
	public void busInvalidate()
	{
		if(this.usesMSI)
		{
			switch((MSI)this.state)
			{
				case INVALID:
					//do nothing
					break;
				case MODIFIED:
					//FIXME need to flush
					this.state = MSI.INVALID;
					break;
				case SHARED:
					this.state = MSI.INVALID;
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
		else
		{
			switch((MESI)this.state)
			{
				case EXCLUSIVE:
					this.state = MESI.INVALID;
					break;
				case INVALID:
					//do nothing
					break;
				case MODIFIED:
					//FIXME need to flush
					this.state = MESI.INVALID;
					break;
				case SHARED:
					this.state = MESI.INVALID;
					break;
				default:
					throw new UnsupportedOperationException("State not handled");
			}
		}
	}
	
	public boolean isInvalid()
	{
		return this.state == MSI.INVALID || this.state == MESI.INVALID;
	}
	
	public boolean isExclusive()
	{
		return this.state == MSI.MODIFIED || this.state == MESI.MODIFIED || this.state == MESI.EXCLUSIVE;
	}
	
	public String toString()
	{
		if(this.state == MSI.MODIFIED || this.state == MESI.MODIFIED)
			return "Modified";
		else if(this.state == MSI.INVALID || this.state == MESI.INVALID)
			return "Invalid";
		else if(this.state == MESI.EXCLUSIVE)
			return "Exclusive";
		else if(this.state == MSI.MODIFIED || this.state == MESI.MODIFIED)
			return "Modified";
		else if(this.state == MSI.SHARED || this.state == MESI.SHARED)
			return "Shared";
		else
			return "";
	}
	public boolean isModified()
	{
		return this.state == MSI.MODIFIED || this.state == MESI.MODIFIED;
	}
}
