package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/** Thread that listens for probe requests from a server and responds
    acknowledging them. */
public class ProbeHandlerThread extends Thread {
  /// Socket used to communicate with the server.
  private DatagramSocket socket;
  /// The size in bytes of a probe or ack packet (including the header).
  private static final int PACKET_SIZE = 4;
  /// A 2-byte id used to authenticate with the server.
  private byte[] magicId;

  /** Creates a new ProbeHandlerThread.
      @param socket The socket used to communicate with the server.
      @param magicId A 2-byte id used to authenticate with the server, represented as an int.
      @throws IllegalArgumentException if `socket` is `null`. */
  public ProbeHandlerThread(DatagramSocket socket, int magicId) {
    if (socket == null) {
      throw new IllegalArgumentException();
    }
    this.socket = socket;
    this.magicId = new byte[2];
    this.magicId[0] = (byte) (magicId >> 8);
    this.magicId[1] = (byte) magicId;
  }

  @Override
  public void run() {
    while (true) {
      try {
        // Wait for a probe request.
        DatagramPacket request =
            new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
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
        }
      } catch (IOException e) {
        // Presumably a SocketException because we were interrupted by the main
        // thread closing the socket when the program terminates. However, if
        // it was another error, we still need to shut down because the state
        // is irrecoverable.
        System.exit(0);
      }
    }
  }
}
