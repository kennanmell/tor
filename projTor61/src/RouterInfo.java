package src;

import java.net.Socket;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque

// class to hold router information, passed on to WebServer and router threads from tor main.
// Encapsulates the state of the router and connection information.
public class RouterInfo {
	private final int groupNumber;
	private final int instanceNumber;
	private final int agentID;
	private final int port;

	// entry containing circuit ID and socket for own circuit
	private RouterEntry gatewayEntry;
	// router ID to next available circuit ID
	private Map<Integer, Integer> nextEvenCircuitID;
	private Map<Integer, Integer> nextOddCircuitID;
	// router ID's to sockets (Tor router side)
	private Map<Integer, Socket> agentIDToSocket;
    // stream ID's to sockets (End of circuit, Tor to web servers)
    private Map<Integer, Socket> streamIDToSocket;
	// routing table entries of (agentID, circuitID) tuples
	private Map<RouterEntry, RouterEntry> routingTable;
    // Stores buffers for communication between router and tor side
    private Map<Integer, LinkedBlockingDeque<byte[]>> agentIDToBuffer;

    public RouterInfo(int groupNumber, int instanceNumber, int port) {
    	this.groupNumber = groupNumber;
    	this.instanceNumber = instanceNumber;
    	this.agentID = (groupNumber << 16) | instanceNumber;
    	this.port = port;
    	this.gatewayEntry = null;
    	this.nextEvenCircuitID = new HashMap<Integer, Integer>();
    	this.nextOddCircuitID = new HashMap<Integer, Integer>();
    	this.agentIDToSocket = new HashMap<Integer, Socket>();
        this.streamIDToSocket = new HashMap<Integer, Socket>();
    	this.routingTable = new HashMap<RouterEntry, RouterEntry>();
    	this.agentIDToBuffer = new HashMap<Integer, LinkedBlockingDeque<byte[]>>();
    }

    public synchronized LinkedBlockingDeque<byte[]> getTorBuffer(int agentID) {
        return agentIDToBuffer.get(agentID);
    }

    public synchronized LinkedBlockingDeque<byte[]> removeTorBuffer(int agentID) {
        return agentIDToBuffer.remove(agentID);
    }

    public synchronized LinkedBlockingDeque<byte[]> addTorBuffer(int agentID, LinkedBlockingDeque<byte[]> buffer) {
        return agentIDToBuffer.put(agentID, buffer);
    }

    public RouterEntry getGatewayEntry() {
    	return gatewayEntry;
    }

    public void setGatewayEntry(RouterEntry entry) {
    	this.gatewayEntry = entry;
    }

    public int getGroupNumber() {
    	return groupNumber;
    }

    public int getInstanceNumber() {
    	return instanceNumber;
    }

    public int getAgentID() {
    	return agentID;
    }

    public int getPort() {
    	return port;
    }

    // Returns next available even circuit ID given the next agentID. Wraps circuitID to 2 on overflow.
    public synchronized int getNextEvenCircuitID(int agentID) {
    	if (!nextEvenCircuitID.containsKey(agentID)) {
    		nextEvenCircuitID.put(agentID, 2);
    	}
    	int ret = nextEvenCircuitID.get(agentID);
    	if (nextEvenCircuitID.get(agentID) == 65534) {
    		nextEvenCircuitID.put(agentID, 0);
    	}
    	nextEvenCircuitID.put(agentID, nextEvenCircuitID.get(agentID) + 2);
    	return ret;
    }

    // Returns next available odd circuit ID given the next agentID. Wraps circuitID to 1 on overflow.
    public synchronized int getNextOddCircuitID(int agentID) {
    	if (!nextOddCircuitID.containsKey(agentID)) {
    		nextOddCircuitID.put(agentID, 1);
    	}
    	int ret = nextOddCircuitID.get(agentID);
    	if (nextEvenCircuitID.get(agentID) == 65535) {
    		nextEvenCircuitID.put(agentID, -1);
    	}
    	nextOddCircuitID.put(agentID, nextEvenCircuitID.get(agentID) + 2);
    	return ret;
    }

    public synchronized boolean containsRouterSocket(int agentID) {
        return agentIDToSocket.containsKet(agentID);
    }

    // Returns the socket associated with the agentID. Returns null if there was no mapping
    public synchronized Socket getRouterSocket(int agentID) {
    	return agentIDToSocket.get(agentID);
    }

    // Adds the (agentID, socket) to the map and returns the previous socket, or null if there
    // was no mapping for the key.
    public synchronized Socket addRouterSocket(int agentID, Socket socket) {
    	return agentIDToSocket.put(agentID, socket);
    }

    // Removes the agentID (and its corresponding socket) from this map. This method does nothing if the key is not in the map.
    public synchronized Socket removeRouterSocket(int agentID) {
    	return agentIDToSocket.remove(agentID);
    }

    // Returns the number of connected agents.
    public synchronized int numConnectedAgents() {
        return agentIDToSocket.size();
    }

    // Returns the socket associated with the agentID. Returns null if there was no mapping
    public synchronized Socket getWebServerSocket(int streamID) {
        return streamIDToSocket.get(streamID);
    }

    // Adds the (agentID, socket) to the map and returns the previous socket, or null if there
    // was no mapping for the key.
    public synchronized Socket addWebServerSocket(int streamID, Socket socket) {
        return streamIDToSocket.put(streamID, socket);
    }

    // Removes the agentID (and its corresponding socket) from this map. This method does nothing if the key is not in the map.
    public synchronized Socket removeWebServerSocket(int streamID) {
        return streamIDToSocket.remove(streamID);
    }

    // Returns the number of connected agents.
    public synchronized int numRequests() {
        return streamIDToSocket.size();
    }

    public synchronized boolean containsEntry(RouterEntry entry) {
        return routingTable.containsKey(entry);
    }

    // Returns the next RouterEntry (socket, circuitID) to get to the next hop.
    // Returns null if not found.
    public synchronized RouterEntry getEntry(RouterEntry entry) {
    	return routingTable.get(entry);
    }

    // Adds the previous and next RoutingEntry to the routing table. Returns null if there was no mapping for the previous routing entry.
    public synchronized RouterEntry addEntry(RouterEntry previous, RouterEntry next) {
    	return routingTable.put(previous, next);
    }

    // Removes the previous routing entry (and its corresponding next entry) from this map. This method does nothing if the key is not in the map.
    public synchronized RoutingEntry removeEntry(RouterEntry previous) {
    	return routingTable.remove(previous);
    }

    // Returns the number of routing table entries.
    public synchronized int routingTableSize() {
    	return routingTable.size();
    }
}
