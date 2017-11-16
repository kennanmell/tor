package src;

import java.io.*;
import java.net.*;

/** ProxyMain runs a simple HTTP proxy capable of handling HTTP requests and HTTP connect
    tunneling. Takes a port number as a command line argument. */
public class ProxyMain {
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: ./run <port-num>");
      return;
    }

    ServerSocket serverSocket; // A socket that accepts requests and connections.
    int iport = -1;
    try {
      iport = Integer.parseInt(args[0]);
      serverSocket = new ServerSocket(iport);
    } catch (Exception e) {
      if (iport == -1) {
        System.out.println("usage: ./run <port-num>");
        System.out.println("specified invalid port.");
      } else {
        System.out.println("port unavailable: " + iport);
      }
      return;
    }

    System.out.println("Proxy listening on port " + iport);

    // Listen for eof on stdin.
    (new EofListenerThread()).start();

    // Accept new TCP connections until proxy is closed.
    while (true) {
      try {
        (new RequestThread(serverSocket.accept())).start();
      } catch (IOException e) {
        continue;
      }
    }
  }
}
