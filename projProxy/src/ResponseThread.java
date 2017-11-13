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
        if (line == null) {
          System.out.println("null");
          continue;
        }
        if (line.equals("Connection: keep-alive")) {
          line = "Connection: close";
        } else if (line.equals("Proxy-connection: keep-alive")) {
          line = "Proxy-connection: close";
        }
        line += "\n";
        System.out.print(line);
        writeSocket.getOutputStream().write(line.getBytes());

        if ((line.length() == 1 && line.charAt(0) == '\n') ||
            (line.length() == 2 && line.charAt(1) == '\n')) {
          // End of HTTP response.
          return;
        }
      }
    } catch (IOException e) {
      return;
    }
  }
}
