package src;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.Socket;

// class to hold router information, passed on to proxy and router threads from tor main
public class RouterInfo {
	private final int groupNumber;
	private final int instanceNumber;
	private final int agentID;
	private final int port;

	// next available stream ID
	private int nextStreamID;

	// router ID to next available circuit ID
	private ConcurrentMap<Integer, Integer> nextEvenCircuitID;
	private ConcurrentMap<Integer, Integer> nextOddCircuitID;

	// socket reader on router side puts cell into buffer for proxy reader to get
	private ConcurrentMap<Integer, ConcurrentLinkedQueue<byte[]>> streamIDToBuffer;

	// router ID's to sockets
	private ConcurrentMap<Integer, Socket> routerIDToSocket;

	// routing table entries of (routerID, circuitID) tuples
	private ConcurrentMap<RouterEntry, RouterEntry> routingTable;

    public RouterInfo(int groupNumber, int instanceNumber, int port) {
    	this.groupNumber = groupNumber;
    	this.instanceNumber = instanceNumber;
    	this.agentID = (groupNumber << 16) | instanceNumber;
    	this.port = port;
    	this.nextStreamID = 1;
    	this.nextEvenCircuitID = new ConcurrentHashMap<Integer, Integer>();
    	this.nextOddCircuitID = new ConcurrentHashMap<Integer, Integer>();
    	this.streamIDToBuffer = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<byte[]>>();
    	this.routerIDToSocket = new ConcurrentHashMap<Integer, Socket>();
    	this.routingTable = new ConcurrentHashMap<RouterEntry, RouterEntry>();
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

    // Returns next available stream ID in a thread safe manner
    public synchronized int getNexStreamID() {
    	int ret = nextStreamID;
    	nextStreamID++;
    	return ret;
    }

    // Returns next available even circuit ID given the next routerID
    public synchronized int getNextEvenCircuitID(int routerID) {
    	if (!nextEvenCircuitID.containsKey(routerID) {
    		nextEvenCircuitID.put(routerID, 2);
    	}
    	int ret = nextEvenCircuitID.get(routerID);
    	nextEvenCircuitID.put(routerID, ret + 2);
    	return ret;
    }

    // Returns next available even circuit ID given the next routerID
    public synchronized int getNextOddCircuitID(int routerID) {
    	if (!nextOddCircuitID.containsKey(routerID) {
    		nextOddCircuitID.put(routerID, 1);
    	}
    	int ret = nextOddCircuitID.get(routerID);
    	nextOddCircuitID.put(routerID, ret + 2);
    	return ret;
    }


}