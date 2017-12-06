package src;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WriteBufferToSocketThread extends Thread {
  public final Socket socket;
  public final BlockingQueue<byte[]> buf;

  public WriteBufferToSocketThread(Socket socket) {
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
        e.printStackTrace();
      }
    }
  }
}
