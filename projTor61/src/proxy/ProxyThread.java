package proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

// Assign stream ID to each new HTTPRequest thread

/** ProxyMain runs a simple HTTP proxy capable of handling HTTP requests and HTTP connect
    tunneling. Takes a port number as a command line argument. */
public class ProxyThread extends Thread {
  /// The port number to accept connections on.
  public static SharedDataDistributionThread distributorThread;
  private int iport;
  public final int circuitId;
  private Socket gatewaySocket;
  private static ProxyThread sharedInstance;

  public ProxyThread(int iport, int circuitId, Socket gatewaySocket) {
    this.iport = iport;
    this.circuitId = circuitId;
    this.gatewaySocket = gatewaySocket;
    try {
      gatewaySocket.setSoTimeout(5000);
    } catch (IOException e) {
      // no op
    }
    ProxyThread.sharedInstance = this;
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

    ProxyThread.distributorThread = new SharedDataDistributionThread(gatewaySocket);
    distributorThread.start();

    // Accept new TCP connections until proxy is closed.
    while (true) {
      try {
        (new HttpRequestThread(serverSocket.accept(), null, gatewaySocket)).start();
      } catch (IOException e) {
        continue;
      }
    }
  }

  public Socket getGatewaySocket() {
    return this.gatewaySocket;
  }

  public void setGatewaySocket(Socket gatewaySocket) {
    if (gatewaySocket == null) {
      throw new IllegalArgumentException();
    }
    this.gatewaySocket = gatewaySocket;
    try {
      gatewaySocket.setSoTimeout(5000);
    } catch (IOException e) {
      // no op
    }
  }

  public static ProxyThread sharedInstance() {
    return ProxyThread.sharedInstance;
  }
}
