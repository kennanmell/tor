package src;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.Socket;

// class to hold router information, passed on to proxy and router threads from tor main
public class RouterInfo {
	public int groupNumber;
	public int instanceNumber;
	public int agentID;
	public int port;

	// next available stream ID
	public int nextStreamID;

	// router ID to next available circuit ID
	public ConcurrentMap<Integer, Integer> nextEvenCircuitID;
	public ConcurrentMap<Integer, Integer> nextOddCircuitID;

	// socket reader on router side puts cell into buffer for proxy reader to get
	public ConcurrentMap<Integer, ConcurrentLinkedQueue<byte[]>> streamIDToBuffer;

	// router ID's to sockets
	public ConcurrentMap<Integer, Socket> routerIDToSocket;

	// routing table entries of (routerID, circuitID) tuples
	public ConcurrentMap<RouterEntry, RouterEntry> routingTable;

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
}