package src;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TorServerThread extends Thread {
  public final ServerSocket serverSocket;

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
    // Accept new TCP connections until tor is closed.
    while (true) {
      try {
        Socket newSocket = serverSocket.accept();
        SocketManager.addSocket(newSocket, false);
        (new DirectedHopHandlerThread(newSocket)).start();
      } catch (IOException e) {
        continue;
      }
    }
  }
}
