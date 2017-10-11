package src;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class AgentMain {
  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println("Usage: ./run <registration service host name> <service port>");
      return;
    }

    Scanner scanner = new Scanner(System.in);
    while (true) {
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
        } else if (command[0].equals("u") && command.length == 2) {
          // Unregister portnum
          // Unregister this ip + portnum.
        } else if (command[0].equals("f") && command.length == 2) {
          // Fetch <name prefix>
          // Fetch name prefix info from registration service, print returned info.
        } else if (command[0].equals("p") && command.length == 1) {
          // Probe.
          // See if registration service is alive.
        } else if (command[0].equals("q") && command.length == 1) {
          // Quit.
          return;
        } else {
          // Invalid.
          System.out.println("Invalid command. Type \"h\" for command list.");
          continue;
        }
      } catch (NoSuchElementException e) {
        System.out.println("Unexpected character in input. Terminating.");
        scanner.close();
        return;
      }
    }
  }
}
