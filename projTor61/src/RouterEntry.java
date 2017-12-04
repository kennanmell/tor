package src;
import java.net.Socket;
// class used for storing router table entries and values

public class RouterEntry {
	private Socket socket;
	private int circuitID;

	public RouterEntry(Socket socket, int circuitID) {
		this.socket = socket;
		this.circuitID = circuitID;
	}

	public int getSocket() {
		return this.socket;
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
	    if (socket == other.getSocket()) && (circuitID == other.getCircuitID())) {
	    	return true;
	    }
	    return false;
	}

	@Override
    public int hashCode() {
        int hash = 1610612741;
        hash = (37 * hash) + this.socket.hashCode();
        hash = (37 * hash) + this.getCircuitID();
        return hash;
    }
}