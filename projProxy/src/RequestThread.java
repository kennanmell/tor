package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class RequestThread extends Thread {
  private Socket socket;
  private BufferedReader inBuffer;

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
  }

  @Override
  public void run() {
    List<String> currentHeaderLines = new ArrayList<>();
    Socket clientSocket = null;

    while (true) {
      try {
        String line = inBuffer.readLine();
        if (line == null) {
          continue;
        }

        if (clientSocket == null && currentHeaderLines.isEmpty()) {
          // Print the first line of the request.
          System.out.println(">>> " + line);
        }

        if (line.length() == 1 && line.charAt(0) == ((char) 10) ||
            line.length() == 2 && line.charAt(1) == ((char) 10)) {
          // End of HTTP request.
          currentHeaderLines.clear();
          clientSocket = null;
        } else if (line.trim().toLowerCase().startsWith("host")) {
          // Host line.
          String inetAddressString = line.trim().substring(6).trim();
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

          clientSocket = new Socket(ip, iport);
          System.out.println("debug: new response thread >> " + ip + ":" + iport);
          ResponseThread newThread = new ResponseThread(clientSocket, socket);
          newThread.start();
          while (!currentHeaderLines.isEmpty()) {
            System.out.println("debug: sent line >> " + currentHeaderLines.get(0));
            clientSocket.getOutputStream().write(currentHeaderLines.remove(0).getBytes());
          }
        }

        if (clientSocket == null) {
          currentHeaderLines.add(line);
        } else {
          System.out.println("debug: sent line >> " + line);
          clientSocket.getOutputStream().write(line.getBytes());
        }
      } catch (IOException e) {
        System.out.println("fatal error");
        return;
      }
    }
  }
}
