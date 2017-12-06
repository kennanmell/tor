public class TerminalEntry {
	private int circuitID;
	private int streamID;
	
	public TerminalEntry(int circuitID, int streamID) {
		this.ciruitID = circuitID;
		this.streamID = streamID;
	}

	public int getCircuitID() {
		return circuitID;
	}

	public int getStreamID() {
		return streamID;
	}

	@Override
	public boolean equals(Object obj) {
	    if (obj == null ||
	    		!TerminalEntry.class.isAssignableFrom(obj.getClass())) {
	        return false;
	    }
		final TerminalEntry other = (TerminalEntry) obj;
	    if (circuitID  == other.getCircuitID() && streamID == other.getStreamID()) {
	    	return true;
	    }
	    return false;
	}

	@Override
    public int hashCode() {
        int hash = 1610612741;
        hash = (37 * hash) + circuitID;
        hash = (37 * hash) + streamID;
        return hash;
    }
}