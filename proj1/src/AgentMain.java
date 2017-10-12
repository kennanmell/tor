package src;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Runs a command-line interface that allows the user to communicate with
    a server that provides and registers information about various ips and ports.
    The application that communicates with the server on behalf of the user
    is referred to as an agent. */
public class AgentMain {
  /// A 2-byte authentication token represented as an int.
  public static final int MAGIC_ID = 0xC461;
  /// The maximum number of ms to wait before timing out when waiting for a server response.
  public static final int REQUEST_TIMEOUT_MS = 5000;
  /// The maximum number of times to send a request to the server before giving up.
  public static final int MAX_REQUEST_TRIES = 3;

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

    final InetAddress localhostIp;
    try {
      localhostIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.out.println("fatal error: unable to get localhost ip");
      return;
    }

    // Bind to adjacent ports.
    DatagramSocket writeSocket = null;
    DatagramSocket readSocket = null;
    int startPort = 1500;
    while (writeSocket == null) {
      try {
        writeSocket = new DatagramSocket(startPort);
        writeSocket.setSoTimeout(REQUEST_TIMEOUT_MS);
        readSocket = new DatagramSocket(++startPort);
      } catch (SocketException e) {
        startPort++;
        writeSocket = null;
        readSocket = null;
        continue;
      }
    }

    // Handle probes from server.
    final ProbeHandlerThread probeHandler = new ProbeHandlerThread(readSocket, MAGIC_ID);
    probeHandler.start();

    // Prepare for user commands.
    RequestHandler.configureInstance(MAGIC_ID, writeSocket, serverAddress, serverPort);

    System.out.println("Running the tor61 registration client. Version 1.");
    System.out.println(String.format(
        "Connected to server at %s:%d.", serverAddress.toString(), serverPort));
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

        if (command[0].equals("r") && command.length == 4) {
          // Register portnum data serviceName
          // Register this ip + portnum with the specified data and service name.
          int iport;
          int data;
          String serviceName = command[3];
          try {
            iport = Integer.parseInt(command[1]);
          } catch (NumberFormatException e) {
            System.out.println("Invalid port for register command.");
            continue;
          }
          try {
            data = (int) Long.parseLong(command[2]); // parse unsigned int
          } catch (NumberFormatException e) {
            System.out.println("Invalid data for register command.");
            continue;
          }
          Service service = new Service(localhostIp, iport, data, serviceName);
          Object result = requestWithRetries(new Callable<Object>() {
            public Object call() throws ProtocolException {
              return RequestHandler.sharedInstance().registerService(service);
            }
          }, Command.REGISTER);

          if (result != null) {
            System.out.println(String.format(
                "Register %s:%d successful: lifetime = %d.",
                localhostIp.toString(), iport, service.getLifetime()));
          }
        } else if (command[0].equals("u") && command.length == 2) {
          // Unregister portnum
          // Unregister this ip + portnum.
          int iport;
          try {
            iport = Integer.parseInt(command[1]);
          } catch (NumberFormatException e) {
            System.out.println("Invalid port for unregister command.");
            continue;
          }

          // Last two arguments of the service are not used.
          Service service = new Service(localhostIp, iport, 0, "");

          Object result = requestWithRetries(new Callable<Object>() {
            public Object call() throws ProtocolException {
              return RequestHandler.sharedInstance().unregisterService(service);
            }
          }, Command.UNREGISTER);

          if (result != null) {
            System.out.println(String.format(
                "Unregister %s:%d successful.", localhostIp.toString(), iport));
          }
        } else if (command[0].equals("f") && command.length == 2) {
          // Fetch <name prefix>
          // Fetch name prefix info from registration service, print returned info.
        } else if (command[0].equals("p") && command.length == 1) {
          // Probe.
          // See if registration service is alive.
          Object result = requestWithRetries(new Callable<Object>() {
            public Object call() throws ProtocolException {
              return RequestHandler.sharedInstance().probeServer();
            }
          }, Command.PROBE);

          if (result != null) {
            System.out.println("Probe succeeded.");
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
          continue;
        }
      } catch (NoSuchElementException e) {
        System.out.println("Unexpected character in input. Terminating.");
        writeSocket.close();
        readSocket.close();
        System.exit(0);
      }
    }
  }

  /** Performs a `Callable` function associated with a `Command`. If the function
      returns `null` or `false`, it retries up to `MAX_REQUEST_TRIES` times.
      Does not retry if a `ProtocolException` is thrown. Prints a message to
      stdout whenever a failure or timeout occurs.
      @param requestHandlerFunc The function to execute up to `MAX_REQUEST_TRIES` times.
      @param type The `Command` type associated with `requestHandlerFunc`.
      @return The Object returned by the `requestHandlerFunc`, or `null` if
              the function never completed successfully.
      @throws IllegalStateException If an unexpected exception is thrown. */
  private static Object requestWithRetries(Callable<Object> requestHandlerFunc, Command type) {
    for (int i = 0; i < MAX_REQUEST_TRIES; i++) {
      try {
        Object result = requestHandlerFunc.call();
        if (result == null ||
            ((result instanceof Boolean) && ((Boolean) result == false))) {
          System.out.println(String.format(
              "Timed out waiting for reply to %s message.", type.toString()));
        } else {
          return result;
        }
      } catch (ProtocolException e) {
        System.out.println(String.format(
            "Received invalid response to %s message.", type.toString()));
        return null;
      } catch (Exception e) {
        throw new IllegalStateException();
      }
    }
    System.out.println(String.format(
        "Sent %d %s messages but got no reply.", MAX_REQUEST_TRIES, type.toString()));
    System.out.println(String.format("%s command failed.", type.toString()));
    return null;
  }
}
