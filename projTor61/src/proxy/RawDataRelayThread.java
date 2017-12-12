package proxy;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/** RawDataRelayThread reads data byte-by-byte from from a TCP socket until it closes, and
    writes that data byte-by-byte to another TCP socket. Can be used for HTTP connect requests. */
public class RawDataRelayThread extends Thread {
  /// The socket connected to the browser to write data to.
  public final Socket writeSocket;
  /// The socket connected to the server to read data from.
  private BufferedStreamReader reader;
  public final Socket readSocket;
  private int streamId;
  private int circuitId;
  private boolean killed;
  private Map<Integer, RawDataRelayThread> removeWhenDone;
  private BlockingQueue<byte[]> buf;

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
  public RawDataRelayThread(Socket writeSocket, Socket readSocket, int streamId, int circuitId) {
    this.writeSocket = writeSocket;
    try {
      this.reader = new BufferedStreamReader(readSocket.getInputStream());
    } catch (IOException e) {
      reader = null;
      // TODO: handle better
    }
    this.streamId = streamId;
    this.circuitId = circuitId;
    this.killed = false;
    this.readSocket = readSocket;
  }

  public RawDataRelayThread(Socket writeSocket, Socket readSocket, int streamId, int circuitId, Map<Integer, RawDataRelayThread> removeWhenDone, BlockingQueue<byte[]> buf) {
    this(writeSocket, readSocket, streamId, circuitId);
    this.removeWhenDone = removeWhenDone;
    this.buf = buf;
  }

  public RawDataRelayThread(Socket writeSocket, BufferedStreamReader reader, int streamId, int circuitId) {
    this.writeSocket = writeSocket;
    this.reader = reader;
    this.streamId = streamId;
    this.circuitId = circuitId;
    this.killed = false;
    this.readSocket = null;
  }

  @Override
  public void run() {
    byte[] message = new byte[512];
    message[0] = (byte) (circuitId >> 8);
    message[1] = (byte) circuitId;
    message[2] = 3; // relay
    message[3] = (byte) (streamId >> 8);
    message[4] = (byte) streamId;
    message[5] = 0;
    message[6] = 0;
    message[13] = 2; // data

    int offset = 14;

    try {
      byte[] cellData = new byte[512 - 14];
      int curr;
      while ((curr = reader.readChunk(cellData)) != -1) {
        System.arraycopy(cellData, 0, message, 14, curr);
        message[11] = (byte) (curr >> 8);
        message[12] = (byte) curr;
        if (buf == null) {
          writeSocket.getOutputStream().write(message);
        } else {
          byte[] messageCopy = new byte[512];
          System.arraycopy(message, 0, messageCopy, 0, 512);
          buf.add(messageCopy);
        }
      }
      readSocket.close();
      if (removeWhenDone != null) {
        removeWhenDone.remove(this.streamId);
      }
    } catch (IOException e) {
      try {
        readSocket.close();
        if (removeWhenDone != null) {
          removeWhenDone.remove(this.streamId);
        }
        //writeSocket.close();
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }

  public void kill() {
    this.killed = true;
  }
}
