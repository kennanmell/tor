// Refactor HTTPRequest Thread
public class TorNode {
	// constant for max hops (3)
	// field: routing table
	// field: group number
	// field: instance number
	// field: ConcurrentHashMap of circuit number to 
	// field: ConcurrentHashMap of stream IDs to Concurrent Queues (passed on to RouterThread and ProxyThread)
	// spawn 2 threads: RouterThread, ProxyThread (will assign stream IDs to each HTTPRequestThread)
	// check that there is only one tcp connection beteween this TorNode and another
    


}