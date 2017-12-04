package proxy;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

// This thread (sends to tor socket RouterEntry.getSocket() gatewayEntry):
// Relay BEGIN w/ server ip:port (get stream id from router info)
// Relay data
// get response from buffer

// Other thread:
// Read from tor socket RouterEntry.getSocket() gatewayEntry
// Write to buffer associated with stream id (LinkedBlockingQueue)

/** HttpRequestThread sends one HTTP or HTTP connect request from the browser client to the
    server, then sends the response from the server to the browser. The request
    will always be downgraded to HTTP/1.0 and Connection: close. If the request is
    for connect, it starts two threads to relay the data in both directions. */
public class HttpRequestThread extends Thread {
  /** Callbacks for events in a HttpRequestThread. */
  public interface HttpRequestListener {
    /** Called when a request is received from the client socket.
        @param firstHeaderLine The first line of the request header. */
    public void onRequestReceived(String firstHeaderLine);
  }
  /// The number of ms to wait for a read from a socket before giving up and closing the connection.
  public static final int SO_TIMEOUT_MS = 5000;

  /// The socket for communication with the browser client.
  private Socket clientSocket;
  /// The socket for communication with the server.
  private Socket serverSocket;
  /// An HttpRequestListener that is called when events occur in this thread.
  private HttpRequestListener listener;

  /** Sole constructor.
      @param clientSocket The socket for communication with the browser (must not be null).
      @param listener The HttpRequestListener to call when events occur in this thread. */
  public HttpRequestThread(Socket clientSocket, HttpRequestListener listener) {
    try {
      clientSocket.setSoTimeout(SO_TIMEOUT_MS);
    } catch (IOException e) {
      // no op
    }
    this.clientSocket = clientSocket;
    this.serverSocket = null;
    this.listener = listener;
  }

  @Override
  public void run() {
    try {
      BufferedStreamReader reader = new BufferedStreamReader(clientSocket.getInputStream());
      String line = reader.readLine();
      if (line == null) {
        return;
      }

      if (listener != null) {
        listener.onRequestReceived(modifyHttpHeaderLine(line));
      }

      List<String> bufferedLines = new ArrayList<>();
      bufferedLines.add(line);
      serverSocket = getServerFromHttpHeader(reader, bufferedLines);

      if (line.trim().toLowerCase().startsWith("connect")) {
        if (serverSocket == null) {
          clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
          return;
        }

        if (!bufferedLines.get(bufferedLines.size() - 1).equals("\n") &&
            !bufferedLines.get(bufferedLines.size() - 1).equals("\r\n")) {
          String tempLine;
          while ((tempLine = reader.readLine()) != null) {
            // Flush the rest of the header.
            if (tempLine.equals("\n") || tempLine.equals("\r\n")) {
              break;
            }
          }
        }

        clientSocket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
        clientSocket.setSoTimeout(0);
        serverSocket.setSoTimeout(0);
        (new RawDataRelayThread(clientSocket, serverSocket)).start();
        (new RawDataRelayThread(serverSocket, clientSocket)).run();
      } else {
        if (serverSocket == null) {
          return;
        }

        while (!bufferedLines.isEmpty()) {
          // Send the lines of the header that have been read.
          serverSocket.getOutputStream().write(
              modifyHttpHeaderLine(bufferedLines.remove(0)).getBytes());
        }

        handleHttpMessage(clientSocket, serverSocket);
        handleHttpMessage(serverSocket, clientSocket);
      }
    } catch (IOException e) {
      try {
        clientSocket.close();
        if (serverSocket != null) {
          serverSocket.close();
        }
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }

  /** Handles an HTTP header and body sent from readSocket to writeSocket. */
  private void handleHttpMessage(Socket readSocket, Socket writeSocket) throws IOException {
    BufferedStreamReader reader = new BufferedStreamReader(readSocket.getInputStream());
    String line;
    while ((line = reader.readLine()) != null) {
      writeSocket.getOutputStream().write(modifyHttpHeaderLine(line).getBytes());
      if (line.equals("\n") || line.equals("\r\n")) {
        break;
      }
    }

    (new RawDataRelayThread(readSocket, writeSocket)).run();
  }

  /** Parses an HTTP header line to downgrade to HTTP/1.0 and Connection: close.
      @param line The line to modify.
      @return The modified line. */
  private String modifyHttpHeaderLine(String line) {
    line = line.replace("HTTP/1.1", "HTTP/1.0");
    if (line.trim().equalsIgnoreCase("Connection: keep-alive")) {
      line = "Connection: close\r\n";
    } else if (line.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
      line = "Proxy-connection: close\r\n";
    }
    return line;
  }

  /** Helper that returns a Socket for communication with a server specified by the host line or
      the first line in bufferedLines. Adds header lines it reads while looking for the host line
      to bufferedLines. Returns null if there is an error or the server is invalid. */
  private Socket getServerFromHttpHeader(BufferedStreamReader reader, List<String> bufferedLines) {
    String line;
    while ((line = reader.readLine()) != null) {
      bufferedLines.add(line);

      if (line.equals("\n") || line.equals("\r\n")) {
        line = null;
        break;
      }

      line = line.trim();
      if (line.toLowerCase().startsWith("host")) {
        break;
      }
    }

    if (bufferedLines.isEmpty()) {
      return null;
    }

    String[] alternateComponents = bufferedLines.get(0).toLowerCase().split(" ")[1].split(":");
    String[] ipComponents;
    if (line == null) {
      ipComponents = alternateComponents;
    } else {
      ipComponents = line.substring(6).trim().split(":");
    }

    String ip = ipComponents[0];
    int iport = -1;
    if (ipComponents.length == 2) {
      iport = Integer.parseInt(ipComponents[1]);
    } else if (alternateComponents.length > 1) {
      try {
        iport = Integer.parseInt(alternateComponents[alternateComponents.length - 1]);
      } catch (NumberFormatException e) {
        // no op
      }
    }

    if (iport == -1) {
      // Use default ports.
      if (alternateComponents[0].startsWith("https")) {
        iport = 443;
      } else {
        iport = 80;
      }
    }

    Socket resultSocket = null;
    try {
      resultSocket = new Socket(ip, iport);
      resultSocket.setSoTimeout(SO_TIMEOUT_MS);
      return resultSocket;
    } catch (IOException e) {
      if (resultSocket != null) {
        try {
          resultSocket.close();
        } catch (IOException e2) {
          // no op
        }
      }
      return null;
    }
  }
}
