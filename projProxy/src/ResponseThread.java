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
      System.out.println("running response thread");
      while (true) {
        int b = readSocket.getInputStream().read();
        writeSocket.getOutputStream().write(b);
        System.out.println("debug: response sent >> " + b);
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("fatal error");
      return;
    }
  }
}
