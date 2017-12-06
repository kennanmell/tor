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

public class DirectedHopHandlerThread extends Thread {
  public static Map<Hop, Hop> hopTable = new HashMap<>();

  private final Socket readSocket;
  private Map<Integer, RawDataRelayThread> responseRelayForStream;

  public DirectedHopHandlerThread(Socket readSocket) {
    this.readSocket = readSocket;
    this.responseRelayForStream = new HashMap<>();
  }

  @Override
  public void run() {
    System.out.println("started DHH thread " + this.toString());
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
        byte[] message = new byte[512]; // copy so original cell doesn't get put on buffer then modified.
        System.arraycopy(cell, 0, message, 0, 512);
        TorCommand command = commandForCell(cell);

        // If this is a response intended for another thread, forward it.
        // Shouldn't need to handle relay connected or relay extended because this case only
        // happens in relay extend, which only sends open and create request-response exchanges.
        if (command == TorCommand.OPENED || command == TorCommand.OPEN_FAILED ||
            command == TorCommand.CREATED || command == TorCommand.CREATE_FAILED) {
          if (SocketManager.extendBuffers.containsKey(readSocket)) {
            SocketManager.extendBuffers.get(readSocket).add(message);
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
          // cell in this case.
          if (command == TorCommand.CREATE) {
            hopTable.put(currentHop, null);
            message[2] = TorCommand.CREATED.toByte();
            SocketManager.writeToSocket(readSocket, message);
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
      // no op
    }

    for (int key : responseRelayForStream.keySet()) {
      responseRelayForStream.get(key).kill();
    }
    // TODO: kill any open relay extend thread
    SocketManager.removeSocket(readSocket);
  }

  private TorCommand commandForCell(byte[] cell) {
    return TorCommand.fromByte(cell[2]);
  }

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
    SocketManager.writeToSocket(readSocket, message);
    return openedId == TorMain.agentId;
  }

  private byte[] getOpenCommand(TorCommand command, int openerId, int openedId) {
    byte[] result = new byte[512];
    switch (command) {
      case OPEN: result[2] = command.toByte(); break;
      case OPENED: result[2] = command.toByte(); break;
      case OPEN_FAILED: result[2] = command.toByte(); break;
      default: throw new IllegalArgumentException();
    }
    result[3] = (byte) (openerId >> 24);
    result[4] = (byte) (openerId >> 16);
    result[5] = (byte) (openerId >> 8);
    result[6] = (byte) openerId;
    result[7] = (byte) (openedId >> 24);
    result[8] = (byte) (openedId >> 16);
    result[9] = (byte) (openedId >> 8);
    result[10] = (byte) openedId;
    return result;
  }

  private class RelayExtendThread extends Thread {
    byte[] extendCell;
    BlockingQueue<byte[]> readBuffer;

    RelayExtendThread(byte[] extendCell) {
      this.extendCell = extendCell;
      this.readBuffer = new LinkedBlockingQueue<>();
    }

    @Override
    public void run() {
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
      for (int i = colonSeparatorIndex + 1; i < endIndex; i++) {
        iport |= (extendCell[i] & 0xFF) << ((endIndex - 1 - i) * 8);
      }
      final String ip = (new String(extendCell)).substring(14, colonSeparatorIndex);

      // Get/create socket to extend the hop to.
      Socket nextHopSocket = null;
      try {
        nextHopSocket = SocketManager.getSocketWithStringAddress(
            InetAddress.getByName(ip).toString() + ":" + iport);
      } catch (UnknownHostException e) {
        // no op
      }
      if (nextHopSocket == null) {
        try {
          nextHopSocket = new Socket(ip, iport);
        } catch (IOException e) {
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }

        SocketManager.addSocket(nextHopSocket, true);
        SocketManager.extendBuffers.put(nextHopSocket, readBuffer);

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
          SocketManager.extendBuffers.remove(nextHopSocket);
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }
        if (opened[2] != TorCommand.OPENED.toByte()) {
          // TODO: also check opener and opened ids
          SocketManager.removeSocket(nextHopSocket);
          SocketManager.extendBuffers.remove(nextHopSocket);
          message[13] = RelayCommand.EXTEND_FAILED.toByte();
          SocketManager.writeToSocket(readSocket, message);
          return;
        }
      } else {
        SocketManager.extendBuffers.put(nextHopSocket, readBuffer);
      }

      int newCircuitId = SocketManager.getNextCircuitIdForSocket(nextHopSocket);

      byte[] createCell = new byte[512];
      createCell[0] = (byte) (newCircuitId >> 8);
      createCell[1] = (byte) newCircuitId;
      createCell[2] = TorCommand.CREATE.toByte();
      SocketManager.writeToSocket(nextHopSocket, createCell);
      byte[] created;
      try {
        created = readBuffer.take();
      } catch (InterruptedException e) {
        SocketManager.removeSocket(nextHopSocket);
        SocketManager.extendBuffers.remove(nextHopSocket);
        message[13] = RelayCommand.EXTEND_FAILED.toByte();
        SocketManager.writeToSocket(readSocket, message);
        return;
      }
      if (created[0] != createCell[0] ||
          created[1] != createCell[1] ||
          created[2] != TorCommand.CREATED.toByte()) {
        SocketManager.removeSocket(nextHopSocket);
        SocketManager.extendBuffers.remove(nextHopSocket);
        message[13] = RelayCommand.EXTEND_FAILED.toByte();
        SocketManager.writeToSocket(readSocket, message);
        return;
      }

      Hop currentHop = new Hop(readSocket, ((extendCell[0] & 0xFF) << 8) | (extendCell[1] & 0xFF));
      Hop newHop = new Hop(nextHopSocket, newCircuitId);
      hopTable.put(currentHop, newHop);
      message[13] = RelayCommand.EXTENDED.toByte();
      SocketManager.writeToSocket(readSocket, message);
      SocketManager.extendBuffers.remove(nextHopSocket);
    }
  }
}