package src;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import proxy.RawDataRelayThread;

/** A `TorSocketReaderThread` reads from a `Socket` and is responsible for handling or
    delegating all events on the `Socket` (i.e. the 8 types of tor commands). */
public class TorSocketReaderThread extends Thread {
  /** A `Hop` represents one step in a tor circuit, which can transmit data between two tor
      nodes. Each `Hop` consists of a `Socket` and a `int` representing a circuit id. */
  private static class Hop {
    /// The `Socket` associated with this `Hop`.
    public final Socket s;
    /// The circuit id associated with this `Hop`.
    public final int circuitId;

    /** Sole constructor.
        @param s The `Socket` associated with this `Hop`.
        @param circuitId The circuit id associated with this `Hop`. */
    Hop(Socket s, int circuitId) {
      this.s = s;
      this.circuitId = circuitId;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Hop)) {
        return false;
      }

      Hop otherHop = (Hop) other;

      // Only one socket per ip:port, so use strict == comparison.
      return this.s == otherHop.s && this.circuitId == otherHop.circuitId;
    }

    @Override
    public int hashCode() {
      return s.hashCode() * 5 + circuitId * 13;
    }
  }

  /// A shared map that all `TorSocketReaderThread`s maintain and reference to direct traffic
  /// across the tor network.
  private static Map<Hop, Hop> hopTable = new HashMap<>();
  private static int threadCount = 0;

  /// The `Socket` this thread reads from.
  private final Socket readSocket;
  /// A map from stream ids to `RawDataRelayThread`s reading data from a web server for that stream.
  private Map<Integer, RawDataRelayThread> responseRelayForStream;

  /** Sole constructor.
      @param readSocket The `Socket` to read from and handle events for.
      @requires Nothing else will read from `readSocket` as long as this thread is running. */
  public TorSocketReaderThread(Socket readSocket) {
    this.readSocket = readSocket;
    try {
      readSocket.setSoTimeout(0);
    } catch (IOException e) {
      // no op
    }
    this.responseRelayForStream = new HashMap<>();
  }

  @Override
  public void run() {
    System.out.println("started DHH thread " + this.toString());
    TorSocketReaderThread.threadCount++;
    System.out.println("threads: " + TorSocketReaderThread.threadCount + " sockets: " + SocketManager.size());
    try {
      byte[] cell = new byte[512];
      if (!SocketManager.socketWasInitiated(readSocket)) {
        System.out.println(this.toString() + ": waiting for OPEN");
        // If we did not initiate, check for open to make sure other end is also a tor node.
        if (readSocket.getInputStream().read(cell) != 512) {
          System.out.println(this.toString() + ": OPEN FAILED 1");
          SocketManager.removeSocket(readSocket);
          return;
        }

        if (commandForCell(cell) != TorCommand.OPEN || !handleOpenCommand(cell)) {
          System.out.println(this.toString() + ": OPEN FAILED 2");
          SocketManager.removeSocket(readSocket);
          return;
        }

        System.out.println(this.toString() + ": OPENED");
      }

      loop: while (readSocket.getInputStream().read(cell) == 512) {
        System.out.println(this.toString() + ": processing " + TorCommand.fromByte(cell[2]).toString());
        if (TorCommand.fromByte(cell[2]) == TorCommand.RELAY) {
          System.out.println(RelayCommand.fromByte(cell[13]).toString());
        }
        byte[] message = new byte[512]; // copy so original cell doesn't get put on buffer then modified.
        System.arraycopy(cell, 0, message, 0, 512);
        TorCommand command = commandForCell(cell);

        // If this is a response intended for another thread, forward it.
        // Shouldn't need to handle relay connected or relay extended because this case only
        // happens in relay extend, which only sends open and create request-response exchanges.
        if (command == TorCommand.OPENED || command == TorCommand.OPEN_FAILED ||
            command == TorCommand.CREATED || command == TorCommand.CREATE_FAILED) {
          BlockingQueue<byte[]> extendBuffer = SocketManager.getRelayExtendBufferForSocket(readSocket);
          if (extendBuffer != null) {
            extendBuffer.add(message);
          }
          continue loop;
        }

        // Handle OPEN commands separately since they don't have circuit ids.
        if (command == TorCommand.OPEN) {
          handleOpenCommand(cell);
          continue loop;
        }

        int circuitId = ((cell[0] & 0xFF) << 8) | ((cell[1] & 0xFF));
        Hop currentHop = new Hop(readSocket, circuitId);

        if (!hopTable.containsKey(currentHop)) {
          // This thread doesn't have the circuit mapping, so it can only handle a create
          // cell or relay extend cell in this case.
          System.out.println(this.toString() + ": hop table doesn't have hop");
          if (command == TorCommand.CREATE) {
            System.out.println(this.toString() + ": handling CREATE, sending CREATED");
            hopTable.put(currentHop, null);
            message[2] = TorCommand.CREATED.toByte();
            SocketManager.writeToSocket(readSocket, message);
          } else if (command == TorCommand.RELAY && RelayCommand.fromByte(cell[13]) == RelayCommand.EXTEND) {
            (new RelayExtendThread(message)).start();
          }
        } else if (hopTable.get(currentHop) != null) {
          // This thread has the mapping and it's not at the end of the circuit, so just relay
          // the message.
          Hop nextHop = hopTable.get(currentHop);
          message[0] = (byte) (nextHop.circuitId >> 8);
          message[1] = (byte) nextHop.circuitId;
          SocketManager.writeToSocket(nextHop.s, message);
          if (command == TorCommand.DESTROY) {
            hopTable.remove(currentHop);
          }
        } else {
          // This thread has the mapping and it's at the end of the circuit, so it needs
          // to handle the message.
          switch (command) {
            case CREATE:
            message[2] = TorCommand.CREATE_FAILED.toByte();
            SocketManager.writeToSocket(readSocket, message);
            break;

            case DESTROY:
            hopTable.remove(currentHop);
            break;

            case RELAY:
            int relayId = circuitId << 16;
            relayId |= (cell[3] & 0xFF) << 8;
            relayId |= cell[4] & 0xFF;

            switch (RelayCommand.fromByte(cell[13])) {
              case BEGIN:
              message[11] = 0;
              message[12] = 0;
              if (responseRelayForStream.containsKey(relayId)) {
                message[13] = RelayCommand.BEGIN_FAILED.toByte();
                SocketManager.writeToSocket(readSocket, message);
              } else {
                int bodyLength = cell[12] & 0xFF;
                bodyLength |= (cell[11] & 0xFF) << 8;
                int colonSeparatorIndex = 0;
                int endIndex = 0;
                for (int i = 14; i < 14 + bodyLength; i++) {
                  if (((char) message[i]) == ':') {
                    colonSeparatorIndex = i;
                  } else if (((char) message[i]) == '\0') {
                    endIndex = i;
                    break;
                  }
                }

                int iport = 0;
                for (int i = colonSeparatorIndex + 1; i < endIndex; i++) {
                  iport |= (cell[i] & 0xFF) << ((endIndex - 1 - i) * 8);
                }
                final String ip = (new String(cell)).substring(14, colonSeparatorIndex);
                Socket webSocket;
                try {
                  webSocket = new Socket(ip, iport);
                } catch (IOException e) {
                  message[13] = RelayCommand.BEGIN_FAILED.toByte();
                  SocketManager.writeToSocket(readSocket, message);
                  continue loop;
                }

                RawDataRelayThread responseRelayThread = new RawDataRelayThread(
                    webSocket, readSocket, relayId & 0xFFFF, circuitId); // TODO: ok to write directly to this socket?
                responseRelayThread.start();
                responseRelayForStream.put(relayId, responseRelayThread);

                message[13] = RelayCommand.CONNECTED.toByte();
                SocketManager.writeToSocket(readSocket, message);
              }
              break;

              case DATA:
              if (responseRelayForStream.containsKey(relayId)) {
                Socket webSocket = responseRelayForStream.get(relayId).readSocket;
                // TODO: ok to write directly to this socket?
                // TODO: how to demultiplex if simultaneous requests to same server from same stream?
                webSocket.getOutputStream().write(message, 14, 512 - 14);
              }
              break;

              case END:
              if (responseRelayForStream.containsKey(relayId)) {
                responseRelayForStream.get(relayId).kill();
                responseRelayForStream.remove(relayId);
              }

              //case EXTEND:
              //message[11] = cell[11];
              //message[12] = cell[12];
              //(new RelayExtendThread(message)).start();
              break;

              case EXTEND:
              message[11] = cell[11];
              message[12] = cell[12];
              (new RelayExtendThread(message)).start();
              break;

              default: // no op
            }
            break;

            default: // no op
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    for (int key : responseRelayForStream.keySet()) {
      responseRelayForStream.get(key).kill();
    }
    // TODO: kill any open relay extend thread
    try {
      SocketManager.removeSocket(readSocket);
    } catch (NullPointerException e) {
      // no op; closing anyway
    }
    TorSocketReaderThread.threadCount--;
    System.out.println(this + ": killed thread");
    System.out.println("threads: " + TorSocketReaderThread.threadCount + " sockets: " + SocketManager.size());
  }

  /** Gets the `TorCommand` type for a tor cell. */
  private TorCommand commandForCell(byte[] cell) {
    return TorCommand.fromByte(cell[2]);
  }

  /** Responds to an open cell from a tor node. */
  private boolean handleOpenCommand(byte[] cell) {
    byte[] message = new byte[512];
    System.arraycopy(cell, 0, message, 0, 512);
    int openedId = 0;
    openedId |= (cell[7] & 0xFF) << 24;
    openedId |= (cell[8] & 0xFF) << 16;
    openedId |= (cell[9] & 0xFF) << 8;
    openedId |= (cell[10] & 0xFF);
    if (openedId == TorMain.agentId) {
      message[2] = TorCommand.OPENED.toByte();
    } else {
      message[2] = TorCommand.OPEN_FAILED.toByte();
    }

    int openerId = 0;
    openerId |= (cell[3] & 0xFF) << 24;
    openerId |= (cell[4] & 0xFF) << 16;
    openerId |= (cell[5] & 0xFF) << 8;
    openerId |= (cell[6] & 0xFF);
    SocketManager.setAgentIdForSocket(readSocket, openerId);

    SocketManager.writeToSocket(readSocket, message);
    return openedId == TorMain.agentId;
  }

  /** A helper thread that handles a series of request-response exchanges triggered by
      a relay extend request. */
  private class RelayExtendThread extends Thread {
    /// The relay extend request cell.
    byte[] extendCell;
    /// A buffer that contains responses to this thread's requests.
    BlockingQueue<byte[]> readBuffer;

    /** Sole constructor. */
    RelayExtendThread(byte[] extendCell) {
      this.extendCell = extendCell;
      this.readBuffer = new LinkedBlockingQueue<>();
    }

    @Override
    public void run() {
      System.out.println("Relay Extend: starting");
      // Get the data from the relay extend cell.
      byte[] message = new byte[512];
      System.arraycopy(extendCell, 0, message, 0, 512);
      message[11] = 0;
      message[12] = 0;

      int bodyLength = extendCell[12] & 0xFF;
      bodyLength |= (extendCell[11] & 0xFF) << 8;
      int colonSeparatorIndex = 0;
      int endIndex = 0;
      for (int i = 14; i < 14 + bodyLength; i++) {
        if (((char) message[i]) == ':') {
          colonSeparatorIndex = i;
        } else if (((char) message[i]) == '\0') {
          endIndex = i;
          break;
        }
      }

      int iport = 0;
      try {
        iport = Integer.parseInt(new String(extendCell).substring(colonSeparatorIndex + 1, endIndex));
      } catch (NumberFormatException e) {
        e.printStackTrace();
        // TODO: handle?
      }
      final String ip = (new String(extendCell)).substring(14, colonSeparatorIndex);

      int newAgentId = 0;
      for (int i = endIndex + 1; i < 14 + bodyLength; i++) {
        newAgentId |= (extendCell[i] & 0xFF) << ((bodyLength + 13 - i) * 8);
      }

      System.out.println("Relay extend agent: " + newAgentId);
      if (newAgentId == TorMain.agentId) {
        System.out.println("Relay extend: self loop");
        message[13] = RelayCommand.EXTENDED.toByte();
        SocketManager.writeToSocket(readSocket, message);
        return;
      }

      // Get/create socket to extend the hop to.
      Socket nextHopSocket = SocketManager.socketForAgentId(newAgentId);
      if (nextHopSocket == null) {
        System.out.println("Relay Extend: opening new socket: " + ip + iport);
        try {
          nextHopSocket = new Socket(ip, iport);
        } catch (IOException e) {
          System.out.println(this.toString() + ": failed to open next hop socket 1");
          e.printStackTrace();
          System.out.println(ip);
          System.out.println(iport);
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }

        SocketManager.addSocket(nextHopSocket, true);
        SocketManager.setAgentIdForSocket(nextHopSocket, newAgentId);
        (new TorSocketReaderThread(nextHopSocket)).start();
        SocketManager.setRelayExtendBufferForSocket(nextHopSocket, readBuffer);

        byte[] openCell = new byte[512];
        openCell[2] = TorCommand.OPEN.toByte();
        openCell[3] = (byte) (TorMain.agentId >> 24);
        openCell[4] = (byte) (TorMain.agentId >> 16);
        openCell[5] = (byte) (TorMain.agentId >> 8);
        openCell[6] = (byte) TorMain.agentId;
        openCell[7] = extendCell[endIndex + 1];
        openCell[8] = extendCell[endIndex + 2];
        openCell[9] = extendCell[endIndex + 3];
        openCell[10] = extendCell[endIndex + 4];
        SocketManager.writeToSocket(nextHopSocket, openCell);

        byte[] opened;
        try {
          opened = readBuffer.take();
        } catch (InterruptedException e) {
          SocketManager.removeSocket(nextHopSocket);
          SocketManager.setRelayExtendBufferForSocket(nextHopSocket, null);
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }
        if (opened[2] != TorCommand.OPENED.toByte()) {
          // TODO: also check opener and opened ids
          SocketManager.removeSocket(nextHopSocket);
          SocketManager.setRelayExtendBufferForSocket(nextHopSocket, null);
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }
      } else {
        SocketManager.setRelayExtendBufferForSocket(nextHopSocket, readBuffer);
      }

      System.out.println("Relay Extend: got circuit");
      int newCircuitId = SocketManager.getNextCircuitIdForSocket(nextHopSocket);

      byte[] createCell = new byte[512];
      createCell[0] = (byte) (newCircuitId >> 8);
      createCell[1] = (byte) newCircuitId;
      createCell[2] = TorCommand.CREATE.toByte();
      System.out.println("Relay Extend: writing CREATE");
      SocketManager.writeToSocket(nextHopSocket, createCell);
      byte[] created;
      try {
        System.out.println("Relay Extend: waiting for CREATE response");
        created = readBuffer.take();
      } catch (InterruptedException e) {
        SocketManager.removeSocket(nextHopSocket);
        SocketManager.setRelayExtendBufferForSocket(nextHopSocket, null);
        message[13] = RelayCommand.EXTEND_FAILED.toByte();
        SocketManager.writeToSocket(readSocket, message);
        return;
      }
      System.out.println("Relay Extend: got CREATE response");
      if (created[0] != createCell[0] ||
          created[1] != createCell[1] ||
          created[2] != TorCommand.CREATED.toByte()) {
        SocketManager.removeSocket(nextHopSocket);
        SocketManager.setRelayExtendBufferForSocket(nextHopSocket, null);
        message[13] = RelayCommand.EXTEND_FAILED.toByte();
        SocketManager.writeToSocket(readSocket, message);
        return;
      }

      Hop currentHop = new Hop(readSocket, ((extendCell[0] & 0xFF) << 8) | (extendCell[1] & 0xFF));
      Hop newHop = new Hop(nextHopSocket, newCircuitId);
      hopTable.put(currentHop, newHop);
      message[13] = RelayCommand.EXTENDED.toByte();
      SocketManager.writeToSocket(readSocket, message);
      SocketManager.setRelayExtendBufferForSocket(nextHopSocket, null);
    }
  }
}
