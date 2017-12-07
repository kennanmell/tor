package src;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class SocketManager {
  // Maps from an ip:port String to a Socket, buffer for writing to that socket, and a boolean
  // indicating whether or not this node created the Socket.
  private static Map<String, AddressToWriteBufferTuple> addressToSocketWriteBuffer =
      new HashMap<>();
  public static Map<Socket, BlockingQueue<byte[]>> extendBuffers =
      new HashMap<>();
  // For getting circuit ids.
  private static int currentOddId = 3;
  // For getting circuit ids.
  private static int currentEvenId = 2;

  // MARK: socket Map
  public static Map<Integer, Socket> agentIdToSocket = new HashMap<>();

  // MARK: Address to write socket
  public static void addSocket(Socket socket, boolean initiated) {
    synchronized(addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      WriteBufferToSocketThread t = new WriteBufferToSocketThread(socket);
      t.start();
      addressToSocketWriteBuffer.put(address, new AddressToWriteBufferTuple(t, initiated));
    }
  }

  public static void removeSocket(Socket socket) {
    synchronized(addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      if (addressToSocketWriteBuffer.containsKey(address)) {
        try {
          addressToSocketWriteBuffer.get(address).t.socket.close();
        } catch (IOException e) {
          // no op
        }
        synchronized(addressToSocketWriteBuffer.get(address).t) {
          addressToSocketWriteBuffer.get(address).t.notify();
        }
        addressToSocketWriteBuffer.remove(address);
      }
    }
  }

  public static void writeToSocket(Socket socket, byte[] data) {
    synchronized (addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      if (addressToSocketWriteBuffer.containsKey(address)) {
        addressToSocketWriteBuffer.get(address).t.buf.add(data);
      }
    }
  }

  public static boolean socketWasInitiated(Socket socket) {
    synchronized(addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      if (addressToSocketWriteBuffer.containsKey(address)) {
        return addressToSocketWriteBuffer.get(address).initiated;
      } else {
        return false;
      }
    }
  }

  // MARK: circuit id
  public static int getNextCircuitIdForSocket(Socket socket) {
    synchronized(addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      AddressToWriteBufferTuple tuple = addressToSocketWriteBuffer.get(address);
      if (tuple == null) {
        // TODO: need to change this handling?
        throw new IllegalArgumentException();
      }

      if (tuple.initiated) {
        currentOddId += 2;
        if (currentOddId == 30001) {
          // make sure it doesn't overflow
          currentOddId = 3;
        }
        return currentOddId;
      } else {
        currentEvenId += 2;
        if (currentEvenId == 30000) {
          // make sure it doesn't overflow
          currentEvenId = 2;
        }
        return currentEvenId;
      }
    }
  }

  private static class AddressToWriteBufferTuple {
    WriteBufferToSocketThread t;
    boolean initiated;

    AddressToWriteBufferTuple(WriteBufferToSocketThread t, boolean initiated) {
      this.t = t;
      this.initiated = initiated;
    }
  }
}
