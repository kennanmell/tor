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
    this.removeWhenDone = removeWhenDone;
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
    message[11] = (byte) ((512 - 14) >> 8);
    message[12] = (byte) (512 - 14);
    message[13] = 2; // data

    int offset = 14;

    try {
      int curr;
      while ((curr = reader.read()) != -1) {
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
      // no op
    }
/*
    try {
      readSocket.close();
      if (removeWhenDone != null) {
        removeWhenDone.remove(this.streamId);
      }
      //writeSocket.close();
    } catch (IOException e2) {
      // no op
    }*/
  }

  public void kill() {
    this.killed = true;
  }
}
