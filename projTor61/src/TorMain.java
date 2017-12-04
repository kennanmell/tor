package src;

import regagent.RegAgentThread;
import proxy.ProxyThread;

// constant for max hops (3)

  // field: group number
  // field: instance number
  //
  // field: ConcurrentHashMap of stream IDs to Concurrent
  //        Queues (passed on to RouterThread and ProxyThread)
  // field: map of router id to socket to check if TCP connection exists
    // Routing table Map<Long, Integer> map from circuit ID combined with
    //        router ID bit shift, to next router ID
    // field: map from router ID to even nums
    // field: map from router ID to odd nums
    // field: next available stream ID


  // spawn 2 threads: RouterThread, ProxyThread (will assign stream IDs to each HTTPRequestThread)
  // check that there is only one tcp connection beteween this TorNode and another

    // 1. register self router using registration service
    // 2. On circuit creation, pick next router at random
    //


public class TorMain {

  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("usage: ./run <group number> <instance number> <HTTP Proxy port>");
      return;
    }

    int groupNo;
    int instanceNo;
    int iport;
    try {
      groupNo = Integer.parseInt(args[0]);
      instanceNo = Integer.parseInt(args[1]);
      iport = Integer.parseInt(args[2]);
    } catch (NumberFormatException e) {
      System.out.println("usage: ./run <group number> <instance number> <HTTP Proxy port>");
      return;
    }

    // TODO: create server for tor and use that port
    int tempPort = 5203;
    final int agentId = (groupNo << 16) | instanceNo; // router number
    RegAgentThread regThread = new RegAgentThread(groupNo, instanceNo, agentId, tempPort); // TODO: use Service class instead?
    regThread.start();
    RouterInfo routerInfo = new RouterInfo(groupNo, instanceNo, tempPort);
    // Thread for initializing self circuit
    CircuitInitThread circuitInitThread = new CircuitInitThread(this.routerInfo, this.regThread);
    circuitInitThread.start();
    // Thread for handling Tor side requests
    RouterThread routerThread = new RouterThread(routerInfo, regThread);
    routerThread.start();

    // circuit needs to be initialized with gatewayentry in routerInfo set for proxy thread to send on the self circuit
    circuitInitThread.join();
    (new ProxyThread(iport)).start();
  }
}
