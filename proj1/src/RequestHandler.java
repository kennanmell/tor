package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class RequestHandler {

  private int magicId;
  private byte sequenceNo;
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

  public boolean registerService(Service service) throws ServiceException {
    byte[] buf = new byte[200];

    buf[0] = (byte) (magicId >> 8);
    buf[1] = (byte) magicId;

    buf[2] = sequenceNo;
    buf[3] = commandToByte(Command.REGISTER);

    System.arraycopy(service.ip.getAddress(), 0, buf, 4, 4);

    buf[8] = (byte) (service.iport >> 8);
    buf[9] = (byte) service.iport;

    buf[10] = (byte) (service.data >> 24);
    buf[11] = (byte) (service.data >> 16);
    buf[12] = (byte) (service.data >> 8);
    buf[13] = (byte) (service.data);

    buf[14] = (byte) service.name.length();

    System.arraycopy(service.name.getBytes(), 0, buf, 15, service.name.length());

    try {
      final DatagramPacket request =
          new DatagramPacket(buf, buf.length, serverAddress, serverPort);
      socket.send(request);

      // Block on registered response.
      final DatagramPacket response =
          new DatagramPacket(new byte[6], 6);
      socket.receive(response);
      if (responseIsValid(response) && commandForResponse(response) == Command.REGISTERED) {
        sequenceNo++;
        service.setLifetime(
            (response.getData()[4] & 0xFF) << 8 | (response.getData()[5] & 0xFF));
        service.setLastRegistrationTimeMs(System.currentTimeMillis());
        // TODO: add automatic re-registration before expiration
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

  public boolean unregisterService(Service service) throws ServiceException {
    byte[] buf = new byte[10];
    buf[0] = (byte) (magicId >> 8);
    buf[1] = (byte) magicId;
    buf[2] = sequenceNo;
    buf[3] = commandToByte(Command.UNREGISTER);
    System.arraycopy(service.ip.getAddress(), 0, buf, 4, 4);
    buf[8] = (byte) (service.iport >> 8);
    buf[9] = (byte) service.iport;
    try {
      final DatagramPacket request =
          new DatagramPacket(buf, buf.length, serverAddress, serverPort);
      socket.send(request);

      // Block on ack response.
      final DatagramPacket response =
          new DatagramPacket(new byte[4], 4);
      socket.receive(response);
      if (responseIsValid(response) && commandForResponse(response) == Command.ACK) {
        sequenceNo++;
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

  // Returns true if and only if the probe succeeded (server responded with ACK).
  // Throws a ServiceException if the request fails for any reason besides a timeout
  // (a timeout causes a false return).
  public boolean probeServer() throws ServiceException {
    byte[] buf = new byte[4];
    buf[0] = (byte) (magicId >> 8);
    buf[1] = (byte) magicId;
    buf[2] = sequenceNo;
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
        sequenceNo++;
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
           buf[2] == sequenceNo;
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
