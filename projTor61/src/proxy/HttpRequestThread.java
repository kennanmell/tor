package proxy;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// This thread (sends to tor socket RouterEntry.getSocket() gatewayEntry):
// Relay BEGIN w/ server ip:port (get stream id from router info)
//     Wait for response
//         Connected:
//            Relay data
//            get response from buffer
//         BEGIN failed or timeout:
//            close client socket, done

// OTHER END:
//     Use stream id to demultiplex and send to appropriate server
//     -how to multiplex circuits on TCP connections?
//     Routing to next tor cell -> control cell, use circuit number
//     Routing to web server -> relay cell, use stream number

/*
// TODO: make sure proxy closes all connections

// Other thread:
// Read from tor socket RouterEntry.getSocket() gatewayEntry
// Write to buffer associated with stream id (LinkedBlockingQueue)

/** HttpRequestThread sends one HTTP or HTTP connect request from the browser client to the
    server, then sends the response from the server to the browser. The request
    will always be downgraded to HTTP/1.0 and Connection: close. If the request is
    for connect, it starts two threads to relay the data in both directions. */
public class HttpRequestThread extends Thread {
  /** Callbacks for events in a HttpRequestThread. */
  public interface HttpRequestListener {
    /** Called when a request is received from the client socket.
        @param firstHeaderLine The first line of the request header. */
    public void onRequestReceived(String firstHeaderLine);
  }
  /// The number of ms to wait for a read from a socket before giving up and closing the connection.
  public static final int SO_TIMEOUT_MS = 5000;

  /// The socket for communication with the browser client.
  private Socket clientSocket;
  /// An HttpRequestListener that is called when events occur in this thread.
  private HttpRequestListener listener;
  /// Buffer for communication with the server via tor.
  public BlockingQueue<byte[]> buf;
  final int streamId;

  /** Sole constructor.
      @param clientSocket The socket for communication with the browser (must not be null).
      @param listener The HttpRequestListener to call when events occur in this thread. */
  public HttpRequestThread(Socket clientSocket, HttpRequestListener listener) {
    try {
      clientSocket.setSoTimeout(SO_TIMEOUT_MS);
    } catch (IOException e) {
      // no op
    }
    this.clientSocket = clientSocket;
    this.listener = listener;
    this.buf = new LinkedBlockingQueue<>();
    this.streamId = StreamIdGenerator.next();
    SharedDataDistributionThread.sharedInstance().addStream(this.streamId, this.buf);
  }

  @Override
  public void run() {
    try {
      BufferedStreamReader reader = new BufferedStreamReader(clientSocket.getInputStream());
      String line = reader.readLine();
      if (line == null) {
        SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
        return;
      }

      if (listener != null) {
        listener.onRequestReceived(modifyHttpHeaderLine(line));
      }

      List<String> bufferedLines = new ArrayList<>();
      bufferedLines.add(line);
      boolean connection = beginRelayWithHttpHeader(reader, bufferedLines);

      if (line.trim().toLowerCase().startsWith("connect")) {
        if (!connection) {
          clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
          SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
          return;
        }

        if (!bufferedLines.get(bufferedLines.size() - 1).equals("\n") &&
            !bufferedLines.get(bufferedLines.size() - 1).equals("\r\n")) {
          String tempLine;
          while ((tempLine = reader.readLine()) != null) {
            // Flush the rest of the header.
            if (tempLine.equals("\n") || tempLine.equals("\r\n")) {
              break;
            }
          }
        }

        clientSocket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
        clientSocket.setSoTimeout(0);
        (new RawDataRelayThread(
            ProxyThread.sharedInstance().getGatewaySocket(), clientSocket, streamId)).start();
        (new TorBufferRelayThread(clientSocket, buf, streamId)).run();
        SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
      } else {
        if (!connection) {
          SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
          return;
        }

        while (!bufferedLines.isEmpty()) {
          // Send the lines of the header that have been read.
          ProxyThread.sharedInstance().getGatewaySocket().getOutputStream().write(
              modifyHttpHeaderLine(bufferedLines.remove(0)).getBytes());
        }

        handleHttpMessage(clientSocket, ProxyThread.sharedInstance().getGatewaySocket());
        handleHttpResponse(clientSocket);
        SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
      }
    } catch (IOException e) {
      try {
        clientSocket.close();
      } catch (IOException e2) {
        // no op
      }

      SharedDataDistributionThread.sharedInstance().removeStream(this.streamId);
      return;
    }
  }

  /** Handles an HTTP header and body sent from readSocket to writeSocket. */
  private void handleHttpMessage(Socket readSocket, Socket writeSocket) throws IOException {
    byte[] message = new byte[512];
    message[0] = (byte) (ProxyThread.sharedInstance().circuitId >> 8);
    message[1] = (byte) ProxyThread.sharedInstance().circuitId;
    message[2] = 3; // relay
    message[3] = (byte) (streamId >> 8);
    message[4] = (byte) streamId;
    message[5] = 0;
    message[6] = 0;
    message[11] = (byte) ((512 - 14) >> 8);
    message[12] = (byte) (512 - 14);
    message[13] = 2; // data

    BufferedStreamReader reader = new BufferedStreamReader(readSocket.getInputStream());
    String buf = "";
    String line;
    while ((line = reader.readLine()) != null) {
      line = modifyHttpHeaderLine(line);
      buf += line;
      if (buf.length() >= (512 - 14)) {
        System.arraycopy(buf, 0, message, 14, 512 - 14);
        writeSocket.getOutputStream().write(message);
        buf = buf.substring(512 - 14);
      }
      if (line.equals("\n") || line.equals("\r\n")) {
        while (buf.length() != 0) {
          // TODO
          message[11] = (byte) (Math.min(buf.length(), 512 - 14) >> 8);
          message[12] = (byte) Math.min(buf.length(), 512 - 14);
          System.arraycopy(buf, 0, message, 14, Math.min(buf.length(), 512 - 14));
          writeSocket.getOutputStream().write(message);
          buf = buf.substring(Math.min(buf.length(), 512 - 14));
        }
        break;
      }
    }

    (new RawDataRelayThread(readSocket, writeSocket, streamId)).run();
  }

  private void handleHttpResponse(Socket writeSocket) {
    StringBuilder line = new StringBuilder();
    while (true) {
      try {
        byte[] curr = buf.poll(25000, TimeUnit.MILLISECONDS);
        if (curr[2] == 3 && curr[13] == 2) { // relay data
          int length = ((curr[11] & 0xFF) << 8 | (curr[12] & 0xFF));
          for (int i = 14; i < 14 + length; i++) {
            line.append((char) curr[i]);
            if (curr[i] == (int) '\n') {
              clientSocket.getOutputStream().write(line.toString().getBytes());
              if (line.equals("\n") || line.equals("\r\n")) {
                (new TorBufferRelayThread(clientSocket, buf, streamId)).run();
                break;
              }
              line = new StringBuilder();
            }
            writeSocket.getOutputStream().write(curr[i]);
          }
        } else {
          // most likely relay end, but we're done regardless
          break;
        }
      } catch (IOException e) {
          break;
      } catch (InterruptedException e) {
          break;
      }
    }
  }

  /** Parses an HTTP header line to downgrade to HTTP/1.0 and Connection: close.
      @param line The line to modify.
      @return The modified line. */
  private String modifyHttpHeaderLine(String line) {
    line = line.replace("HTTP/1.1", "HTTP/1.0");
    if (line.trim().equalsIgnoreCase("Connection: keep-alive")) {
      line = "Connection: close\r\n";
    } else if (line.trim().equalsIgnoreCase("Proxy-connection: keep-alive")) {
      line = "Proxy-connection: close\r\n";
    }
    return line;
  }

  /** Helper that returns a Socket for communication with a server specified by the host line or
      the first line in bufferedLines. Adds header lines it reads while looking for the host line
      to bufferedLines. Returns null if there is an error or the server is invalid. */
  private boolean beginRelayWithHttpHeader(BufferedStreamReader reader, List<String> bufferedLines) {
    String line;
    while ((line = reader.readLine()) != null) {
      bufferedLines.add(line);

      if (line.equals("\n") || line.equals("\r\n")) {
        line = null;
        break;
      }

      line = line.trim();
      if (line.toLowerCase().startsWith("host")) {
        break;
      }
    }

    if (bufferedLines.isEmpty()) {
      return false;
    }

    String[] alternateComponents = bufferedLines.get(0).toLowerCase().split(" ")[1].split(":");
    String[] ipComponents;
    if (line == null) {
      ipComponents = alternateComponents;
    } else {
      ipComponents = line.substring(6).trim().split(":");
    }

    String ip = ipComponents[0];
    int iport = -1;
    if (ipComponents.length == 2) {
      iport = Integer.parseInt(ipComponents[1]);
    } else if (alternateComponents.length > 1) {
      try {
        iport = Integer.parseInt(alternateComponents[alternateComponents.length - 1]);
      } catch (NumberFormatException e) {
        // no op
      }
    }

    if (iport == -1) {
      // Use default ports.
      if (alternateComponents[0].startsWith("https")) {
        iport = 443;
      } else {
        iport = 80;
      }
    }

    final byte[] body = (ip + ":" + iport + "\0").getBytes();
    if (14 + body.length > 512) {
      // error
    }
    byte[] message = new byte[512];
    message[0] = (byte) (ProxyThread.sharedInstance().circuitId >> 8);
    message[1] = (byte) ProxyThread.sharedInstance().circuitId;
    message[2] = 3; // relay
    message[3] = (byte) (streamId >> 8);
    message[4] = (byte) streamId;
    message[5] = 0;
    message[6] = 0;
    message[11] = (byte) (body.length >> 8);
    message[12] = (byte) body.length;
    message[13] = 1; // begin
    System.arraycopy(body, 0, message, 14, body.length);
    try {
      ProxyThread.sharedInstance().getGatewaySocket().getOutputStream().write(message);
      byte[] result = buf.poll(SO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      // TODO: handle timeout, stricter data integrity in check
      return result[13] == 4; // connected
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      return false;
    }
  }
}
