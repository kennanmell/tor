package proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.Date;

/** ProxyMain runs a simple HTTP proxy capable of handling HTTP requests and HTTP connect
    tunneling. Takes a port number as a command line argument. */
public class ProxyThread extends Thread {
  /// The port number to accept connections on.
  private int iport;

  public ProxyThread (int iport) {
    this.iport = iport;
  }

  @Override
  public void run() {
    ServerSocket serverSocket; // A socket that accepts requests and connections.
    try {
      serverSocket = new ServerSocket(iport);
    } catch (Exception e) {
      // TODO
      System.out.println("unable to bind to proxy port");
      return;
    }

    // Accept new TCP connections until proxy is closed.
    while (true) {
      try {
        (new HttpRequestThread(serverSocket.accept(), null)).start();
      } catch (IOException e) {
        continue;
      }
    }
  }
}
