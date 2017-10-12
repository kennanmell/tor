package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ProbeHandlerThread extends Thread {
  private DatagramSocket socket;
  private static final int PACKET_SIZE = 4;
  private static final byte PROBE_TO_BYTE = 6;
  private static final byte ACK_TO_BYTE = 7;
  private int magicId;

  public ProbeHandlerThread(DatagramSocket socket, int magicId) {
    this.socket = socket;
    this.magicId = magicId;
  }

  @Override
  public void run() {
    while (true) {
      try {
        // Wait for a probe request.
        DatagramPacket request =
            new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
        socket.receive(request);
        if (request.getData()[0] == ((byte) (magicId >> 8)) &&
            request.getData()[1] == ((byte) (magicId)) &&
            request.getData()[3] == PROBE_TO_BYTE) {
          // Valid probe (magicId matches and command is 6=probe). Ack.
          byte[] responseBytes = new byte[PACKET_SIZE];
          responseBytes[0] = (byte) (magicId >> 8);
          responseBytes[1] = (byte) magicId;
          responseBytes[2] = request.getData()[2]; // sequence number
          responseBytes[3] = ACK_TO_BYTE;
          DatagramPacket response = new DatagramPacket(responseBytes,
                                                       PACKET_SIZE,
                                                       request.getAddress(),
                                                       request.getPort());
          socket.send(response);
        }
      } catch (IOException e) {
        throw new IllegalStateException();
      }
    }
  }
}
