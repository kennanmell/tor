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

/** RequestThread   */
public class RequestThread extends Thread {
  private Socket socket;
  private List<String> currentHeaderLines;
  private Socket clientSocket;
  private String firstLine;

  public RequestThread(Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException();
    }

    this.socket = socket;
    this.currentHeaderLines = new ArrayList<>();
    this.clientSocket = null;
  }

  @Override
  public void run() {
    StringBuilder line = new StringBuilder();
    boolean isConnect = false;
    boolean sentHeader = false;
    int curr;
    try {
      while ((curr = socket.getInputStream().read()) != -1) {
        // write all bytes without buffering if we've sent the header 
        if (sentHeader && !isConnect) {
          clientSocket.getOutputStream().write(curr);
          continue;
        }

        // build up line
        line.append((char) curr);
        if (curr != (int) '\n') {
          continue;
        }

        String lineString = line.toString();
        line = new StringBuilder();

        if (lineString.contains("HTTP/1.1")) {
          lineString = lineString.replace("HTTP/1.1", "HTTP/1.0");
        } else if (lineString.trim().equalsIgnoreCase("Connection: keep-alive")) {
          lineString = "Connection: close\r\n";
        } else if (lineString.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
          lineString = "Proxy-connection: close\r\n";
        } 
        
        // print first line if we haven't set connect
        if (!isConnect && clientSocket == null && currentHeaderLines.isEmpty()) {
          // Print the first line of the request, store first line
          firstLine = lineString.trim();
          System.out.print(">>> " + lineString);
        }

        // this is a connect request, store state and first line
        if(lineString.trim().toLowerCase().startsWith("connect")) {          
          isConnect = true;
          continue;
        }
        
        // do not buffer header if we are on a CONNECT request.
        // just establish tcp connection, send OK or Bad Gateway, and start tunneling threads.
        if (isConnect) {
          if (lineString.equals("\r\n") || lineString.equals("\n")) {
            if (clientSocket == null) {
              clientSocket = socketFromRequestString((firstLine.split("\\s+")[1]).trim());
            }
          } else if (lineString.trim().toLowerCase().startsWith("host")) {
            if (clientSocket == null) {
              clientSocket = socketFromHostString(lineString.trim().substring(6).trim());
            }
          } else {
            // go until we reach end of request or host header
            continue;
          }
          if (clientSocket == null) {
            System.out.println("error: failed to connect to server for CONNECT request.");
            return;
          }
          (new ConnectTunnelingThread(socket, clientSocket)).start();
          (new ConnectTunnelingThread(clientSocket, socket)).start();
          try {
            socket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
          } catch (UnknownHostException e) {
            socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
          }
          return;
        } 
        
        // handle non-connect request
        if(!isConnect) {          
          // add header to buffer
          currentHeaderLines.add(lineString);

          // reached end of header or host line
          if ((lineString.equals("\r\n") || 
              lineString.equals("\n") || 
              lineString.trim().toLowerCase().startsWith("host")) && clientSocket == null) {
            if (lineString.equals("\r\n") || lineString.equals("\n")) {
              clientSocket = socketFromRequestString((firstLine.split("\\s+")[1]).trim());
            } else {
              // we are on the host header
              clientSocket = socketFromHostString(lineString.trim().substring(6).trim());
            }
            (new ResponseThread(clientSocket, socket)).start();
            // write remaining header
            while (!currentHeaderLines.isEmpty()) {
              clientSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
            }
            // flag for signalling we will now write all remaining bytes byte by byte without buffering
            sentHeader = true;
            continue;
          }
        }
      }
    } catch (IOException e) {
      try {
        socket.close();
        if (clientSocket != null) {
          clientSocket.close();
        }
      } catch (IOException e2) {
         // no op
      }
      return;
    }
  }

  private Socket socketFromHostString(String inetAddressString) throws IOException {
    String[] ipComponents = inetAddressString.split(":");
    String ip = ipComponents[0];
    // -1 for not found
    int iport = -1;

    // get port number from host line
    if (ipComponents.length == 2) {
      iport = Integer.parseInt(ipComponents[1]);
    } 
    
    String uri = (firstLine.split("\\s+")[1]).trim();

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

  private Socket socketFromRequestString(String inetAddressString) throws IOException {
    String ip = null;
    // -1 for not found
    int iport = -1;

    if (iport == -1) {
      try {
        // get port number from request line if not found in host line
        URI hostURI = new URI(inetAddressString);
        iport = hostURI.getPort();
        ip = hostURI.getHost();
      } catch (URISyntaxException e) {
        System.out.println("Invalid URI on request line");
        return null;
      }
    }

    // port num not present in either host or request line, check request (http or https)
    if (iport == -1) {
      if (inetAddressString.toLowerCase().startsWith("https")) {
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