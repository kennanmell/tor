package proxy;

import java.io.IOException;
import java.net.Socket;

/** RawDataRelayThread reads data byte-by-byte from from a TCP socket until it closes, and
    writes that data byte-by-byte to another TCP socket. Can be used for HTTP connect requests. */
public class RawDataRelayThread extends Thread {
  /// The socket connected to the browser to write data to.
  public final Socket writeSocket;
  /// The socket connected to the server to read data from.
  public final Socket readSocket;
  private int streamId;
  private int circuitId;
  private boolean killed;

  /** Sole constructor.
      @param readSocket The TCP socket to read data from (must not be null).
      @param writeSocket The TCP socket to write data to (must not be null). */
  public RawDataRelayThread(Socket writeSocket, Socket readSocket, int streamId, int circuitId) {
    this.writeSocket = writeSocket;
    this.readSocket = readSocket;
    this.streamId = streamId;
    this.circuitId = circuitId;
    this.killed = false;
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
      int curr;
      while ((curr = readSocket.getInputStream().read()) != -1) {
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
    } catch (IOException e) {
      try {
        readSocket.close();
        writeSocket.close();
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
