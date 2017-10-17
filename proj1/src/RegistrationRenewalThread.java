package src;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RegistrationRenewalThread extends Thread {
  private static final int BUFFER_TIME = 30000;
  private List<Service> servicesToRegister;
  private final RequestHandler requestHandler;
  private final Comparator<Service> comparator;

  /** Sole constructor.
      @param requestHandler The `RequestHandler` to use to send registrations to the server.
      @throws IllegalArgumentException If `requestHandler` is null. */
  public RegistrationRenewalThread(RequestHandler requestHandler) {
    if (requestHandler == null) {
      throw new IllegalArgumentException();
    }
    this.requestHandler = requestHandler;
    this.servicesToRegister = new ArrayList<>();
    this.comparator = new Comparator<Service>() {
      // Note: this comparator imposes orderings that are inconsistent with equals.
      @Override
      public int compare(Service o1, Service o2) {
        return Long.signum(o1.expirationTimeMillis - o2.expirationTimeMillis);
      }
    };
  }

  /** Adds a `Service` to the list of services this thread should automatically
      renew. The caller must ensure that the expiration for the service is correct.
      @param service The service to automatically reregister.
      @throws IllegalArgumentException If `service` is null. */
  public void addService(Service service) {
    if (service == null) {
      throw new IllegalArgumentException();
    }

    synchronized (servicesToRegister) {
      servicesToRegister.remove(service);
      servicesToRegister.add(service);
      Collections.sort(servicesToRegister, comparator);
    }

    synchronized(this) {
      notify();
    }
  }

  /** Removes a `Service` from the list of services this thread should automatically
      renew. Does nothing if the service is not being reregistered.
      @param service The service to remove. */
  public void removeService(Service service) {
    synchronized (servicesToRegister) {
      servicesToRegister.remove(service);
    }

    synchronized(this) {
      notify();
    }
  }

  @Override
  public void run() {
    try {
      // TODO: callback to AgentMain when service is re-registered so main prints pregistration
      while(true) {
        // Wait until a service is about to expire.
        synchronized (this) {
          if (servicesToRegister.isEmpty()) {
            wait();
          } else {
            wait(Math.max(1, servicesToRegister.get(0).expirationTimeMillis -
                             System.currentTimeMillis() - BUFFER_TIME));
          }
        }

        synchronized(servicesToRegister) {
          // Renew registration for all services within BUFFER_TIME of expiring.
          while (!servicesToRegister.isEmpty() && servicesToRegister.get(0).expirationTimeMillis -
                 System.currentTimeMillis() <= BUFFER_TIME) {
            final Service currentService = servicesToRegister.get(0);
            try {
              requestHandler.registerService(servicesToRegister.get(0));
              System.out.println("Automatically renewed registration for " + currentService);
            } catch (ProtocolException e) {
              System.out.println("Failed to automatically renew registration for " + currentService);
            }
            Collections.sort(servicesToRegister, comparator);
          }
        }
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException();
    }
  }
}
