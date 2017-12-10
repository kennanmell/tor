package regagent;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.List;

/** Runs a command-line interface that allows the user to act as the client of an
    agent via a simple command line interface. The agent communicates with
    a server that provides and registers information about internet services. */
public class RegAgentThread extends Thread {
  /// A 2-byte authentication token represented as an int.
  public static final int MAGIC_ID = 0xC461;
  /// The maximum number of ms to wait before timing out when waiting for a server response.
  public static final int REQUEST_TIMEOUT_MS = 5000;
  /// The maximum number of times to send a request to the server before giving up.
  public static final int MAX_REQUEST_TRIES = 3;
  /// The number of links in a circuit.
  public static final int CIRCUIT_LENGTH = 3;
  /// The DatagramSocket used to read probe requests from the server.
  private DatagramSocket readSocket = null;
  /// The DatagramSocket used to send requests to the server.
  private DatagramSocket writeSocket = null;
  /// The RequestHandler used to make and send requests, except registration renewal requests.
  private RequestHandler requestHandler;
  /// The thread that handles automatic service registration renewal.
  private RegistrationRenewalThread registrationRenewer;
  /// The thread that handles probes from the server.
  private ProbeHandlerThread probeHandler;
  /// The group number in the name of the service to keep registered.
  private int groupNo;
  /// The instance number in the name of the servie to keep registered.
  private int instanceNo;
  /// The id for the Service to register, sent as the data field during registration.
  private int agentId;
  /// The port of the service to register.
  private int iport;
  /// The Service to keep registered (represents the Tor node).
  private Service service;

  public RegAgentThread(int groupNo, int instanceNo, int agentId, int iport) {
    this.groupNo = groupNo;
    this.instanceNo = instanceNo;
    this.agentId = agentId;
    this.iport = iport;

    InetAddress serverAddress;
    try {
      serverAddress = InetAddress.getByName("cse461.cs.washington.edu");
    } catch (UnknownHostException e) {
      return;
      // TODO
    }
    final int serverPort = 46101;

    // Bind to adjacent ports.
    int startPort = 1500;
    while (writeSocket == null || readSocket == null) {
      try {
        writeSocket = new DatagramSocket(startPort);
        startPort += 1;
        readSocket = new DatagramSocket(startPort);
        writeSocket.setSoTimeout(REQUEST_TIMEOUT_MS);
      } catch (SocketException e) {
        startPort++;
        if (writeSocket != null) {
          writeSocket.close();
        }
        if (readSocket != null) {
          readSocket.close();
        }
        writeSocket = null;
        readSocket = null;
        continue;
      }
    }

    // Prepare request handler.
    RequestHandler.setServer(serverAddress, serverPort);
    requestHandler = new RequestHandler(MAGIC_ID, writeSocket, MAX_REQUEST_TRIES, null);
  }

  @Override
  public void run() {
    // Handle probes from server.
    System.out.println("Reg Agent: running agent");
    probeHandler = new ProbeHandlerThread(readSocket, MAGIC_ID, null);
    probeHandler.start();

    // Handle automatic registration renewal.
    registrationRenewer = new RegistrationRenewalThread(requestHandler, null);
    registrationRenewer.start();

    // Register the service.
    InetAddress localhostIp = null;
    try {
      localhostIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      // TODO
    }
    final String serviceName = "Tor61Router-" + String.format("%04d", groupNo) + "-" +
        String.format("%04d", instanceNo);
    service = new Service(localhostIp, iport, agentId, serviceName);
    if (requestHandler.registerService(service)) {
      registrationRenewer.addService(service);
    } else {
      // TODO
    }
  }

  // Unregisters the service associated with the tor server.
  public void unregisterService() {
    requestHandler.unregisterService(service);
    registrationRenewer.removeService(service);
    System.out.println("unregistered service");
  }

  // Return all reported services available
  public List<Service> getAllServices() {
    List<Service> candidates = new ArrayList<>(Arrays.asList(requestHandler.fetchServicesBeginningWith("Tor61Router-4391-")));
    // TODO: take all
    candidates = new ArrayList<>(Arrays.asList(requestHandler.fetchServicesBeginningWith("Tor61Router-4589-")));
    // Register the service.
    InetAddress localhostIp = null;
    try {
      localhostIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      // TODO
    }
    if (service == null) {
      service = new Service(localhostIp, iport, agentId, "Tor61Router-" + String.format("%04d", groupNo) + "-" +
              String.format("%04d", instanceNo));
    }
    candidates.add(service);
    return candidates;
  }
}
