// class used for storing router table entries and values

public class RouterEntry {
	private int routerID;
	private int circuitID;

	public RouterEntry(int routerID, int circuitID) {
		this.routerID = routerID;
		this.circuitID = circuitID;
	}

	public int getRouterID() {
		return this.routerID;
	}

	public int getCircuitID() {
		return this.circuitID;
	}

	@Override
	public boolean equals(Object obj) {
	    if (obj == null || 
	    		!RouterEntry.class.isAssignableFrom(obj.getClass())) {
	        return false;
	    }
		final RouterEntry other = (RouterEntry) obj;
	    if (routerID.equals(other.getRouterID()) && circuitID.equals(other.getCircuitID())) {
	    	return true;
	    }
	    return false;
	}

	@Override
    public int hashCode() {
        int hash = 1610612741;
        hash = (37 * hash) + this.getRouterID();
        hash = (37 * hash) + this.getCircuitID();
        return hash;
    }
}