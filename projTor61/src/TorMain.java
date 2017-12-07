package src;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import regagent.RegAgentThread;
import regagent.Service;
import proxy.ProxyThread;

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

    TorServerThread torServer = new TorServerThread();
    torServer.start();

    RegAgentThread regThread = new RegAgentThread(groupNo, instanceNo, agentId, torServer.serverSocket.getLocalPort()); // TODO: use Service class instead?
    regThread.start();

    Socket proxyCircuitFirstHopSocket = makeLocalCircuit(regThread.getAllServices());

    (new ProxyThread(iport, 1, proxyCircuitFirstHopSocket)).start();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("running shutdown hook");
        regThread.unregisterService();
        SocketManager.removeAllSockets();
      }
    });
  }

  /** Creates the local circuit for routing browser proxy traffic on this tor node. */
  private static Socket makeLocalCircuit(List<Service> candidates) {
    Random r = new Random();
    List<Socket> openedSockets = new ArrayList<>();
    System.out.println("local circuit candidates: " + candidates);

    int connected = 0;
    Socket result = null;
    while (connected < 3) {
      Service nextHopService = candidates.get(r.nextInt(candidates.size()));
      System.out.println("trying to add to local circuit: " + nextHopService);
      Socket s = SocketManager.socketForAgentId(nextHopService.data);
      if (s == null) {
        try {
          s = new Socket(nextHopService.ip, nextHopService.iport);
        } catch (IOException e) {
          System.out.println("Main: Failed to bind to new socket");
          candidates.remove(nextHopService);
          continue;
        }
        if (s == null) {
          System.out.println("S IS NULL!!!!");
        }
        SocketManager.addSocket(s, true);
        SocketManager.setAgentIdForSocket(s, nextHopService.data);

        System.out.println("Main: opening new socket for circuit");
        byte[] openCell = new byte[512];
        openCell[2] = TorCommand.OPEN.toByte();
        openCell[3] = (byte) (TorMain.agentId >> 24);
        openCell[4] = (byte) (TorMain.agentId >> 16);
        openCell[5] = (byte) (TorMain.agentId >> 8);
        openCell[6] = (byte) TorMain.agentId;
        openCell[7] = (byte) (nextHopService.data >> 24); // opened agent id
        openCell[8] = (byte) (nextHopService.data >> 16);
        openCell[9] = (byte) (nextHopService.data >> 8);
        openCell[10] = (byte) nextHopService.data;
        try {
          s.getOutputStream().write(openCell);
        } catch (IOException e) {
          candidates.remove(nextHopService);
          SocketManager.removeSocket(s);
          System.out.println("Main: failed to write OPEN");
          continue;
        }

        byte[] response = new byte[512];
        try {
          if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.OPENED.toByte()) {
            System.out.println("Main: OPENing new socket failed 1");
            candidates.remove(nextHopService);
            SocketManager.removeSocket(s);
            continue;
          }
        } catch (IOException e) {
          System.out.println("Main: OPENing new socket failed 2");
          candidates.remove(nextHopService);
          SocketManager.removeSocket(s);
          continue;
        }

        openedSockets.add(s);
        System.out.println("Main: OPENed new socket");
      }

      if (connected == 0) {
        System.out.println("Main: sending CREATE for circut");
        byte[] createCell = new byte[512];
        createCell[1] = 1;
        createCell[2] = TorCommand.CREATE.toByte();
        try {
          s.getOutputStream().write(createCell);
        } catch (IOException e) {
          System.out.println("Main: failed to write CREATE");
          candidates.remove(nextHopService);
          continue;
        }

        byte[] response = new byte[512];
        try {
          if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.CREATED.toByte()) {
            System.out.println("Main: CREATEing new socket failed 1");
            candidates.remove(nextHopService);
            continue;
          }
        } catch (IOException e) {
          System.out.println("Main: CREATINGing new socket failed 2");
          candidates.remove(nextHopService);
          continue;
        }

        System.out.println("Main: CREATED new socket");
      } else {
        System.out.println("Main: EXTENDing socket");
        String ipStr = nextHopService.ip.toString() + ":" + nextHopService.iport + '\0';
        int ipStrBytesLength = ipStr.getBytes().length;
        byte[] extendCell = new byte[512];
        extendCell[1] = 1;
        extendCell[2] = TorCommand.RELAY.toByte();
        extendCell[11] = (byte) ((ipStrBytesLength + 4) >> 8);
        extendCell[12] = (byte) (ipStrBytesLength + 4);
        extendCell[13] = RelayCommand.EXTEND.toByte();
        System.arraycopy(ipStr.getBytes(), 0, extendCell, 14, ipStrBytesLength);
        extendCell[14 + ipStrBytesLength] = (byte) (nextHopService.data >> 24); // agent id
        extendCell[15 + ipStrBytesLength] = (byte) (nextHopService.data >> 16);
        extendCell[16 + ipStrBytesLength] = (byte) (nextHopService.data >> 8);
        extendCell[17 + ipStrBytesLength] = (byte) nextHopService.data;

        try {
          s.getOutputStream().write(extendCell);
        } catch (IOException e) {
          System.out.println("Main: failed to write EXTEND");
          candidates.remove(nextHopService);
          continue;
        }

        byte[] response = new byte[512];
        while (true) {
          try {
            if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.RELAY.toByte() ||
                response[13] != RelayCommand.EXTENDED.toByte()) {
              if (SocketManager.getRelayExtendBufferForSocket(s) != null) {
                byte[] tempCopy = new byte[512];
                System.arraycopy(response, 0, tempCopy, 0, 512);
                SocketManager.getRelayExtendBufferForSocket(s).add(tempCopy);
              }
              System.out.println("Main: Forwarded non-EXTEND response");
              continue;
            } else {
              break;
            }
          } catch (IOException e) {
            System.out.println("Main: EXTENDing circuit failed 2");
            candidates.remove(nextHopService);
            continue;
          }
        }

        System.out.println("Main: EXTENDED circuit");
      }

      if (result == null) {
        result = s;
      }
      connected++;
    }

    while (!openedSockets.isEmpty()) {
      (new TorSocketReaderThread(openedSockets.remove(0))).start();
    }
    return result;
  }
}
