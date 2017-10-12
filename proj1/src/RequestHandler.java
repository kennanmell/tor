package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class RequestHandler {

  private int magicId;
  private int sequenceNo;
  private InetAddress serverAddress;
  private int serverPort;
  private DatagramSocket socket;

  private static RequestHandler sharedInstance = null;

  public static RequestHandler sharedInstance() {
    if (sharedInstance == null) {
      throw new IllegalStateException();
    }
    return sharedInstance;
  }

  public static RequestHandler configureInstance(int magicId,
                                                 DatagramSocket socket,
                                                 InetAddress serverAddress,
                                                 int serverPort) {
    if (sharedInstance != null) {
      throw new IllegalStateException();
    }
    sharedInstance = new RequestHandler();
    sharedInstance.magicId = magicId;
    sharedInstance.socket = socket;
    sharedInstance.serverAddress = serverAddress;
    sharedInstance.serverPort = serverPort;
    return sharedInstance;
  }

  private RequestHandler() {
    this.sequenceNo = 0;
  }

  // Returns true if and only if the probe succeeded (server responded with ACK).
  // Throws an IOException if the request fails for any reason besides a timeout
  // (a timeout causes a false return).
  public boolean probeServer() throws ServiceException {
    byte[] buf = new byte[4];
    buf[0] = (byte) (magicId >> 8);
    buf[1] = (byte) magicId;
    buf[2] = (byte) sequenceNo;
    buf[3] = commandToByte(Command.PROBE);
    try {
      final DatagramPacket request =
          new DatagramPacket(buf, buf.length, serverAddress, serverPort);
      socket.send(request);

      // Block on ack response.
      final DatagramPacket response =
          new DatagramPacket(new byte[4], 4);
      socket.receive(response);
      if (responseIsValid(response) && commandForResponse(response) == Command.ACK) {
        return true;
      } else {
        throw new ServiceException();
      }
    } catch (SocketTimeoutException e) {
      return false;
    } catch (IOException e) {
      throw new ServiceException();
    }
  }

  private boolean responseIsValid(DatagramPacket response) {
    final byte[] buf = response.getData();
    return buf[0] == ((byte) (magicId >> 8)) &&
           buf[1] == ((byte) magicId) &&
           buf[2] == ((byte) sequenceNo);
  }

  private Command commandForResponse(DatagramPacket response) {
    switch (response.getData()[3]) {
    case 1: return Command.REGISTER;
    case 2: return Command.REGISTERED;
    case 3: return Command.FETCH;
    case 4: return Command.FETCHRESPONSE;
    case 5: return Command.UNREGISTER;
    case 6: return Command.PROBE;
    case 7: return Command.ACK;
    default: throw new IllegalArgumentException();
    }
  }

  private byte commandToByte(Command command) {
    switch(command) {
    case REGISTER:      return 1;
    case REGISTERED:    return 2;
    case FETCH:         return 3;
    case FETCHRESPONSE: return 4;
    case UNREGISTER:    return 5;
    case PROBE:         return 6;
    case ACK:           return 7;
    default: throw new IllegalArgumentException();
    }
  }
}
