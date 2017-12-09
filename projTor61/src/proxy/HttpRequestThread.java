package proxy;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.nio.ByteBuffer;

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
  /// The socket for communication with the server.
  private Socket serverSocket;
  /// An HttpRequestListener that is called when events occur in this thread.
  private HttpRequestListener listener;
  private BlockingQueue<byte[]> responseBuf;
  private int streamId;
  private int circuitId = 1;

  /** Sole constructor.
      @param clientSocket The socket for communication with the browser (must not be null).
      @param listener The HttpRequestListener to call when events occur in this thread. */
  public HttpRequestThread(Socket clientSocket, HttpRequestListener listener, Socket torSocket) {
    try {
      clientSocket.setSoTimeout(SO_TIMEOUT_MS);
    } catch (IOException e) {
      // no op
    }
    this.clientSocket = clientSocket;
    this.serverSocket = torSocket;
    this.listener = listener;
    this.responseBuf = new LinkedBlockingQueue<>();
    this.streamId = StreamIdGenerator.next();
  }

  @Override
  public void run() {
    System.out.println("HTTPR: in run");
    try {
      // read from browser socket
      BufferedStreamReader reader = new BufferedStreamReader(clientSocket.getInputStream());
      String line = reader.readLine();
      if (line == null) {
        return;
      }

      if (listener != null) {
        listener.onRequestReceived(modifyHttpHeaderLine(line));
      }

      List<String> bufferedLines = new ArrayList<>();
      bufferedLines.add(line);
      boolean opened = openTorConnection(reader, bufferedLines);

      if (line.trim().toLowerCase().startsWith("connect")) {
        System.out.println("CONNECT received from tor gateway");
        if (!opened) {
          clientSocket.getOutputStream().write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
          System.out.println("BAD GATEWAY WRITING 502");
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
        System.out.println("SENT OK (1)");
        clientSocket.setSoTimeout(0);
        serverSocket.setSoTimeout(0);
        System.out.println("SENT OK (2)");
        (new RawDataRelayThread(clientSocket, serverSocket, streamId, circuitId)).start();
        (new RawDataRelayThread(serverSocket, clientSocket, streamId, circuitId)).run();
      } else {
        if (serverSocket == null) {
          System.out.println("browser socket was null");
          return;
        }

        while (!bufferedLines.isEmpty()) {
          // Send the lines of the header that have been read.
          writeTorData(serverSocket, modifyHttpHeaderLine(bufferedLines.remove(0)).getBytes());
        }

        System.out.println("sent buffered header lines");

        handleHttpMessage(clientSocket, serverSocket);
        handleHttpResponse(clientSocket);
      }
    } catch (IOException e) {
      try {
        clientSocket.close();
        if (serverSocket != null) {
          serverSocket.close();
        }
      } catch (IOException e2) {
        // no op
      }
      return;
    }
  }

  private byte[] dataCell = new byte[512];
  private int dataCellOffset = 14;
  private void writeTorData(Socket writeSocket, byte[] data) throws IOException {
    System.out.println("HTTPR: starting write");
    dataCell[0] = (byte) (circuitId >> 8);
    dataCell[1] = (byte) circuitId;
    dataCell[2] = 3; // relay
    dataCell[3] = (byte) (streamId >> 8);
    dataCell[4] = (byte) streamId;
    dataCell[11] = (byte) ((512 - 14) >> 8);
    dataCell[12] = (byte) (512 - 14);
    dataCell[13] = 2; // relay data

    for (int i = 0; i < data.length; i++) {
      dataCell[dataCellOffset] = data[i];
      dataCellOffset++;
      if (dataCellOffset == 512) {
        writeSocket.getOutputStream().write(dataCell);
        dataCellOffset = 14;
      }
    }

    if (dataCell[dataCellOffset - 1] == (int) '\n' && (dataCell[dataCellOffset - 2] == (int) '\n' ||
        dataCell[dataCellOffset - 3] == (int) '\n')) {
      dataCell[11] = (byte) ((dataCellOffset - 14) >> 8);
      dataCell[12] = (byte) (dataCellOffset - 14);
      writeSocket.getOutputStream().write(dataCell);
      dataCell[11] = (byte) ((512 - 14) >> 8);
      dataCell[12] = (byte) (512 - 14);
      dataCellOffset = 14;
    }

/*
    int offset = 0;

    while (offset < data.length - (512 - 14)) {
      dataCell[11] = (byte) ((512 - 14) >> 8);
      dataCell[12] = (byte) (512 - 14);
      for (int i = offset; i < offset + 512 - 14; i++) {
        dataCell[14 + i - offset] = data[i];
      }
      writeSocket.getOutputStream().write(dataCell);
      offset += 512 - 14;
    }

    if (offset != data.length) {
      dataCell[11] = (byte) ((data.length - offset) >> 8);
      dataCell[12] = (byte) (data.length - offset);
      for (int i = offset; i < data.length; i++) {
        dataCell[14 + i - offset] = data[i];
      }
      writeSocket.getOutputStream().write(dataCell);
    }
    System.out.println("HTTPR: ending write");
    */
  }

  /** Handles an HTTP header and body sent from readSocket to writeSocket. */
  private void handleHttpMessage(Socket readSocket, Socket writeSocket) throws IOException {
    System.out.println("HTTPR: starting handle message");
    BufferedStreamReader reader = new BufferedStreamReader(readSocket.getInputStream());
    String line;
    while ((line = reader.readLine()) != null) {
      writeTorData(writeSocket, modifyHttpHeaderLine(line).getBytes());
      if (line.equals("\n") || line.equals("\r\n")) {
        break;
      }
    }

    (new RawDataRelayThread(readSocket, writeSocket, streamId, circuitId)).run();
    System.out.println("HTTPR: ending handle message");
  }

  private void handleHttpResponse(Socket writeSocket) throws IOException {
    System.out.println("HTTPR: starting handle response");
    BufferedStreamReader reader = new BufferedStreamReader(responseBuf);
    String line;
    while ((line = reader.readLine()) != null) {
      writeSocket.getOutputStream().write(modifyHttpHeaderLine(line).getBytes());
      if (line.equals("\n") || line.equals("\r\n")) {
        break;
      }
    }

    (new RawDataRelayThread(writeSocket, reader, streamId, circuitId)).run();
    System.out.println("HTTPR: ending handle response");
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
  private boolean openTorConnection(BufferedStreamReader reader, List<String> bufferedLines) throws IOException {
    System.out.println("attempting to open tor connection");
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
      System.out.println("buffered lines empty");
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

    byte[] bodyData = (ip + ":" + iport + "\0").getBytes();
    byte[] beginCell = new byte[512];
    beginCell[0] = (byte) (circuitId >> 8);
    beginCell[1] = (byte) circuitId;
    beginCell[2] = 3; // relay
    beginCell[3] = (byte) (streamId >> 8);
    beginCell[4] = (byte) streamId;
    beginCell[11] = (byte) (bodyData.length >> 8);
    beginCell[12] = (byte) bodyData.length;
    beginCell[13] = 1; // begin
    for (int i = 14; i < 14 + bodyData.length; i++) {
      beginCell[i] = bodyData[i - 14];
    }

    SharedDataDistributionThread.sharedInstance().addStream(this.streamId, this.responseBuf);
    System.out.println("attempting to write begin");
    serverSocket.getOutputStream().write(beginCell);
     System.out.println("wrote begin");

    try {
      byte[] connectedCell = responseBuf.take();//poll(25000, TimeUnit.MILLISECONDS);
      if (connectedCell != null && connectedCell[2] == 3 && connectedCell[13] == 4) {
        System.out.println("Received CONNECTED relay cell");
      } else {
        System.out.println("------------------");
        System.out.println(Arrays.toString(connectedCell));
        System.out.println("-------------------");
        System.out.println("Received unkown cell instead of connected");
      }
      return connectedCell != null && connectedCell[2] == 3 && connectedCell[13] == 4;
    } catch (InterruptedException e) {
      System.out.println("INTERRUPTED");
      return false;
    }
  }
}