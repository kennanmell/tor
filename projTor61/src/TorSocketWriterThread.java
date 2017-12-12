package src;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Helper class for SocketManager; one TorSocketWriterThread is running for each SocketManager
    socket. Each instance reads byte arrays from a buffer and writes them to its socket's
    output stream. The SocketManager is responsible for terminating it. */
public class TorSocketWriterThread extends Thread {
  /// The `Socket` to write data to.
  public final Socket socket;
  /// FIFO data to write to the `socket`.
  public final BlockingQueue<byte[]> buf;

  /** Sole constructor.
      @param socket The `Socket` whose output stream should be written to. */
  public TorSocketWriterThread(Socket socket) {
    this.socket = socket;
    this.buf = new LinkedBlockingQueue<>();
  }

  @Override
  public void run() {
    while (true) {
      byte[] curr;
      try {
        curr = buf.take();
      } catch (InterruptedException e) {
        return;
      }

      try {
        socket.getOutputStream().write(curr);
      } catch (IOException e) {
        return;
      }
    }
  }
}
