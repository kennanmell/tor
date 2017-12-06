package src;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import regagent.RegAgentThread;
import proxy.ProxyThread;

public class TorMain {

  public static int agentId;
  public static final int serverPort = 5004;
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

    TorServerThread torServer = new TorServerThread(serverPort);
    torServer.start();

    // TODO: make my circuit, uncomment
    Socket[] createdSockets = makeLocalCircuit(serverPort);
    for (int i = 1; i < 4; i++) {
      if (createdSockets[i] != null) {
        SocketManager.addSocket(createdSockets[i], true);
      }
    }

    RegAgentThread regThread = new RegAgentThread(groupNo, instanceNo, agentId, torServer.serverSocket.getLocalPort()); // TODO: use Service class instead?
    regThread.start();

    (new ProxyThread(iport, 1, createdSockets[0])).start();
  }

  private static Socket[] makeLocalCircuit(int iport) {
    int connected = 0;
    Socket[] createdSockets = new Socket[4];
    while (connected < 3) {
      InetAddress ip;
      try {
        ip = InetAddress.getLocalHost();
      } catch (UnknownHostException e) {
        System.out.println("Main: can't find local host");
        return null;
      }
      String sstr = ip.toString() + ":" + iport;
      Socket s = null;
      for (int i = 1; i < 4; i++) {
        if (createdSockets[i] != null) {
          String sstr2 = createdSockets[i].getInetAddress().toString() + ":" + createdSockets[i].getPort();
          if (sstr.equals(sstr2)) {
            s = createdSockets[i];
            break;
          }
        }
      }
      if (s == null) {
        try {
          s = new Socket(ip, iport);
        } catch (IOException e) {
          System.out.println("Failed to bind to local host");
          continue;
        }
        System.out.println("Main: opening new socket for circuit");
        byte[] openCell = new byte[512];
        openCell[2] = TorCommand.OPEN.toByte();
        openCell[3] = (byte) (agentId >> 24);
        openCell[4] = (byte) (agentId >> 16);
        openCell[5] = (byte) (agentId >> 8);
        openCell[6] = (byte) agentId;
        openCell[7] = openCell[3];
        openCell[8] = openCell[4];
        openCell[9] = openCell[5];
        openCell[10] = openCell[6];
        try {
          s.getOutputStream().write(openCell);
        } catch (IOException e) {
          System.out.println("Main: failed to write OPEN");
          continue;
        }

        byte[] response = new byte[512];
        try {
          if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.OPENED.toByte()) {
            System.out.println("Main: OPENing new socket failed 1");
            continue;
          }
        } catch (IOException e) {
          System.out.println("Main: OPENing new socket failed 2");
          continue;
        }

        if (createdSockets[1] == null) {
          createdSockets[1] = s;
        } else if (createdSockets[2] == null) {
          createdSockets[2] = s;
        } else if (createdSockets[3] == null) {
          createdSockets[3] = s;
        }
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
          continue;
        }

        byte[] response = new byte[512];
        try {
          if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.CREATED.toByte()) {
            System.out.println("Main: CREATEing new socket failed 1");
            continue;
          }
        } catch (IOException e) {
          System.out.println("Main: CREATINGing new socket failed 2");
          continue;
        }

        System.out.println("Main: CREATED new socket");
      } else {
        System.out.println("Main: EXTENDing socket");
        sstr += "\0" + TorMain.agentId; // TODO: change id
        byte[] extendCell = new byte[512];
        extendCell[1] = 1;
        extendCell[2] = TorCommand.RELAY.toByte();
        extendCell[11] = (byte) (sstr.length() >> 8);
        extendCell[12] = (byte) sstr.length();
        extendCell[13] = RelayCommand.EXTEND.toByte();
        System.arraycopy(sstr, 0, extendCell, 14, sstr.length());

        try {
          s.getOutputStream().write(extendCell);
        } catch (IOException e) {
          System.out.println("Main: failed to write EXTEND");
          continue;
        }

        byte[] response = new byte[512];
        try {
          if (s.getInputStream().read(response) != 512 || response[2] != TorCommand.RELAY.toByte() ||
              response[13] != RelayCommand.EXTENDED) {
            System.out.println("Main: EXTENDing circuit failed 1");
            continue;
          }
        } catch (IOException e) {
          System.out.println("Main: EXTENDing circuit failed 2");
          continue;
        }

        System.out.println("Main: EXTENDED circuit");
      }

      if (createdSockets[0] == null) {
        createdSockets[0] = s;
      }
      connected++;
    }

    return createdSockets;
  }
}
