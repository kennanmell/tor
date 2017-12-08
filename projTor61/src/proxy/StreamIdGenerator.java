package proxy;

public class StreamIdGenerator {
  private static int nextStreamId = 1;
  private static Object lock = new Object();

  public static int next() {
    synchronized (lock) {
      nextStreamId++;
      if (nextStreamId == 30000) {
        nextStreamId = 1;
      }
      return nextStreamId;
    }
  }
}
