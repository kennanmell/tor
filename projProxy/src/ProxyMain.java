package src;

import java.io.*;
import java.net.*;

public class ProxyMain {
  public static final int SO_TIMEOUT_MS = 5000;

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: ./run <port-num>");
      return;
    }

    int iport;
    try {
      iport = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.out.println("usage: ./run <port-num>");
      return;
    }

    ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(iport);
    } catch (IOException e) {
      System.out.println("fatal error");
      return;
    } catch (IllegalArgumentException e) {
      System.out.println("Port value out of range.");
      return;
    }

    System.out.println("Proxy listening on 0.0.0.0:" + iport);

    (new EofListenerThread()).start();

    while (true) {
      try {
        Socket newSocket = serverSocket.accept();
        newSocket.setSoTimeout(SO_TIMEOUT_MS);
        (new RequestThread(newSocket)).start();
      } catch (IOException e) {
        System.out.println("fatal error");
        return;
      }
    }
  }
}
