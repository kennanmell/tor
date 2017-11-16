package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.net.URISyntaxException;
import java.net.URI;

/** RequestThread sends one HTTP or HTTP connect request from the browser client to the
    server, starts a thread to send the response/connection, then terminates. The request
    will always be downgraded to HTTP/1.0 and Connection: close. */
public class RequestThread extends Thread {
  /// The number of ms to wait for a read from a socket before giving up and closing the connection.
  public static final int SO_TIMEOUT_MS = 5000;

  /// The socket for communication with the client.
  private Socket clientSocket;

  /** Sole constructor.
      @param clientSocket The socket for communication with the browser (must not be null). */
  public RequestThread(Socket clientSocket) {
    try {
      clientSocket.setSoTimeout(SO_TIMEOUT_MS);
    } catch (IOException e) {
      // no op
    }
    this.clientSocket = clientSocket;
  }

  @Override
  public void run() {
    try {
      BufferedHttpReader reader = new BufferedHttpReader(clientSocket.getInputStream());
      String line = reader.readLine();
      if (line == null) {
        return;
      }

      System.out.print(">>> " + modifyHttpHeaderLine(line));

      List<String> bufferedLines = new ArrayList<>();
      bufferedLines.add(line);
      Socket serverSocket = getServerFromHttpHeader(reader, bufferedLines);

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
        (new BodyRelayThread(clientSocket, serverSocket)).start();
        (new BodyRelayThread(serverSocket, clientSocket)).run();
      } else {
        if (serverSocket == null) {
          return;
        }

        while (!bufferedLines.isEmpty()) {
          // Send the lines of the header that have been read.
          serverSocket.getOutputStream().write(
              modifyHttpHeaderLine(bufferedLines.remove(0)).getBytes());
        }

        handleHttpMessage(reader, clientSocket, serverSocket);
        handleHttpMessage(reader, serverSocket, clientSocket);
      }
    } catch (IOException e) {
      try {
        clientSocket.close();
        if (serverSocket != null) {
          serverSocket.close();
        }
      } catch (IOException e) {
        // no op
      }
      return;
    }
  }

  private void handleHttpMessage(BufferedHttpReader reader, Socket readSocket, Socket writeSocket)
                                 throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      writeSocket.getOutputStream().write(modifyHttpHeaderLine(line).getBytes());
      if (line.equals("\n") || line.equals("\r\n")) {
        break;
      }
    }

    (new BodyRelayThread(readSocket, writeSocket)).run();
  }

  private String modifyHttpHeaderLine(String line) {
    line = line.replace("HTTP/1.1", "HTTP/1.0");
    if (line.trim().equalsIgnoreCase("Connection: keep-alive")) {
      line = "Connection: close\r\n";
    } else if (line.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
      line = "Proxy-connection: close\r\n";
    }
    return line;
  }

  private Socket getServerFromHttpHeader(BufferedHttpReader reader, List<String> bufferedLines)
                                         throws IOException {
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

    try {
      Socket resultSocket = new Socket(ip, iport);
      resultSocket.setSoTimeout(SO_TIMEOUT_MS);
      return resultSocket;
    } catch (IOException e) {
      if (resultSocket != null) {
        try {
          resultSocket.close();
        } catch (IOException e) {
          // no op
        }
      }
      return null;
    }
  }
}
