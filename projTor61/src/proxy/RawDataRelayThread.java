package proxy;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;

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

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
  public RawDataRelayThread(Socket writeSocket, Socket readSocket, int streamId, int circuitId) {
    System.out.println(this + " started A");
    this.writeSocket = writeSocket;
    try {
      this.reader = new BufferedStreamReader(readSocket.getInputStream());
    } catch (IOException e) {
      System.out.println("FATAL ERROR!!!");
      e.printStackTrace();
      reader = null;
      // TODO: handle better
    }
    this.streamId = streamId;
    this.circuitId = circuitId;
    this.killed = false;
    this.readSocket = readSocket;
  }

  public RawDataRelayThread(Socket writeSocket, Socket readSocket, int streamId, int circuitId, Map<Integer, RawDataRelayThread> removeWhenDone) {
    this(writeSocket, readSocket, streamId, circuitId);
    System.out.println(this + " started B");
    this.removeWhenDone = removeWhenDone;
  }

  public RawDataRelayThread(Socket writeSocket, BufferedStreamReader reader, int streamId, int circuitId) {
    System.out.println(this + " started C");
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
    message[11] = (byte) ((512 - 14) >> 8);
    message[12] = (byte) (512 - 14);
    message[13] = 2; // data

    int offset = 14;

    try {
      byte[] cellData = new byte[512 - 14];
      int curr;
      while ((curr = reader.readChunk(cellData)) == (512 - 14)) {
        System.arraycopy(cellData, 0, message, 14, 512 - 14);
        writeSocket.getOutputStream().write(message);
      }

      if (curr > 0) {
        System.arraycopy(cellData, 0, message, 14, curr);
        for (int i = 14 + curr; i < 512; i++) {
          message[i] = 0;
        }
        message[11] = (byte) (curr >> 8);
        message[12] = (byte) curr;
        writeSocket.getOutputStream().write(message);
      }
      /*
      int curr;
      while ((curr = reader.read()) != -1) {
        System.out.println(this + " read");
        message[offset] = (byte) curr;
        offset++;
        if (offset == 512) {
          writeSocket.getOutputStream().write(message);
          offset = 14;
        }
        if (killed) {
          break;
        }
      }

      if (offset != 14) {
        message[11] = (byte) ((offset - 14) >> 8);
        message[12] = (byte) (offset - 14);
        for (int i = offset; i < 512; i++) {
          message[i] = 0;
        }
        writeSocket.getOutputStream().write(message);
      }
      */
      readSocket.close();
      if (removeWhenDone != null) {
        removeWhenDone.remove(this.streamId);
      }
    } catch (IOException e) {
      System.out.println(this + " exception");
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
