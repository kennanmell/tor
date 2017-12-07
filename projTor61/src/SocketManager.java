package src;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/** The `SocketManager` is a static class that is responsible for tracking all tor `Socket`s
    opened by the application, as well as relevant information about them such as a tor
    agent id associated with the `Socket`. Information about each `Socket` can be accessed
    and modified via this class's static functions. This class also includes functions for
    getting new circuit ids for a `Socket`.

    Any code that initializes or accepts a `Socket` should add it to the `SocketManager`,
    and remove it when the `Socket` is closed or no longer or in use. */
public class SocketManager {
  /** A `SocketInfo` stores all of the `SocketManager`'s information about a `Socket`. */
  private static class SocketInfo {
    /// The `TorSocketWriterThread` responsible for writing to this `Socket`.
    TorSocketWriterThread t;
    /// `true` if and only if this application created this `Socket` (instead of adding
    /// it via a call to a `ServerSocket.accept`).
    boolean initiated;
    /// The FIFO queue used for mapping relay extend request-response exchange responses
    /// to the appropriate `RelayExtendThread`, or `null` if there are no pending relay
    /// extend requests on this `Socket`.
    BlockingQueue<byte[]> buffer;
    /// The agent id associated with this `Socket` or `-1` if the agent id has not been set.
    int agentId;

    /** Sole constructor.
        @param t The `TorSocketWriterThread` responsible for writing to this `Socket`.
        @param initiated `true` if this application created this `Socket`. */
    SocketInfo(TorSocketWriterThread t, boolean initiated) {
      this.t = t;
      this.initiated = initiated;
      this.buffer = null;
      this.agentId = -1;
    }
  }

  /// Maps from a `Socket` to the `SocketManager`'s information about the `Socket`.
  private static Map<Socket, SocketInfo> socketToInfo = new HashMap<>();
  /// For getting circuit ids.
  private static int nextOddCircuitId = 3;
  /// For getting circuit ids.
  private static int nextEvenCircuitId = 2;

  /** Adds a `Socket` for the `SocketManager` to manage.
      @param socket The `Socket` to add.
      @param initiated `true` if this application breated this `Socket`.
      @requires `socket` is not managed by the `SocketManager`. */
  public static void addSocket(Socket socket, boolean initiated) {
    synchronized (socketToInfo) {
      if (socketToInfo.containsKey(socket)) {
        throw new IllegalStateException("socket: " + socket);
      }
      TorSocketWriterThread t = new TorSocketWriterThread(socket);
      t.start();
      socketToInfo.put(socket, new SocketInfo(t, initiated));
    }
  }

  /** Removes a `Socket` from the `SocketManager`'s collection and closes the `Socket`
      if it's still open.
      @param socket The `Socket` to close and remove.
      @requires `socket` is managed by the `SocketManager`. */
  public static void removeSocket(Socket socket) {
    synchronized (socketToInfo) {
      try {
        socketToInfo.get(socket).t.socket.close();
      } catch (IOException e) {
        // no op
      }
      synchronized(socketToInfo.get(socket).t) {
        socketToInfo.get(socket).t.notify();
      }
      socketToInfo.remove(socket);
    }
  }

  /** Writes a `byte[]` of data to a `Socket` managed by the `SocketManager`.
      @param socket The `Socket` to write to.
      @param data The `byte`s to write to the `Socket`.
      @requires `socket` is managed by the `SocketManager`. */
  public static void writeToSocket(Socket socket, byte[] data) {
    synchronized (socketToInfo) {
      socketToInfo.get(socket).t.buf.add(data);
    }
  }

  /** Returns `true` if and only if the `Socket` is known to the `SocketManager`
      and this application created the `Socket`.
      @param socket The `Socket` whose origin to check.
      @requires `socket` is managed by the `SocketManager`. */
  public static boolean socketWasInitiated(Socket socket) {
    synchronized (socketToInfo) {
      return socketToInfo.get(socket).initiated;
    }
  }

  /** Returns a circuit id that can be used for relay extend requests on the `Socket`.
      @param socket The `Socket` to get a new circuit id for.
      @requires `socket` is managed by the `SocketManager`. */
  public static int getNextCircuitIdForSocket(Socket socket) {
    synchronized (socketToInfo) {
      if (socketToInfo.get(socket).initiated) {
        nextOddCircuitId += 2;
        if (nextOddCircuitId == 30001) {
          // make sure it doesn't overflow
          nextOddCircuitId = 3;
        }
        return nextOddCircuitId;
      } else {
        nextEvenCircuitId += 2;
        if (nextEvenCircuitId == 30000) {
          // make sure it doesn't overflow
          nextEvenCircuitId = 2;
        }
        return nextEvenCircuitId;
      }
    }
  }

  /** Sets the buffer to write relay extend request-response exchange responses to.
      @param socket The `Socket` to set a relay extend buffer for.
      @param buffer The FIFO queue to use as a relay extend buffer.
      @requires `socket` is managed by the `SocketManager`. */
  public static void setRelayExtendBufferForSocket(Socket socket, BlockingQueue<byte[]> buffer) {
    synchronized (socketToInfo) {
      socketToInfo.get(socket).buffer = buffer;
    }
  }

  /** Gets the buffer to write relay extens request-response exchanges responses to.
      @param socket The `Socket` to get the relay extend buffer for.
      @return A FIFO queue to use as a relay extend buffer.
      @requires `socket` is managed by the `SocketManager`. */
  public static BlockingQueue<byte[]> getRelayExtendBufferForSocket(Socket socket) {
    synchronized (socketToInfo) {
      return socketToInfo.get(socket).buffer;
    }
  }

  /** Sets the agent id for a `Socket`.
      @param socket The `Socket` to set the agent id of.
      @param agentId The agent id to associate with `socket`.
      @requires `socket` is managed by the `SocketManager`.
      @requires The agent id has not been previously set for `socket`. */
  public static void setAgentIdForSocket(Socket socket, int agentId) {
    synchronized (socketToInfo) {
      if (socketToInfo.get(socket).agentId != -1) {
        throw new IllegalStateException("socket: " + socket + "id: " + agentId);
      } else {
        socketToInfo.get(socket).agentId = agentId;
      }
    }
  }

  /** Returns the `Socket` associated with an agent id, or `null` if there is no agent id
      associated with it.
      @param agentId The agent id to get the `Socket` for.
      @return The `Socket` associated with `agentId`, or `null` if no `Socket` is
              associated with it. */
  public static Socket socketForAgentId(int agentId) {
    // Looping instead of keeping separate map because it dramatically simplifies the code
    // and is more space-efficient. This is still technically constant time since we cap
    // the number of open sockets at any time.
    Set<Socket> keySet;
    synchronized (socketToInfo) {
      keySet = socketToInfo.keySet();
    }
    for (Socket s : keySet) {
      if (socketToInfo.get(s).agentId == agentId) {
        return s;
      }
    }

    return null;
  }
}
