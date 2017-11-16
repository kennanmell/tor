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
  /// The socket for communication with the client.
  private Socket clientSocket;
  /// The socket for communication with the server.
  private Socket serverSocket;
  /// A buffer of HTTP header lines from the client to send to the server.
  private List<String> currentHeaderLines;
  // port number from request line, -1 if not found
  private int headerPort; 
  // port number from host line, -1 if not found
  private int hostPort;
  // starts with https or not
  private boolean startsWithHTTPS;

  /** Sole constructor.
      @param clientSocket The socket for communication with the browser (must not be null). */
  public RequestThread(Socket clientSocket) {
    this.clientSocket = clientSocket;
    this.currentHeaderLines = new ArrayList<>();
    this.serverSocket = null;
    this.headerPort = -1;
    this.hostPort = -1;
    this.startsWithHTTPS = false;
  }

  @Override
  public void run() {
    StringBuilder line = new StringBuilder(); // for reading a header line-by-line
    int curr; // the current byte read from the browser
    boolean sentHeader = false; // true only if the header has been sent (but not payload)
    boolean isConnect = false; // true only if dealing with an HTTP connect request
    boolean first = true;
    String requestName;
    try {
      while ((curr = clientSocket.getInputStream().read()) != -1) {
        if (sentHeader && !isConnect) {
          // Already sent header so just send the rest byte-by-byte.
          serverSocket.getOutputStream().write(curr);
          continue;
        }

        line.append((char) curr);
        if (curr != (int) '\n') {
          // Not at end of line yet.
          continue;
        }

        String lineString = line.toString();
        line = new StringBuilder();

        // get port from request line, -1 if not found
        if (first) {
          String urlString = lineString.split("\\s+")[1].trim();
          if (urlString.toLowerCase().startsWith("https")) {
            startsWithHTTPS = true;
          }
          try {
            URI hostURI = new URI(urlString);
            headerPort = hostURI.getPort();
            requestName = hostURI.getHost();
          } catch (URISyntaxException e) {
            System.out.println("Invalid URI on request line");
            headerPort = -1;
            requestName = null;
          }
          first = false;
        }

        if (lineString.equals("\r\n") || lineString.equals("\n")) {
          // End of header.
          if (isConnect) {
            int port = headerPort;

            // port num not present in either host or request line, check request (http or https)
            if (port == -1) {
              if (startsWithHTTPS) {
                port = 443;
              } else {
            // http, no port specified
                port = 80;
              }
            }

            try {
              serverSocket = new Socket(requestName, port);
              (new ConnectTunnelingThread(clientSocket, serverSocket)).start();
              (new ConnectTunnelingThread(serverSocket, clientSocket)).start();
              clientSocket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
              return;
            } catch (UnknownHostException e) {
              clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
              return;
            }
          } else {
            serverSocket.getOutputStream().write(lineString.getBytes());
            sentHeader = true;
          }
        }

        if (isConnect) {
          // Don't send any part of the header if it's a connect request.
          continue;
        }

        if (lineString.contains("HTTP/1.1")) {
          lineString = lineString.replace("HTTP/1.1", "HTTP/1.0");
        }

        if (serverSocket == null && currentHeaderLines.isEmpty()) {
          // Print the first line of the request.
          System.out.print(">>> " + lineString);
        }

        if (lineString.trim().toLowerCase().startsWith("host")) {
          // Host line.
          String[] ipComponents = lineString.trim().substring(6).trim().split(":");
          if (ipComponents.length == 2) {
            hostPort = Integer.parseInt(ipComponents[1]);
          }

          int port = hostPort;

          if (port == -1) {
            port = headerPort;
          }

          // port num not present in either host or request line, check request (http or https)
          if (port == -1) {
            if (startsWithHTTPS) {
              port = 443;
            } else {
            // http, no port specified
              port = 80;
            }
          }

          if (isConnect) {
            try {
              serverSocket = new Socket(ipComponents[0], port);
              (new ConnectTunnelingThread(clientSocket, serverSocket)).start();
              (new ConnectTunnelingThread(serverSocket, clientSocket)).start();
              clientSocket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
              return;
            } catch (UnknownHostException e) {
              clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
              return;
            }
          } else {
            if (serverSocket == null) {
              serverSocket = new Socket(ipComponents[0], port);
              serverSocket.setSoTimeout(ProxyMain.SO_TIMEOUT_MS);
              (new ResponseThread(clientSocket, serverSocket)).start();
            }

            while (!currentHeaderLines.isEmpty()) {
              serverSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
            }
          }
        } else if (lineString.trim().equalsIgnoreCase("Connection: keep-alive")) {
          lineString = "Connection: close\r\n";
        } else if (lineString.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
          lineString = "Proxy-connection: close\r\n";
        } else if (lineString.trim().toLowerCase().startsWith("connect")) {
          // Connect request
          isConnect = true;
          /*try {
            serverSocket = socketFromString(lineString.split(" ")[1]);
          } catch (UnknownHostException e) {
            clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
            return;
          }*/
          continue;
        }

        // If connected to server, send the header line. Otherwise, buffer it.
        if (serverSocket == null) {
          currentHeaderLines.add(lineString);
        } else {
          serverSocket.getOutputStream().write(lineString.getBytes());
        }
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

  /** Helper function that parses a String for an ip and port number and returns
      a socket connected to that address with ProxyMain.SO_TIMEOUT_MS timeout.
      @param inetAddressString The String to parse for an address (must not be null).
      @return A socket bound to the port specified by the inetAddressString.
      @throws IOException If there is an error creating the socket. */
  private Socket socketFromString(String inetAddressString) throws IOException {
    String[] ipComponents = inetAddressString.split(":");
    String ip = ipComponents[0];
    // -1 for not found
    int iport = -1;

    // get port number from host line
    if (ipComponents.length == 2) {
      iport = Integer.parseInt(ipComponents[1]);
    } 
    
    String uri = (currentHeaderLines.get(0).split("\\s+")[1]).trim();
    if (iport == -1) {
      try {
        // get port number from request line if not found in host line
        URI hostURI = new URI(uri);
        iport = hostURI.getPort();
      } catch (URISyntaxException e) {
        System.out.println("Invalid URI on request line");
        iport = -1;
      }
    }

    // port num not present in either host or request line, check request (http or https)
    if (iport == -1) {
      if (uri.toLowerCase().startsWith("https")) {
        iport = 443;
      } else {
      // http, no port specified
        iport = 80;
      }
    }

    Socket resultSocket = new Socket(ip, iport);
    resultSocket.setSoTimeout(ProxyMain.SO_TIMEOUT_MS);
    return resultSocket;
  }
}