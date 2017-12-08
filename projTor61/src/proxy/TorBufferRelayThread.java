package proxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** RawDataRelayThread reads data byte-by-byte from from a TCP socket until it closes, and
    writes that data byte-by-byte to another TCP socket. Can be used for HTTP connect requests. */
public class TorBufferRelayThread extends Thread {
  /// The socket connected to the browser to write data to.
  private Socket writeSocket;
  /// The buffer connected to tor to read from.
  private BlockingQueue<byte[]> buf;
  private int streamId;

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
  public TorBufferRelayThread(Socket writeSocket, BlockingQueue<byte[]> buf, int streamId) {
    this.writeSocket = writeSocket;
    this.buf = buf;
    this.streamId = streamId;
  }

  @Override
  public void run() {
    while (true) {
      try {
        byte[] curr = buf.poll(25000, TimeUnit.MILLISECONDS);
        if (Curr == null) {
          System.out.println("buff is null");
        }
        System.out.println("length of request: " + curr.length);
        if (curr[2] == 3 && curr[13] == 2) { // relay data
          int length = ((curr[11] & 0xFF) << 8 | (curr[12] & 0xFF));
          for (int i = 14; i < 14 + length; i++) {
            writeSocket.getOutputStream().write(curr[i]);
          }
        } else {
          // most likely relay end, but we're done regardless
          break;
        }
      } catch (IOException e) {
          break;
      } catch (InterruptedException e) {
          break;
      }
    }
  }
}
