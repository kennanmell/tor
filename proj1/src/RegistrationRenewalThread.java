package src;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RegistrationRenewalThread extends Thread {
  private List<Service> servicesToRegister;

  @Override
  public void run() {
    try {
      // TODO: finish implementing this. Make sure to implement a callback to AgentMain
      //       when a service is re-registered so that the main can print the registration.
      Collections.sort(servicesToRegister, new Comparator<Service>() {
        @Override
        public int compare(Service o1, Service o2) {
          long currentTime = System.currentTimeMillis();
          long o1TimeLeft = o1.getLifetime() - currentTime + o1.getLastRegistrationTimeMs();
          long o2TimeLeft = o2.getLifetime() - currentTime + o2.getLastRegistrationTimeMs();
          if (o1TimeLeft > o2TimeLeft) {
            return 1;
          } else if (o1TimeLeft == o2TimeLeft) {
            return 0;
          } else {
            // o1TimeLeft < o2TimeLeft
            return -1;
          }
        }
      });
      wait(30000);
    } catch (InterruptedException e) {
      throw new IllegalStateException();
    }
  }
}
