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
        if (line != null) {
          writeSocket.getOutputStream().write(line.getBytes());
          System.out.println("debug: response sent >> " + line);
        }
      }
    } catch (IOException e) {
      System.out.println("fatal error");
      return;
    }
  }
}
