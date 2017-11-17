package src;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.Date;

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
      System.out.println(iport == -1 ? "invalid port" : "port unavailable");
      return;
    }

    System.out.println("Proxy listening on port " + iport);

    // Listen for eof on stdin.
    (new EofListenerThread()).start();

    // Accept new TCP connections until proxy is closed.
    while (true) {
      try {
        (new HttpRequestThread(serverSocket.accept(), new HttpRequestThread.HttpRequestListener() {
          @Override
          public void onRequestReceived(String firstHeaderLine) {
            System.out.print(new SimpleDateFormat("dd MMM HH:mm:ss").format(new Date()) +
                             " - >>> " + firstHeaderLine);
          }
        })).start();
      } catch (IOException e) {
        continue;
      }
    }
  }
}
