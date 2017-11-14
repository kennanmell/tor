package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;

public class ResponseThread extends Thread {
  private Socket readSocket;
  private Socket writeSocket;

  public ResponseThread(Socket readSocket, Socket writeSocket) {
    this.readSocket = readSocket;
    this.writeSocket = writeSocket;
  }

  @Override
  public void run() {
    try {
      String line = "";
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
        line += (char) curr;
        if (curr == (int) '\n') {
          if (line.trim().equals("Connection: keep-alive")) {
            line = "Connection: close\r\n";
          } else if (line.equals("Proxy-connection: keep-alive")) {
            line = "Proxy-connection: close\r\n";
          }
          System.out.print(line); // debug
          writeSocket.getOutputStream().write(line.getBytes());
          line = "";
        }
      }
    } catch (IOException e) {
      return;
    }
  }
}
