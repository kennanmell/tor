package proxy;

public class StreamIdGenerator {
  private static int nextStreamId = 0;
  private static Object lock = new Object();

  public static int next() {
    synchronized (lock) {
      return nextStreamId++;
    }
  }
}
