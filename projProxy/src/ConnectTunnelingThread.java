package src;

import java.io.IOException;
import java.net.Socket;

/** ConnectTunnelingThread reads data byte-by-byte from from a TCP socket and writes that data
    to another TCP socket. Intended to be used for HTTP connect tunneling. */
public class ConnectTunnelingThread extends Thread {
  /// The socket to read data from.
  private Socket readSocket;
  /// The socket to write data to.
  private Socket writeSocket;

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
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
