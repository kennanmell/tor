package proxy;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SharedDataDistributionThread extends Thread {
  private Socket readSocket;
  private Map<Integer, HttpRequestThread> pendingRequests;
  public static SharedDataDistributionThread sharedInstance;

  public SharedDataDistributionThread(Socket readSocket) {
    this.readSocket = readSocket;
    this.pendingRequests = new HashMap<>();
  }

  @Override
  public void run() {
    try {
      byte[] buf = new byte[512];
      while (readSocket.getInputStream().read(buf) == 512) {
        int streamId = ((buf[3] & 0xFF) << 8 | (buf[4] & 0xFF));
        synchronized (pendingRequests) {
          if (pendingRequests.containsKey(streamId)) {
            pendingRequests.get(streamId).buf.put(buf);
          }
        }
      }
    } catch (IOException e) {
      // TODO: better error handling
      System.exit(0);
    } catch (InterruptedException e) {
      // TODO: better error handling
      System.exit(0);
    }
  }

  public void removeStream(int id) {
    synchronized (pendingRequests) {
      pendingRequests.remove(id);
    }
  }
}
