package src;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TorServerThread extends Thread {
  public final ServerSocket serverSocket;

  public TorServerThread(int iport) {
    try {
      this.serverSocket = new ServerSocket(iport);
    } catch (IOException e) {
      // shouldn't ever happen
      throw new IllegalStateException();
    }
  }

  @Override
  public void run() {
    // Accept new TCP connections until tor is closed.
    System.out.println("TorServerThread: starting");
    while (true) {
      try {
        SocketManager.addSocket(serverSocket.accept(), false);
        System.out.println("TorServerThread: added new socket");
      } catch (IOException e) {
        System.out.println("TorServerThread: failed to accept socket");
        continue;
      }
    }
  }
}
