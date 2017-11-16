package src;

import java.io.IOException;
import java.net.Socket;

/** BodyRelayThread reads data byte-by-byte from from a TCP socket and writes that data
    to another TCP socket. Intended to be used for HTTP connect tunneling. */
public class BodyRelayThread extends Thread {
  /// The socket connected to the browser to write data to.
  private Socket writeSocket;
  /// The socket connected to the server to read data from.
  private Socket readSocket;

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
  public BodyRelayThread(Socket writeSocket, Socket readSocket) {
    this.writeSocket = writeSocket;
    this.readSocket = readSocket;
  }

  @Override
  public void run() {
    try {
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
          writeSocket.getOutputStream().write(curr);
      }
    } catch (IOException e) {
      try {
        readSocket.close();
        writeSocket.close();
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }
}
