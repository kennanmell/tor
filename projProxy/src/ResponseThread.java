package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;

/** ResponseThread listens on a TCP socket for a response to a HTTP request, parses it,
    and sends it to a browser client. The response will always be downgraded to HTTP/1.0
    and Connection: close. */
public class ResponseThread extends Thread {
  /// The socket connected to the browser to write data to.
  private Socket clientSocket;
  /// The socket connected to the server to read data from.
  private Socket serverSocket;

  /** Sole constructor.
      @param serverSocket The TCP socket to read data from (must not be null).
      @param clientSocket The TCP socket to write data to (must not be null). */
  public ResponseThread(Socket clientSocket, Socket serverSocket) {
    this.clientSocket = clientSocket;
    this.serverSocket = serverSocket;
  }

  @Override
  public void run() {
    try {
      StringBuilder line = new StringBuilder(); // for reading a header line-by-line
      int curr; // the current byte read from the browser
      boolean sentHeader = false; // true only if the header has been sent (but not payload)
      while ((curr = serverSocket.getInputStream().read()) != -1) {
        if (sentHeader) {
          // Already sent header so just send the rest byte-by-byte.
          clientSocket.getOutputStream().write(curr);
        } else {
          line.append((char) curr);
          if (curr == (int) '\n') {
            // End of header line.
            String lineString = line.toString();

            // Parse out HTTP/1.1 and keep-alive.
            if (lineString.contains("HTTP/1.1")) {
              line = new StringBuilder(lineString.replace("HTTP/1.1", "HTTP/1.0"));
            } else if (lineString.trim().equalsIgnoreCase("Connection: keep-alive")) {
              line = new StringBuilder("Connection: close\r\n");
            } else if (lineString.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
              line = new StringBuilder("Proxy-connection: close\r\n");
            }

            clientSocket.getOutputStream().write(line.toString().getBytes());
            if (lineString.equals("\n") || lineString.equals("\r\n")) {
              // Done parsing/sending header.
              sentHeader = true;
            }
            line = new StringBuilder();
          }
        }
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
