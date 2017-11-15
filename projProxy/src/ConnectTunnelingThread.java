package src;

import java.io.IOException;
import java.net.Socket;

public class ConnectTunnelingThread extends Thread {
  private Socket readSocket;
  private Socket writeSocket;

  public ConnectTunnelingThread(Socket readSocket, Socket writeSocket) {
    this.readSocket = readSocket;
    this.writeSocket = writeSocket;
  }

  @Override
  public void run() {
    try {
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
          writeSocket.getOutputStream().write(curr);
      }
    } catch (IOException e) {
      return;
    }
  }
}
