package src;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class EofListenerThread extends Thread {
  @Override
  public void run() {
    Scanner console = new Scanner(System.in);
    try {
      while (true) {
        console.nextLine();
      }
    } catch (NoSuchElementException e) {
        System.exit(0);
    }
  }
}
