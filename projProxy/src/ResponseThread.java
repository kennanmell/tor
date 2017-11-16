package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

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
      boolean sentHeader = false;
      StringBuilder line = new StringBuilder();
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
        if (sentHeader) {
          writeSocket.getOutputStream().write(curr);
        } else {
          line.append((char) curr);
          if (curr == (int) '\n') {
            String lineString = line.toString();
            if (lineString.contains("HTTP/1.1")) {
              line = new StringBuilder(lineString.replace("HTTP/1.1", "HTTP/1.0"));
            } else if (lineString.trim().equalsIgnoreCase("Connection: keep-alive")) {
              line = new StringBuilder("Connection: close\r\n");
            } else if (lineString.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
              line = new StringBuilder("Proxy-connection: close\r\n");
            }
            writeSocket.getOutputStream().write(line.toString().getBytes());
            if (lineString.equals("\n") || lineString.equals("\r\n")) {
              sentHeader = true;
            }
            line = new StringBuilder();
          }
        }
      }
    } catch (IOException e) {
      try {
        readSocket.close();
        writeSocket.close();
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }
}
