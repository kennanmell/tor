package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/** Thread that listens for probe requests from a server and responds
    acknowledging them. */
public class ProbeHandlerThread extends Thread {
  /// Socket used to communicate with the server.
  private DatagramSocket socket;
  /// The size in bytes of a probe or ack packet (including the header).
  private static final int PACKET_SIZE = 4;
  /// The `PROBE` `Command` represented as a byte.
  private static final byte PROBE_TO_BYTE = 6;
  /// The `ACK` `Command` represented as a byte.
  private static final byte ACK_TO_BYTE = 7;
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
    // TODO: finish implementing this. Make sure to implement a callback to AgentMain
    //       when a service is re-registered so that the main can print the registration.
    while (true) {
      try {
        // Wait for a probe request.
        DatagramPacket request =
            new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
        socket.receive(request);
        if (request.getData()[0] == magicId[0] &&
            request.getData()[1] == magicId[1] &&
            request.getData()[3] == PROBE_TO_BYTE) {
          // Valid probe (magicId matches and command is 6=probe). Respond with ack.
          byte[] responseBytes = new byte[PACKET_SIZE];
          responseBytes[0] = magicId[0];
          responseBytes[1] = magicId[1];
          responseBytes[2] = request.getData()[2]; // sequence number
          responseBytes[3] = ACK_TO_BYTE;
          DatagramPacket response = new DatagramPacket(responseBytes,
                                                       PACKET_SIZE,
                                                       request.getAddress(),
                                                       request.getPort());
          socket.send(response);
        }
      } catch (SocketException e) {
        // Presumably interrupted by the main thread closing the socket when the
        // program terminates. Since there's no way to confirm that this was
        // the cause of the exception, if the exception was caused by something
        // else, it is a fatal error and we still have to terminate.
        System.exit(0);
      } catch (IOException e) {
        throw new IllegalStateException();
      }
    }
  }
}
