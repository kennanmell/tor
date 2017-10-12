package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/** Class that handles one request to the server at a time.
    Can be used to register or unregister a Service, probe the server, or
    fetch registrations from the server. The server location must be set with
    `setServer` before requests can be sent. Setting the server location more than
    once results in undefined behavior. All RequestHandler instances use
    the same server but instances can have different auth tokens, sockets,
    and retry counts. */
public class RequestHandler {
  // The maximum number of bytes in a UDP packet (excluding the default header).
  private static final int MAX_UDP_PACKET_SIZE = 65507;
  // A byte array of length 2 representing the ID used to authenticate with the server.
  private byte[] magicId;
  // The socket used to communicate with the server.
  private DatagramSocket socket;
  // The maximum number of times to try a request before considering it failed.
  private int maxAttempts;

  // The IP address of the server.
  private static InetAddress serverAddress = null;
  // The port of the server.
  private static int serverPort = -1;
  // The current sequence number. Increments each time a request is successfully
  // processed by the server.
  private static byte sequenceNo;
  // Used to ensure that commands execute sequentially across all `RequestHandler` instnaces.
  private static Object lock = new Object();

  /** Sets the IP and port of the server to send requests to. Calling more than once
      results in undefined behavior.
      @param serverAddress The IP of the server.
      @param serverPort The port of the server. */
  public static void setServer(InetAddress serverAddress, int serverPort) {
    RequestHandler.serverAddress = serverAddress;
    RequestHandler.serverPort = serverPort;
    RequestHandler.sequenceNo = 0;
  }

  /** Sole initializer.
      @param magicId The 2-byte authentication token to use when sending data to the server.
      @param socket The socket to use to communicate with the server.
      @param maxAttempts The maximum number of times to try a request before giving up.
      @throws IllegalStateException If the server has not been defined by `RequestHandler.setServer` */
  public RequestHandler(int magicId, DatagramSocket socket, int maxAttempts) {
    if (RequestHandler.serverAddress == null) {
      throw new IllegalStateException();
    }
    this.magicId = new byte[2];
    this.magicId[0] = (byte) (magicId >> 8);
    this.magicId[1] = (byte) magicId;
    this.socket = socket;
    this.maxAttempts = maxAttempts;
  }

  /** Registers a `Service` with the server.
      @param service The `Service` to register.
      @return `true` if the registration was confirmed by the server, `false` if
              it was not, presumably because the request(s) timed out.
      @throws ProtocolException If the server responds with an invalid packet
              or there is another IO error. */
  public boolean registerService(Service service) throws ProtocolException {
    synchronized (lock) {
      final int packetSize = 15 + service.name.length(); // 15-byte header
      if (packetSize > MAX_UDP_PACKET_SIZE) {
        // A packet too large to send is a protocol violation.
        throw new ProtocolException();
      }
      byte[] buf = new byte[packetSize];

      System.arraycopy(magicId, 0, buf, 0, 2);

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

      for (int i = 0; i < maxAttempts; i++) {
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
            throw new ProtocolException();
          }
        } catch (SocketTimeoutException e) {
          continue;
        } catch (IOException e) {
          throw new ProtocolException();
        }
      }
      return false;
    }
  }

  /** Unregisters a `Service` with the server.
      @param service The `Service` to unregister.
      @return `true` if the unregistration was confirmed by the server, `false` if
              it was not, presumably because the request(s) timed out.
      @throws ProtocolException If the server responds with an invalid packet
              or there is another IO error. */
  public boolean unregisterService(Service service) throws ProtocolException {
    synchronized (lock) {
      byte[] buf = new byte[10];
      System.arraycopy(magicId, 0, buf, 0, 2);
      buf[2] = sequenceNo;
      buf[3] = commandToByte(Command.UNREGISTER);
      System.arraycopy(service.ip.getAddress(), 0, buf, 4, 4);
      buf[8] = (byte) (service.iport >> 8);
      buf[9] = (byte) service.iport;

      for (int i = 0; i < maxAttempts; i++) {
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
            throw new ProtocolException();
          }
        } catch (SocketTimeoutException e) {
          continue;
        } catch (IOException e) {
          throw new ProtocolException();
        }
      }
      return false;
    }
  }

  /** Probes the server.
      @return `true` if the server responds to the probe, `false` if
              it does not, presumably because the request(s) timed out.
      @throws ProtocolException If the server responds with an invalid packet
              or there is another IO error. */
  public boolean probeServer() throws ProtocolException {
    synchronized (lock) {
      byte[] buf = new byte[4];
      System.arraycopy(magicId, 0, buf, 0, 2);
      buf[2] = sequenceNo;
      buf[3] = commandToByte(Command.PROBE);

      for (int i = 0; i < maxAttempts; i++) {
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
            throw new ProtocolException();
          }
        } catch (SocketTimeoutException e) {
          continue;
        } catch (IOException e) {
          throw new ProtocolException();
        }
      }
      return false;
    }
  }

  /** Returns `true` if and only if the magic ID and sequence number in
      `response` match the magic ID and sequence number of the RequestHandler. */
  private boolean responseIsValid(DatagramPacket response) {
    final byte[] buf = response.getData();
    return buf[0] == magicId[0] &&
           buf[1] == magicId[1] &&
           buf[2] == sequenceNo;
  }

  /** Converts the header in a `DatagramPacket` to a `Command`.
      @return A `Command` associated with the packet.
      @throws IllegalArgumentException if the `Command` is not recognized. */
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

  /** Converts a `Command` to bytes that can be sent in a packet.
      @return A `byte` corresponding to the `Command`.
      @throws IllegalArgumentException if the `Command` is not recognized. */
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
