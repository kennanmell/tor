package src;

import java.io.*;
import java.net.*;

public class ProxyMain {
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
      System.out.println("fatal error 1");
      return;
    }

    System.out.println("Proxy listening on 0.0.0.0:" + iport);

    while (true) {
      try {
        RequestThread newThread = new RequestThread(serverSocket.accept());
        newThread.start();
      } catch (IOException e) {
        System.out.println("fatal error 2");
        return;
      }
    }
  }
}
