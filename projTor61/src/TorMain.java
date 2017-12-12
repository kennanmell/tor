package src;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import regagent.RegAgentThread;
import regagent.Service;
import proxy.ProxyThread;
import java.util.Collections;

/** `TorMain` sets up and runs a tor node. */
public class TorMain {

  public static int agentId;
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

    TorMain.agentId = (groupNo << 16) | instanceNo; // router number
    System.out.println("Welcome to tor61 node " + TorMain.agentId + "!");

    // correct order:
    // 1. make local circuit
    // 2. start tor socket
    // 3. start registration service
    // 4. start proxy (could also be 2)
    TorServerThread torServer = new TorServerThread();
    torServer.start();

    RegAgentThread regThread = new RegAgentThread(groupNo, instanceNo, agentId, torServer.serverSocket.getLocalPort()); // TODO: use Service class instead?
    regThread.start();

    Socket proxyCircuitFirstHopSocket = makeLocalCircuit(regThread.getAllServices());

    (new ProxyThread(iport, 1, proxyCircuitFirstHopSocket)).start();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("shutting down...");
        byte[] destroyCell = new byte[512];
        destroyCell[1] = 1;
        destroyCell[2] = TorCommand.DESTROY.toByte();
        try {
          proxyCircuitFirstHopSocket.getOutputStream().write(destroyCell);
        } catch (IOException e) {
          // no op
        }
        regThread.unregisterService();
        SocketManager.removeAllSockets();
      }
    });
    System.out.println("proxy ready...");
  }

  /** Creates the local circuit for routing browser proxy traffic on this tor node. */
  private static Socket makeLocalCircuit(List<Service> candidates) {
    System.out.println("creating tor circuit...");
    Random r = new Random();
    Socket gatewaySocket = null;
    Service gatewayServiceCandidate = candidates.get(candidates.size() - 1);
    try {
      // Make socket.
      //System.out.println("Main: creating connection with " + gatewayServiceCandidate);
      gatewaySocket = new Socket(gatewayServiceCandidate.ip, gatewayServiceCandidate.iport);
      //System.out.println(gatewaySocket);

      // Write open.
      byte[] openCell = new byte[512];
      openCell[2] = TorCommand.OPEN.toByte();
      openCell[3] = (byte) (TorMain.agentId >> 24);
      openCell[4] = (byte) (TorMain.agentId >> 16);
      openCell[5] = (byte) (TorMain.agentId >> 8);
      openCell[6] = (byte) TorMain.agentId;
      openCell[7] = (byte) (gatewayServiceCandidate.data >> 24); // opened agent id
      openCell[8] = (byte) (gatewayServiceCandidate.data >> 16);
      openCell[9] = (byte) (gatewayServiceCandidate.data >> 8);
      openCell[10] = (byte) gatewayServiceCandidate.data;
      gatewaySocket.getOutputStream().write(openCell);

      // Read opened.
      byte[] openResponse = new byte[512];
      if (gatewaySocket.getInputStream().read(openResponse) != 512 ||
          openResponse[2] != TorCommand.OPENED.toByte()) {
        // TODO: also check opener and opened ID in openResponse.
        System.out.println("failed to initialize tor");
        System.exit(0);
      }

      // Write create.
      byte[] createCell = new byte[512];
      createCell[1] = 1;
      createCell[2] = TorCommand.CREATE.toByte();
      gatewaySocket.getOutputStream().write(createCell);

      // Read created.
      byte[] createResponse = new byte[512];
      if (gatewaySocket.getInputStream().read(createResponse) != 512 ||
          createResponse[2] != TorCommand.CREATED.toByte()) {
        // TODO: also check circuit id in created.
        System.out.println("failed to initialize tor");
        System.exit(0);
      }
    } catch (IOException e) {
      System.out.println("failed to initialize tor");
      System.exit(0);
    }

    // Extend three times.
    int extendSuccesses = 0;
    while (extendSuccesses < 3 && !candidates.isEmpty()) { // todo: can't have empty
      Service extendServiceCandidate = candidates.get(r.nextInt(candidates.size()));
      if (extendSuccesses == 1) {
        extendServiceCandidate = candidates.get(candidates.size() - 1);
      }
      try {
        // Send relay extend.
        byte[] ipStrBytes = (extendServiceCandidate.ip.getHostAddress() +
            ":" + extendServiceCandidate.iport + '\0').getBytes();
        byte[] extendCell = new byte[512];
        extendCell[1] = 1;
        extendCell[2] = TorCommand.RELAY.toByte();
        extendCell[11] = (byte) ((ipStrBytes.length + 4) >> 8);
        extendCell[12] = (byte) (ipStrBytes.length + 4);
        extendCell[13] = RelayCommand.EXTEND.toByte();
        System.arraycopy(ipStrBytes, 0, extendCell, 14, ipStrBytes.length);
        extendCell[14 + ipStrBytes.length] = (byte) (extendServiceCandidate.data >> 24); // agent id
        extendCell[15 + ipStrBytes.length] = (byte) (extendServiceCandidate.data >> 16);
        extendCell[16 + ipStrBytes.length] = (byte) (extendServiceCandidate.data >> 8);
        extendCell[17 + ipStrBytes.length] = (byte) extendServiceCandidate.data;
        gatewaySocket.getOutputStream().write(extendCell);

        // Read relay extended.
        byte[] extendResponse = new byte[512];
        while (true) {
          if (gatewaySocket.getInputStream().read(extendResponse) != 512) {
            candidates.remove(extendServiceCandidate);
            break;
          } else {
            if (extendResponse[2] == TorCommand.RELAY.toByte() &&
                extendResponse[13] == RelayCommand.EXTENDED.toByte()) {
                System.out.println("  Extended tor circuit to node " + extendServiceCandidate.data +
                    " at " + extendServiceCandidate.ip.getHostAddress() +
                    ":" + extendServiceCandidate.iport);
              extendSuccesses++;
              break;
            } else {
              candidates.remove(extendServiceCandidate);
              break;
            }
          }
        }
      } catch (IOException e) {
        candidates.remove(extendServiceCandidate);
      }
    }

    return gatewaySocket;
  }
}
