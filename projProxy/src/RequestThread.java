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
      System.out.println("fatal error 4");
    }

    this.currentHeaderLines = new ArrayList<>();
    this.clientSocket = null;
  }

  @Override
  public void run() {
    String line = "";
    int curr;
    boolean isConnect = false;
    try {
      while ((curr = socket.getInputStream().read()) != -1) {
        System.out.println("in loop");
        line += (char) curr;
        if (curr != (int) '\n') {
          System.out.println("in if");
          continue;
        }
        //String line = inBuffer.readLine() + "\n";
        //if (line == null) {
        //  continue;
        //}

        if (line.equals("\r\n") || line.equals("\n")) {
          System.out.println(); // debug
          clientSocket.getOutputStream().write(line.getBytes());
          return;
        }

        if (clientSocket == null && currentHeaderLines.isEmpty()) {
          line = line.replace("HTTP/1.1", "HTTP/1.0");

          // Print the first line of the request.
          //System.out.print(">>> " + line);
        }

        if (line.trim().toLowerCase().startsWith("host")) {
          // Host line.
          if (clientSocket == null) {
            clientSocket = socketFromString(line.trim().substring(6).trim());
            try {
              ResponseThread newThread = new ResponseThread(clientSocket, socket);
              newThread.start();
              if (isConnect) {
                socket.getOutputStream().write("HTTP/1.0 200 OK\r\n".getBytes()); // TODO: need another \n?
              }
            } catch (UnknownHostException e) {
              if (isConnect) {
                socket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n".getBytes()); // need another \n?
              }
            }
          }
          while (!currentHeaderLines.isEmpty()) {
            System.out.print(currentHeaderLines.get(0)); // debug
            clientSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
          }
        } else if (line.equalsIgnoreCase("Connection: keep-alive\r\n")) {
          line = "Connection: close\n";
        } else if (line.equalsIgnoreCase("Proxy-connection: keep-alive\r\n")) {
          line = "Proxy-connection: close\n";
        } else if (line.trim().toLowerCase().startsWith("connect")) {
          // Connect request
          isConnect = true;
          /*
          try {
            clientSocket = socketFromString(line.split(" ")[1]);
            ResponseThread newThread = new ResponseThread(clientSocket, socket);
            newThread.start();
            clientSocket.getOutputStream().write("HTTP/1.0 200 OK\r\n".getBytes()); // TODO: need another \n?
          } catch (UnknownHostException e) {
            clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n".getBytes()); // need another \n?
          }
          */
          continue;
        }

        if (clientSocket == null) {
          currentHeaderLines.add(line);
        } else {
          System.out.print(line); // debug
          clientSocket.getOutputStream().write(line.getBytes());
        }

        //if ((line.length() == 1 && line.charAt(0) == '\n') ||
        //    (line.length() == 2 && line.charAt(1) == '\n')) {
        //  // End of HTTP request.
        //  return;
          //currentHeaderLines.clear();
          //clientSocket = null;
        //}
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("fatal error 3");
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
