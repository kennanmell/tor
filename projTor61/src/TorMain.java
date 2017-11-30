package src;

import regagent.RegAgentThread;
import proxy.ProxyThread;

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

    (new ProxyThread(iport)).start();

    // TODO: create server for tor and use that port
    int tempPort = 5203;
    final int agentId = (groupNo << 16) | instanceNo; // router number
    (new RegAgentThread(groupNo, instanceNo, agentId, tempPort)).start(); // TODO: use Service class instead?
  }
}
