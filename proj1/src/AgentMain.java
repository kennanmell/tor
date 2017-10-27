package src;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Runs a command-line interface that allows the user to act as the client of an
    agent via a simple command line interface. The agent communicates with
    a server that provides and registers information about internet services. */
public class AgentMain {
  /// A 2-byte authentication token represented as an int.
  public static final int MAGIC_ID = 0xC461;
  /// The maximum number of ms to wait before timing out when waiting for a server response.
  public static final int REQUEST_TIMEOUT_MS = 5000;
  /// The maximum number of times to send a request to the server before giving up.
  public static final int MAX_REQUEST_TRIES = 3;
  /// The DatagramSocket used to read probe requests from the server.
  private static DatagramSocket readSocket = null;
  /// The DatagramSocket used to send requests to the server.
  private static DatagramSocket writeSocket = null;
  /// The RequestHandler used to make and send requests, except registration renewal requests.
  private static RequestHandler requestHandler;
  /// The thread that handles automatic service registration renewal.
  private static RegistrationRenewalThread registrationRenewer;
  /// The thread that handles probes from the server.
  private static ProbeHandlerThread probeHandler;

  public static void main(String[] args) {
    // Process command line args.
    if (args.length != 2) {
      System.out.println("usage: ./run <registration service host name> <service port>");
      return;
    }

    InetAddress serverAddress;
    try {
      serverAddress = InetAddress.getByName(args[0]);
    } catch (UnknownHostException e) {
      System.out.println("invocation error: invalid address");
      return;
    }

    int serverPort;
    try {
      serverPort = Integer.parseInt(args[1]);
    } catch (NumberFormatException e) {
      System.out.println("invocation error: invalid port");
      return;
    }

    // Bind to adjacent ports.
    int startPort = 1500;
    while (writeSocket == null || readSocket == null) {
      try {
        writeSocket = new DatagramSocket(1500);
        startPort++;
        readSocket = new DatagramSocket(1501);
        writeSocket.setSoTimeout(REQUEST_TIMEOUT_MS);
      } catch (SocketException e) {
        startPort++;
        writeSocket = null;
        readSocket = null;
        continue;
      }
    }

    // Prepare for user commands.
    RequestHandler.setServer(serverAddress, serverPort);

    requestHandler = new RequestHandler(MAGIC_ID, writeSocket, MAX_REQUEST_TRIES,
        new RequestHandler.RequestEventListener() {
      @Override
      public void onRequestTimedOut(Command command, boolean willRetry) {
        if (willRetry) {
          System.out.println(command + " command timed out. Retrying.");
        } else {
          System.out.println(command + " command timed out.");
          System.out.println(command + " command failed.");
        }
      }

      @Override
      public void onRequestError(Command command) {
        System.out.println(command + " command failed.");
      }
    });

    // Handle probes from server.
    probeHandler = new ProbeHandlerThread(readSocket, MAGIC_ID,
        new ProbeHandlerThread.ProbeEventListener() {
      @Override
      public void onProbe() {
        System.out.print("Probed by server.\n> ");
      }

      @Override
      public void onFatalError() {
        System.out.println("fatal error in server probe listener");
        System.exit(0);
      }
    });
    probeHandler.start();

    // Handle automatic registration renewal.
    registrationRenewer = new RegistrationRenewalThread(
        new RequestHandler(MAGIC_ID, writeSocket, MAX_REQUEST_TRIES, null),
        new RegistrationRenewalThread.RegistrationEventListener() {
      @Override
      public void onServiceRegistrationRenewed(Service service) {
        System.out.print("Automatically renewed registration for " + service + "\n> ");
      }

      @Override
      public void onServiceRegistrationExpired(Service service) {
        System.out.print("Failed to automatically renew registration for " + service + "\n> ");
        registrationRenewer.removeService(service);
      }

      @Override
      public void onFatalError() {
        System.out.println("fatal error in automatic registration renewal");
        System.exit(0);
      }
    });

    registrationRenewer.start();

    System.out.println("Running the tor61 registration client. Version 1.");
    System.out.println("Using server at " + serverAddress.getHostAddress() + ":" + serverPort);
    System.out.println("Ready. Type \"h\" for command list.");

    // Run input loop until user terminates.
    final Scanner scanner = new Scanner(System.in);
    while (true) {
      System.out.print("> ");
      try {
        String[] command = scanner.nextLine().split("\\s+");
        if (command.length == 0) {
          // Invalid command.
          System.out.println("Invalid command. Type \"h\" for command list.");
          continue;
        }

        processStringCommand(command);
      } catch (NoSuchElementException e) {
        writeSocket.close();
        readSocket.close();
        System.exit(0);
      }
    }
  }

  /** Attempts to process a user input as a command and execute that command.
      Lets the user know via printing if the command is invalid or if it succeeds. */
  private static void processStringCommand(String[] command) {
    InetAddress localhostIp = null;
    try {
      localhostIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.out.println("fatal error finding ip");
      System.exit(0);
    }

    if (command[0].equals("r") && command.length == 4) {
      // Register portnum data serviceName
      // Register this ip + portnum with the specified data and service name.
      int iport;
      int data;
      String serviceName = command[3];
      try {
        iport = Integer.parseInt(command[1]);
      } catch (NumberFormatException e) {
        System.out.println("Invalid port for REGISTER command.");
        return;
      }
      try {
        data = (int) Long.parseLong(command[2]); // parse unsigned int
      } catch (NumberFormatException e) {
        System.out.println("Invalid data for REGISTER command.");
        return;
      }

      Service service = new Service(localhostIp, iport, data, serviceName);

      if (requestHandler.registerService(service)) {
        System.out.println("Registered " + service + ".");
        registrationRenewer.addService(service);
      }
    } else if (command[0].equals("u") && command.length == 2) {
      // Unregister portnum
      // Unregister this ip + portnum.
      int iport;
      try {
        iport = Integer.parseInt(command[1]);
      } catch (NumberFormatException e) {
        System.out.println("Invalid port for UNREGISTER command.");
        return;
      }

      // Last two arguments of the service are not used.
      Service service = new Service(localhostIp, iport, 0, "");

      if (requestHandler.unregisterService(service)) {
        System.out.println("Unregisted service on port " + service.iport + ".");
        registrationRenewer.removeService(service);
      }
    } else if (command[0].equals("f") && (command.length == 1 || command.length == 2)) {
      // Fetch <name prefix>
      // Fetch name prefix info from registration service, print returned info.
      final String prefix = command.length == 1 ? "" : command[1];
      final Service[] services = requestHandler.fetchServicesBeginningWith(prefix);
      if (services != null) {
        if (services.length == 0) {
          if (prefix.length() == 0) {
            System.out.println("Found no services.");
          } else {
            System.out.println("Found no services starting with " + prefix + ".");
          }
        } else if (services.length == 1) {
          if (prefix.length() == 0) {
            System.out.println("Found 1 service:");
          } else {
            System.out.println("Found 1 service starting with " + prefix + ":");
          }
        } else {
          if (prefix.length() == 0) {
            System.out.println("Found " + services.length + " services:");
          } else {
            System.out.println(
                "Found " + services.length + " services starting with " + prefix + ":");
          }
        }
        for (int i = 0; i < services.length; i++) {
          System.out.println("    " + services[i]);
        }
      }
    } else if (command[0].equals("p") && command.length == 1) {
      // Probe.
      // See if registration service is alive.
      if (requestHandler.probeServer()) {
        System.out.println("Probed server.");
      }
    } else if (command[0].equals("h") && command.length == 1) {
      System.out.println("Commands:");
      System.out.println("REGISTER: r <portnum> <data> <serviceName>");
      System.out.println("UNREGISTER: u <portnum>");
      System.out.println("FETCH: f <name prefix>");
      System.out.println("PROBE: p");
      System.out.println("HELP: h");
      System.out.println("QUIT: q");
    } else if (command[0].equals("q") && command.length == 1) {
      // Quit.
      writeSocket.close();
      readSocket.close();
      System.exit(0);
    } else {
      // Invalid.
      System.out.println("Invalid command. Type \"h\" for command list.");
    }
  }
}
