package proxy;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class SharedDataDistributionThread extends Thread {
  private Socket readSocket;
  private Map<Integer, BlockingQueue<byte[]>> pendingRequests;
  private static SharedDataDistributionThread sharedInstance;

  public SharedDataDistributionThread(Socket readSocket) {
    this.readSocket = readSocket;
    this.pendingRequests = new HashMap<>();
    sharedInstance = this;
  }

  @Override
  public void run() {
    try {
      readSocket.setSoTimeout(0); // remove this later
      while (true) {
        byte[] buf = new byte[512];
        int totalRead = 0;
        while (totalRead < 512) {
          int currentRead = readSocket.getInputStream().read(buf, totalRead, 512 - totalRead);
          if (currentRead == -1) {
            System.out.println("they monster bug killed me");
            System.exit(0);
          }
          totalRead += currentRead;
        }

        int streamId = ((buf[3] & 0xFF) << 8 | (buf[4] & 0xFF));
        synchronized (pendingRequests) {
          if (pendingRequests.containsKey(streamId)) {
            pendingRequests.get(streamId).put(buf);
            buf = new byte[512];
          }
        }
      }
/*
      int curr;
      while ((curr = readSocket.getInputStream().read(buf)) == 512) {
        int streamId = ((buf[3] & 0xFF) << 8 | (buf[4] & 0xFF));
        synchronized (pendingRequests) {
          if (pendingRequests.containsKey(streamId)) {
            pendingRequests.get(streamId).put(buf);
            buf = new byte[512];
          }
        }
      }
      if (curr != -1) {
        System.out.println("the monster bug killed me");
        System.exit(0);
      }
      */
      //System.out.println("DISTRIBUTION ENDED: " + curr);
    } catch (IOException e) {
      System.out.println("the monster bug killed me");
      System.exit(0);
    } catch (InterruptedException e) {
      System.out.println("the monster bug killed me");
      System.exit(0);
    }
  }

  public void removeStream(int id) {
    synchronized (pendingRequests) {
      pendingRequests.remove(id);
    }
  }

  public void addStream(int id, BlockingQueue<byte[]> pending) {
    pendingRequests.put(id, pending);
  }

  public static SharedDataDistributionThread sharedInstance() {
    return sharedInstance;
  }
}
