package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

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

      // Make the buffer to send.
      byte[] buf = filledBufferOfSize(packetSize); // header
      buf[3] = Command.REGISTER.toByte(); // command
      System.arraycopy(service.ip.getAddress(), 0, buf, 4, 4); // ip
      System.arraycopy(ByteBuffer.allocate(2).putShort((short) service.iport).array(), 0, buf, 8, 2); // port
      System.arraycopy(ByteBuffer.allocate(4).putInt(service.data).array(), 0, buf, 10, 4); // data
      buf[14] = (byte) service.name.length(); // name length
      System.arraycopy(service.name.getBytes(), 0, buf, 15, service.name.length()); // name

      // Try to send the buffer and get a response.
      for (int i = 0; i < maxAttempts; i++) {
        try {
          final DatagramPacket request =
              new DatagramPacket(buf, buf.length, serverAddress, serverPort);
          socket.send(request);

          // Block on registered response.
          final DatagramPacket response =
              new DatagramPacket(new byte[6], 6);
          socket.receive(response);
          if (response.getLength() == 6 &&
              responseIsValid(response) &&
              Command.fromByte(response.getData()[3]) == Command.REGISTERED) {
            sequenceNo++;
            service.setLifetime(
                (response.getData()[4] & 0xFF) << 8 | (response.getData()[5] & 0xFF));
            service.setLastRegistrationTimeMs(System.currentTimeMillis());
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
      // Make the buffer to send.
      byte[] buf = filledBufferOfSize(10); // header
      buf[3] = Command.UNREGISTER.toByte(); // command
      System.arraycopy(service.ip.getAddress(), 0, buf, 4, 4); // ip
      System.arraycopy(ByteBuffer.allocate(2).putShort((short) service.iport).array(), 0, buf, 8, 2); // port

      // Try to send the buffer and get a response.
      for (int i = 0; i < maxAttempts; i++) {
        try {
          final DatagramPacket request =
              new DatagramPacket(buf, buf.length, serverAddress, serverPort);
          socket.send(request);

          // Block on ack response.
          final DatagramPacket response =
              new DatagramPacket(new byte[4], 4);
          socket.receive(response);
          if (response.getLength() == 4 &&
              responseIsValid(response) &&
              Command.fromByte(response.getData()[3]) == Command.ACK) {
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

  /** Fetches some subset of services whose names start with 'start'. If 'start'
      is the empty String, any subset of services may be returned.
      @param start The start of each Service name to return.
      @return A list of Services whose names start with 'start', or `null` if the
              server did not respond.
      @throws ProtocolException If the server responds with an invalid packet
              or there is another IO error. */
  public Service[] fetchServicesBeginningWith(String start) throws ProtocolException {
    synchronized (lock) {
      int nameLength = start.length();
      byte[] buf = filledBufferOfSize(5 + nameLength);
      buf[3] = Command.FETCH.toByte();
      buf[4] = (byte) nameLength;
      System.arraycopy(start.getBytes(), 0, buf, 5, nameLength);
      for (int i = 0; i < maxAttempts; i++) {
        try {
          final DatagramPacket request =
              new DatagramPacket(buf, buf.length, serverAddress, serverPort);
          socket.send(request);
          final DatagramPacket response =
              new DatagramPacket(new byte[MAX_UDP_PACKET_SIZE], MAX_UDP_PACKET_SIZE);
          socket.receive(response);
          if (responseIsValid(response) && (response.getLength() == (5 + (10 * response.getData()[4])))
              && response.getLength() < MAX_UDP_PACKET_SIZE 
              && Command.fromByte(response.getData()[3]) == Command.FETCHRESPONSE) {
            sequenceNo++;
            byte[] message = response.getData();
            byte numServices = message[4];
            Service[] services = new Service[numServices];
            for (int j = 0; j < numServices; j++) {
              byte[] ip = new byte[4];
              System.arraycopy(message, (5 + (10 * j)), ip, 0, 4);
              InetAddress inetaddr = InetAddress.getByAddress(ip);
              final byte[] iportBytes = new byte[2];
              System.arraycopy(message, 9 + 10 * j, iportBytes, 0, 2);
              final int port = (int) ByteBuffer.wrap(iportBytes).getShort();
              final byte[] dataBytes = new byte[4];
              System.arraycopy(message, 11 + 10 * j, dataBytes, 0, 4);
              final int data = ByteBuffer.wrap(dataBytes).getInt();
              services[j] = new Service(inetaddr, port, data);
            }
            return services;
          } else {
            throw new ProtocolException();
          }
        } catch (SocketTimeoutException e) {
          continue;
        } catch (IOException e) {
          throw new ProtocolException();
        }
      }
      return null;
    }
  }

  /** Probes the server.
      @return `true` if the server responds to the probe, `false` if
              it does not, presumably because the request(s) timed out.
      @throws ProtocolException If the server responds with an invalid packet
              or there is another IO error. */
  public boolean probeServer() throws ProtocolException {
    synchronized (lock) {
      // Make the buffer to send.
      byte[] buf = filledBufferOfSize(4); // header
      buf[3] = Command.PROBE.toByte(); // command

      // Try to send the buffer and get a response.
      for (int i = 0; i < maxAttempts; i++) {
        try {
          final DatagramPacket request =
              new DatagramPacket(buf, buf.length, serverAddress, serverPort);
          socket.send(request);

          // Block on ack response.
          final DatagramPacket response =
              new DatagramPacket(new byte[4], 4);
          socket.receive(response);
          if (response.getLength() == 4 &&
              responseIsValid(response) &&
              Command.fromByte(response.getData()[3]) == Command.ACK) {
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

  /** Returns a byte array of size `size` with the magic auth token and
      sequence number filled in. */
  private byte[] filledBufferOfSize(int size) {
    byte[] buf = new byte[size];
    System.arraycopy(magicId, 0, buf, 0, 2);
    buf[2] = sequenceNo;
    return buf;
  }

  /** Returns `true` if and only if the magic ID and sequence number in
      `response` match the magic ID and sequence number of the RequestHandler. */
  private boolean responseIsValid(DatagramPacket response) {
    final byte[] buf = response.getData();
    return buf[0] == magicId[0] &&
           buf[1] == magicId[1] &&
           buf[2] == sequenceNo;
  }
}
