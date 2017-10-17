package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/** Thread that listens for probe requests from a server and responds by
    acknowledging them. */
public class ProbeHandlerThread extends Thread {
  /// Socket used to communicate with the server.
  private DatagramSocket socket;
  /// The size in bytes of a probe or ack packet (including the header).
  private static final int PACKET_SIZE = 4;
  /// A 2-byte id used to authenticate with the server.
  private byte[] magicId;
  private Callback callback;

  /** Creates a new ProbeHandlerThread.
      @param socket The socket used to communicate with the server.
      @param callback a Callback containing callback methods
      @param magicId A 2-byte id used to authenticate with the server, represented as an int.
      @throws IllegalArgumentException if `socket` is `null`. */
  public ProbeHandlerThread(DatagramSocket socket, int magicId, Callback callback) {
    if (socket == null) {
      throw new IllegalArgumentException();
    }
    this.callback = callback;
    this.socket = socket;
    this.magicId = new byte[2];
    this.magicId[0] = (byte) (magicId >> 8);
    this.magicId[1] = (byte) magicId;
  }

  /** Creates a new ProbeHandlerThread.
      @param socket The socket used to communicate with the server.
      @param magicId A 2-byte id used to authenticate with the server, represented as an int.
      @throws IllegalArgumentException if `socket` is `null`. */
  public ProbeHandlerThread(DatagramSocket socket, int magicId) {
    this(socket, magicId, null);
  }

  @Override
  public void run() {
    while (true) {
      try {
        // Wait for a probe request.
        DatagramPacket request = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
        socket.receive(request);
        if (request.getData()[0] == magicId[0] &&
            request.getData()[1] == magicId[1] &&
            request.getData()[3] == Command.PROBE.toByte()) {
          // Valid probe (magicId and command match). Respond with ack.
          byte[] buf = new byte[PACKET_SIZE];
          System.arraycopy(magicId, 0, buf, 0, 2);
          buf[2] = request.getData()[2]; // sequence number
          buf[3] = Command.ACK.toByte(); // command
          DatagramPacket response = new DatagramPacket(buf,
                                                       PACKET_SIZE,
                                                       request.getAddress(),
                                                       request.getPort());
          socket.send(response);
          if (this.callback != null) {
            this.callback.onSuccess();
          }
        }
      } catch (SocketException e) {
        // Main thread is closing so we can stop.
        return;
      } catch (IOException e) {
        if (this.callback != null) {
          this.callback.onFailure();
        }
        System.exit(0);
      }
    }
  }
}
