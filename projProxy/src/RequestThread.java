package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class RequestThread extends Thread {
  private Socket socket;
  private BufferedReader inBuffer;
  private List<String> currentHeaderLines;
  private Socket clientSocket;

  public RequestThread(Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException();
    }

    this.socket = socket;
    try {
      this.inBuffer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    } catch (IOException e) {
      System.out.println("fatal error");
    }

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
        if (sentHeader && !isConnect) {
          clientSocket.getOutputStream().write(curr);
          continue;
        }

        line.append((char) curr);
        if (curr != (int) '\n') {
          continue;
        }

        String lineString = line.toString();
        line = new StringBuilder();
        if (lineString.equals("\r\n") || lineString.equals("\n")) {
          if (isConnect) {
            (new ConnectTunnelingThread(socket, clientSocket)).start();
            (new ConnectTunnelingThread(clientSocket, socket)).start();
            try {
              socket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
            } catch (UnknownHostException e) {
              socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
            }
            return;
          } else {
            System.out.print(line);
            clientSocket.getOutputStream().write(lineString.getBytes());
            sentHeader = true;
          }
        }

        if (isConnect) {
          continue;
        }

        if (lineString.contains("HTTP/1.1")) {
          lineString = lineString.replace("HTTP/1.1", "HTTP/1.0");
        }

        if (clientSocket == null && currentHeaderLines.isEmpty()) {
          // Print the first line of the request.
          System.out.print(">>> " + line);
        }

        if (lineString.trim().toLowerCase().startsWith("host")) {
          // Host line.
          if (clientSocket == null) {
            clientSocket = socketFromString(lineString.trim().substring(6).trim());
            (new ResponseThread(clientSocket, socket)).start();
          }
          while (!currentHeaderLines.isEmpty()) {
            System.out.print(currentHeaderLines.get(0));
            clientSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
          }
        } else if (lineString.trim().equalsIgnoreCase("Connection: keep-alive")) {
          lineString = "Connection: close\r\n";
        } else if (lineString.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
          lineString = "Proxy-connection: close\r\n";
        } else if (lineString.trim().toLowerCase().startsWith("connect")) {
          // Connect request
          isConnect = true;
          try {
            clientSocket = socketFromString(lineString.split(" ")[1]);
          } catch (UnknownHostException e) {
            socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
            return;
          }
          continue;
        }

        if (clientSocket == null) {
          currentHeaderLines.add(lineString);
        } else {
          System.out.print(lineString);
          clientSocket.getOutputStream().write(lineString.getBytes());
        }
      }
    } catch (IOException e) {
      System.out.println("fatal error");
      return;
    }
  }

  private Socket socketFromString(String inetAddressString) throws IOException {
    String[] ipComponents = inetAddressString.split(":");

    String ip = ipComponents[0];
    int iport;
    if (ipComponents.length == 2) {
      iport = Integer.parseInt(ipComponents[1]);
    } else if (currentHeaderLines.get(0) == "") {
      // TODO: fix check and find port in first line
      iport = 80;
    } else if (currentHeaderLines.get(0).split(" ")[1].toLowerCase().startsWith("https")) {
      iport = 443;
    } else {
      // http, no port specified
      iport = 80;
    }

    Socket resultSocket = new Socket(ip, iport);
    resultSocket.setSoTimeout(ProxyMain.SO_TIMEOUT_MS);
    return resultSocket;
  }
}
