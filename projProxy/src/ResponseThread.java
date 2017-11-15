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
      //BufferedReader inBuffer =
      //    new BufferedReader(new InputStreamReader(readSocket.getInputStream()));
          /*
      String line;
      while ((line = inBuffer.readLine()) != null) {
        line = line.replace("HTTP/1.1", "HTTP/1.0");
        if (line.equals("Connection: keep-alive")) {
          line = "Connection: close";
        } else if (line.equals("Proxy-connection: keep-alive")) {
          line = "Proxy-connection: close";
        }
        line += "\r\n";
        System.out.print(line); // debug
        writeSocket.getOutputStream().write(line.getBytes());
        if (line.equals("\r\n")) {
          break;
        }
      }
*/
      boolean parsedHeader = false;
      String line = "";
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
          if (parsedHeader) {
            writeSocket.getOutputStream().write(curr);
          } else {
            line += (char) curr;
            if (curr == (int) '\n') {
              if (line.trim().equalsIgnoreCase("Connection: keep-alive")) {
                line = "Connection: close\r\n";
              } else if (line.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
                line = "Proxy-connection: close\r\n";
              }
            }
            writeSocket.getOutputStream().write(line.getBytes());
            if (line.equals("\n") || line.equals("\r\n")) {
              parsedHeader = true;
            }
            line = "";
          }
      }
    } catch (IOException e) {
      return;
    }
  }
}
