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
      BufferedReader inBuffer =
          new BufferedReader(new InputStreamReader(readSocket.getInputStream()));
      while (true) {
        String line = inBuffer.readLine();
        if (line.equals("Connection: keep-alive")) {
          line = "Connection: close";
        } else if (line.equals("Proxy-connection: keep-alive")) {
          line = "Proxy-connection: close";
        }
        line += "\n";
        writeSocket.getOutputStream().write(line.getBytes());
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("fatal error");
      return;
    }
  }
}
