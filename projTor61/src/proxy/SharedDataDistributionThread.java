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
      byte[] buf = new byte[512];
      while (readSocket.getInputStream().read(buf) == 512) {
        int streamId = ((buf[3] & 0xFF) << 8 | (buf[4] & 0xFF));
        System.out.println("GOT RESPONSE");
        synchronized (pendingRequests) {
          if (pendingRequests.containsKey(streamId)) {
            System.out.println("PUT RESPONSE");
            System.out.println(Arrays.toString(buf));
            System.out.println(new String(buf).substring(14));
            if (buf == null) {
              System.out.println("putting in null buf from shared thread");
            }
            System.out.println("bytes before putting in buffer: ");
            System.out.println(buf.toString());
            pendingRequests.get(streamId).put(buf);
            System.out.println("put buffer in map");
          }
        }
        System.out.println("size of buf: " + buf.length);
      }
      System.out.println("size of buf: " + buf.length);
    } catch (IOException e) {
      // TODO: better error handling
      e.printStackTrace();
      return;
    } catch (InterruptedException e) {
      // TODO: better error handling
      System.out.println("interruptederror");
      return;
    }
  }

  public void removeStream(int id) {
    synchronized (pendingRequests) {
      pendingRequests.remove(id);
    }
  }

  public void addStream(int id, BlockingQueue<byte[]> pending) {
    if (pending == null) {
      System.out.println("Putting in null blocking queue from shared addstream.");
    }
    pendingRequests.put(id, pending);
  }

  public static SharedDataDistributionThread sharedInstance() {
    return sharedInstance;
  }
}
