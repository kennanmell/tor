package regagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Thread that renews registrations before they expire.
public class RegistrationRenewalThread extends Thread {
  /// Callbacks for events in a registration renewal thread.
  public interface RegistrationEventListener {
    /** Called by a RegistrationRenewalThread when it renews the registration for a Service.
        @param service The Service whose registration was renewed. */
    void onServiceRegistrationRenewed(Service service);
    /** Called by a RegistrationRenewalThread when it fails to renew the registration for a Service.
        @param service The Service whose registration expired because it couldn't be renewed. */
    void onServiceRegistrationExpired(Service service);
    /// Called when the RegistrationRenewalThread encounters a fatal error and closes.
    void onFatalError();
  }

  /// The number of ms before a registration will expire to renew it.
  private static final int BUFFER_TIME = 30000;
  /// The Services to keep registered.
  private List<Service> servicesToRegister;
  /// The RequestHandler used to re-register Services.
  private final RequestHandler requestHandler;
  /// Used to sort Services by expiration time.
  private final Comparator<Service> comparator;
  /// Used to notify a listener of events on this thread.
  private RegistrationEventListener listener;

  /** Creates a new registration renewal thread.
      @param requestHandler The `RequestHandler` to use to send registrations to the server.
      @param taskListener A TaskListener called when this thread successfully renews
                          registration for a Service or fails to do so.
      @throws IllegalArgumentException If `requestHandler` is null. */
  public RegistrationRenewalThread(RequestHandler requestHandler,
                                   RegistrationEventListener listener) {
    if (requestHandler == null) {
      throw new IllegalArgumentException();
    }
    this.listener = listener;
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

    synchronized(this) {
      servicesToRegister.remove(service);
      servicesToRegister.add(service);
      Collections.sort(servicesToRegister, comparator);
      notify();
    }
  }

  /** Removes a `Service` from the list of services this thread should automatically
      renew. Does nothing if the service is not being reregistered.
      @param service The service to remove. */
  public void removeService(Service service) {
    synchronized(this) {
      servicesToRegister.remove(service);
      notify();
    }
  }

  @Override
  public void run() {
    try {
      while(true) {
        // Wait until a service is about to expire.
        synchronized (this) {
          if (servicesToRegister.isEmpty()) {
            wait();
          } else {
            wait(Math.max(1, servicesToRegister.get(0).expirationTimeMillis -
                             System.currentTimeMillis() - BUFFER_TIME));
          }

          // Renew registration for all services within BUFFER_TIME of expiring.
          while (!servicesToRegister.isEmpty() && servicesToRegister.get(0).expirationTimeMillis -
                 System.currentTimeMillis() <= BUFFER_TIME) {
            final Service currentService = servicesToRegister.get(0);
            if (requestHandler.registerService(servicesToRegister.get(0))) {
              if (listener != null) {
                listener.onServiceRegistrationRenewed(currentService);
              }
            } else {
              if (listener != null) {
                listener.onServiceRegistrationExpired(currentService);
              }
            }
            Collections.sort(servicesToRegister, comparator);
          }
        }
      }
    } catch (InterruptedException e) {
      if (listener != null) {
        listener.onFatalError();
      }
      return;
    }
  }
}
