package src;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RegistrationRenewalThread extends Thread {
  private static final int BUFFER_TIME = 30000;
  private List<Service> servicesToRegister;
  private RequestHandler requestHandler;
  private Comparator<Service> comparator;

  public RegistrationRenewalThread(RequestHandler requestHandler) {
    if (requestHandler == null) {
      throw new IllegalArgumentException();
    }
    this.requestHandler = requestHandler;
    this.servicesToRegister = new ArrayList<>();
    this.comparator = new Comparator<Service>() {
      // Note: this comparator imposes orderings that are inconsistent with equals
      @Override
      public int compare(Service o1, Service o2) {
        return Long.signum(o1.expirationTimeMillis - o2.expirationTimeMillis);
      }
    };
  }

  public void addService(Service service) {
    synchronized (servicesToRegister) {
      servicesToRegister.remove(service);
      servicesToRegister.add(service);
      Collections.sort(servicesToRegister, comparator);
    }
  }

  public void removeService(Service service) {
    synchronized (servicesToRegister) {
      servicesToRegister.remove(service);
    }
  }

  @Override
  public void run() {
    try {
      // TODO: callback to AgentMain when service is re-registered so main prints pregistration
      while(true) {
        // pause thread until we need to handle nextmost request or if
        // client added another element.
        synchronized (this) {
          if (servicesToRegister.isEmpty()) {
            this.wait();
          } else {
            this.wait(servicesToRegister.get(0).expirationTimeMillis -
                      System.currentTimeMillis() - BUFFER_TIME);
          }
        }

        synchronized(servicesToRegister) {
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
