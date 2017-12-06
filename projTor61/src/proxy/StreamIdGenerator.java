package proxy;

public class StreamIdGenerator {
  private static int nextStreamId = 1;
  private static Object lock = new Object();

  public static int next() {
    synchronized (lock) {
      nextStreamId++;
      if (nextStreamId == 0) {
        nextStreamId++;
      }
      return nextStreamId;
    }
  }
}
