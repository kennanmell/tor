package src;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** `TorServerThread` accepts TCP connections on a `ServerSocket`, adds any accepted `Socket`s to
    the `SocketManager`, and starts a `TorSocketReaderThread` to handle events on the `Socket`. */
public class TorServerThread extends Thread {
  /// The `ServerSocket` to accept TCP connections on.
  public final ServerSocket serverSocket;

  /** Sole constructor. */
  public TorServerThread() {
    try {
      this.serverSocket = new ServerSocket();
    } catch (IOException e) {
      // shouldn't ever happen
      throw new IllegalStateException();
    }
  }

  @Override
  public void run() {
    // Accept new TCP connections indefinitely.
    System.out.println("TorServerThread: starting");
    while (true) {
      try {
        Socket newSocket = serverSocket.accept();
        SocketManager.addSocket(newSocket, false);
        (new TorSocketReaderThread(newSocket)).start();
        System.out.println("TorServerThread: added new socket");
      } catch (IOException e) {
        System.out.println("TorServerThread: failed to accept socket");
        continue;
      }
    }
  }
}
