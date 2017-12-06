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

    TorServerThread torServer = new TorServerThread();
    torServer.start();

    final int agentId = (groupNo << 16) | instanceNo; // router number
    RegAgentThread regThread = new RegAgentThread(groupNo, instanceNo, agentId, torServer.serverSocket.getLocalPort()); // TODO: use Service class instead?
    regThread.start();

    // TODO: make my circuit, uncomment
    //(new ProxyThread(iport,
    //                 routerInfo.getGatewayEntry().getCircuitID(),
    //                 routerInfo.getGatewayEntry().getSocket())).start();
  }
}
