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
    String line;
    boolean isConnect = false;
    try {
      while ((line = inBuffer.readLine()) != null) {
        line += "\r\n";

        if (line.equals("\r\n")) {
          if (isConnect) {
            ConnectTunnelingThread clientToServer = new ConnectTunnelingThread(socket, clientSocket);
            ConnectTunnelingThread serverToClient = new ConnectTunnelingThread(clientSocket, socket);
            clientToServer.start();
            serverToClient.start();
            try {
              socket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
            } catch (UnknownHostException e) {
              socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
            }
          } else {
            System.out.print(line);
            clientSocket.getOutputStream().write(line.getBytes());
          }
          return;
        }

        if (isConnect) {
          continue;
        }

        if (clientSocket == null && currentHeaderLines.isEmpty()) {
          line = line.replace("HTTP/1.1", "HTTP/1.0");

          // Print the first line of the request.
          System.out.print(">>> " + line);
        }

        if (line.trim().toLowerCase().startsWith("host")) {
          // Host line.
          if (clientSocket == null) {
            clientSocket = socketFromString(line.trim().substring(6).trim());
            ConnectTunnelingThread newThread = new ConnectTunnelingThread(clientSocket, socket);
            newThread.start();
          }
          while (!currentHeaderLines.isEmpty()) {
            System.out.print(currentHeaderLines.get(0));
            clientSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
          }
        } else if (line.trim().equalsIgnoreCase("Connection: keep-alive")) {
          line = "Connection: close\r\n";
        } else if (line.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
          line = "Proxy-connection: close\r\n";
        } else if (line.trim().toLowerCase().startsWith("connect")) {
          // Connect request
          isConnect = true;
          try {
            clientSocket = socketFromString(line.split(" ")[1]);
          } catch (UnknownHostException e) {
            socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
            return;
          }
          continue;
        }

        if (clientSocket == null) {
          currentHeaderLines.add(line);
        } else {
          System.out.print(line);
          clientSocket.getOutputStream().write(line.getBytes());
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

    return new Socket(ip, iport);
  }
}
