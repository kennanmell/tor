package src;

import java.io.IOException;
import java.net.Socket;

/** BodyRelayThread reads data byte-by-byte from from a TCP socket and writes that data
    to another TCP socket. Intended to be used for HTTP connect tunneling. */
public class BodyRelayThread extends Thread {
  /// The socket connected to the browser to write data to.
  private Socket clientSocket;
  /// The socket connected to the server to read data from.
  private Socket serverSocket;

  /** Sole constructor.
      @param serverSocket The TCP socket to read data from (must not be null).
      @param clientSocket The TCP socket to write data to (must not be null). */
  public BodyRelayThread(Socket clientSocket, Socket serverSocket) {
    this.clientSocket = clientSocket;
    this.serverSocket = serverSocket;
  }

  @Override
  public void run() {
    try {
      int curr;
      while ((curr = serverSocket.getInputStream().read()) != -1) {
          clientSocket.getOutputStream().write(curr);
      }
    } catch (IOException e) {
      try {
        serverSocket.close();
        clientSocket.close();
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }
}
