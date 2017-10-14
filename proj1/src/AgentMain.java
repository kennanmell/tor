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

  private static DatagramSocket readSocket = null;
  private static DatagramSocket writeSocket = null;
  private static RequestHandler requestHandler;
  private static InetAddress localhostIp;
  private static RegistrationRenewalThread registrationRenewer;
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

    try {
      localhostIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.out.println("fatal error");
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
        System.out.println(readSocket.getPort());
        System.out.println(readSocket.getInetAddress());
      } catch (SocketException e) {
        System.out.println("exception");
        startPort++;
        writeSocket = null;
        readSocket = null;
        continue;
      }
    }

    System.out.println(readSocket);
    System.out.println(readSocket.getPort());

    // Handle probes from server.
    probeHandler = new ProbeHandlerThread(readSocket, MAGIC_ID);
    probeHandler.start();

    // set up automatic service registration renewal thread.
    registrationRenewer = new RegistrationRenewalThread(writeSocket, MAGIC_ID);
    registrationRenewalThread.start();

    // Prepare for user commands.
    RequestHandler.setServer(serverAddress, serverPort);
    // Use only one attempt for each request because we print an error message
    // each time a request fails. A custom retry mechanism is implemented by
    // AgentMain.requestWithRetries.
    requestHandler = new RequestHandler(MAGIC_ID, writeSocket, 1);

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

        // Custom retry mechanism with error messages to stdout.
        Command type = null;
        for (int i = 0; i < MAX_REQUEST_TRIES; i++) {
          try {
            type = processStringCommand(command);
            if (type == null) {
              break;
            } else {
              System.out.println(type + " command timed out. Retrying.");
            }
          } catch (ProtocolException e) {
            System.out.println("command failed.");
            break;
          }
        }

        if (type != null) {
          System.out.println(type + " command failed.");
        }
      } catch (NoSuchElementException e) {
        System.out.println("Unexpected character in input. Terminating.");
        writeSocket.close();
        readSocket.close();
        System.exit(0);
      }
    }
  }

  /** Attempts to process user input as a command. Returns null if the command
      succeeds, but returns the type of the command if it times out (to make error
      messages easier to print). Throws a ProtocolException if the command fails
      for a reason besides a timeout. */
  private static Command processStringCommand(String[] command) throws ProtocolException {
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
        return Command.REGISTER;
      }
      try {
        data = (int) Long.parseLong(command[2]); // parse unsigned int
      } catch (NumberFormatException e) {
        System.out.println("Invalid data for REGISTER command.");
        return Command.REGISTER;
      }

      Service service = new Service(localhostIp, iport, data, serviceName);

      if (requestHandler.registerService(service)) {
        System.out.println("Registered " + service + ".");
        // TODO: request that the service be automatically re-registered.
        registrationRenewer.addService(service);
        registrationRenewer.notify();
        return null;
      } else {
        return Command.REGISTER;
      }
    } else if (command[0].equals("u") && command.length == 2) {
      // Unregister portnum
      // Unregister this ip + portnum.
      int iport;
      try {
        iport = Integer.parseInt(command[1]);
      } catch (NumberFormatException e) {
        System.out.println("Invalid port for UNREGISTER command.");
        return Command.UNREGISTER;
      }

      // Last two arguments of the service are not used.
      Service service = new Service(localhostIp, iport, 0, "");

      if (requestHandler.unregisterService(service)) {
        System.out.println("Unregisted service on port " + service.iport + ".");
        return null;
      } else {
        return Command.UNREGISTER;
      }
    } else if (command[0].equals("f") && (command.length == 1 || command.length == 2)) {
      // Fetch <name prefix>
      // Fetch name prefix info from registration service, print returned info.
      final String prefix = command.length == 1 ? "" : command[1];
      Service[] services = requestHandler.fetchServicesBeginningWith(prefix);
      if (services != null) {
        if (services.length == 0) {
          if (prefix.length() == 0) {
            System.out.println("Found no services.");
          } else {
            System.out.println("Found no services starting with " + prefix + ".");
          }
          return null;
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
            System.out.println("Found " + services.length + " services starting with " + prefix + ":");
          }
        }
        for (int i = 0; i < services.length; i++) {
          System.out.println("    " + services[i]);
        }
        return null;
      } else {
        return Command.FETCH;
      }
    } else if (command[0].equals("p") && command.length == 1) {
      // Probe.
      // See if registration service is alive.
      if (requestHandler.probeServer()) {
        System.out.println("Probed server.");
        return null;
      }
      return Command.PROBE;
    } else if (command[0].equals("h") && command.length == 1) {
      System.out.println("Commands:");
      System.out.println("REGISTER: r <portnum> <data> <serviceName>");
      System.out.println("UNREGISTER: u <portnum>");
      System.out.println("FETCH: f <name prefix>");
      System.out.println("PROBE: p");
      System.out.println("HELP: h");
      System.out.println("QUIT: q");
      return null;
    } else if (command[0].equals("q") && command.length == 1) {
      // Quit.
      writeSocket.close();
      readSocket.close();
      System.exit(0);
      return null;
    } else {
      // Invalid.
      System.out.println("Invalid command. Type \"h\" for command list.");
      return null;
    }
  }
}
