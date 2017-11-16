package src;

import java.io.*;
import java.net.*;

/** ProxyMain runs a simple HTTP proxy capable of handling HTTP requests and HTTP connect
    tunneling. Takes a port number as a command line argument. */
public class ProxyMain {
  /// The number of ms to wait for a read from a socket before giving up and closing the connection.
  public static final int SO_TIMEOUT_MS = 5000;

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: ./run <port-num>");
      return;
    }

    int iport; // port on localhost to run the proxy on
    try {
      iport = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.out.println("usage: ./run <port-num>");
      return;
    }

    ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(iport);
    } catch (Exception e) {
      System.out.println("Unable to bind to server port.");
      return;
    }

    System.out.println("Proxy listening on 0.0.0.0:" + iport);

    // Listen for eof on stdin.
    (new EofListenerThread()).start();

    // Accept new TCP connections until proxy is closed.
    while (true) {
      try {
        Socket newSocket = serverSocket.accept();
        newSocket.setSoTimeout(SO_TIMEOUT_MS);
        (new RequestThread(newSocket)).start();
      } catch (IOException e) {
        e.printStackTrace();
        System.out.println("fatal error");
        return;
      }
    }
  }
}
