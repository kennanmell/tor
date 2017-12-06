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
  private static Map<Hop, BlockingQueue<byte[]>> extendBuffers =
      new HashMap<>();
  // For getting circuit ids.
  private static int currentOddId = 3;
  // For getting circuit ids.
  private static int currentEvenId = 2;

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
        addressToSocketWriteBuffer.get(address).t.notify();
        addressToSocketWriteBuffer.remove(address);
      }
    }
  }

  public static BlockingQueue<byte[]> getWriteBuffer(Socket socket) {
    synchronized(addressToSocketWriteBuffer) {
      String address = socket.getInetAddress().toString() + ":" + socket.getPort();
      if (addressToSocketWriteBuffer.containsKey(address)) {
        return addressToSocketWriteBuffer.get(address).t.buf;
      } else {
        return null;
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

  // MARK: extend buffers
  public static BlockingQueue<byte[]> getBufferForCircuit(Socket s, int circuitId) {
      return extendBuffers.get(new Hop(s, circuitId));
  }

  public static void setBufferForCircuit(Socket s, int circuitId, BlockingQueue<byte[]> buf) {
    Hop key = new Hop(s, circuitId);
    if (extendBuffers.containsKey(key)) {
      // TODO: need better handling?
      throw new IllegalArgumentException();
    }

    extendBuffers.put(key, buf);
  }

  public static void removeBufferForCircuit(Socket s, int circuitId) {
    extendBuffers.remove(new Hop(s, circuitId));
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
