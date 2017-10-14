package src;

import java.net.DatagramSocket;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class RegistrationRenewalThread extends Thread {
  private static final int BUFFER_TIME = 30000;
  private Set<Service> servicesToRegister;
  private byte[] magicId;
  private DatagramSocket socket;
  
  public RegistrationRenewalThread(DatagramSocket socket, int magicId) {
    if (socket == null) {
      throw new IllegalArgumentException();
    }
    this.servicesToRegister = new ArrayList<Service>();
    this.socket = socket;
    this.magicId = new byte[2];
    this.magicId[0] = (byte) (magicId >> 8);
    this.magicId[1] = (byte) magicId;
    // see if this is sorted by shortest time first
    this.servicesToRegister = new TreeSet<Service>(new Comparator<Service>() {
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
  }

  public void addService(Service service) {
    servicesToRegister.add(service);
  }

  @Override
  public void run() {
    try {
      // TODO: finish implementing this. Make sure to implement a callback to AgentMain
      //       when a service is re-registered so that the main can print the registration.
      // may need lock, main could add to set when set is being read or modified by this thread
      // add sleep to thread while size of set is empty
      // check case where set is emmpty, and wait times
      RequestHandler requestHandler = new RequestHandler(MAGIC_ID, socket, 3);
      int waitTime = 0;
      boolean succeeded = false;
      while(true) {
        Service priorityService = servicesToRegister.pollFirst();
        if (priorityService != null) {
          succeeded = requestHandler.registerService(priorityService);
          waitTime = Math.max(servicesToRegister.first().getLifetime() - BUFFER_TIME, 0);
        }
        // pause thread until we need to handle nextmost request or if 
        // client added another element.
        wait(waitTime);
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException();
    }
  }
}
