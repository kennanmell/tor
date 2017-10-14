package src;

import java.net.InetAddress;

/** Represents a Service that can be registered with the server using the
    agent application. */
public class Service {
  /// The IP of the service.
  public final InetAddress ip;
  /// The port number of the service.
  public final int iport;
  /// Arbitrary data associated with the service, represented as an int.
  public final int data;
  /// The name of the service.
  public final String name;
  /// The lifetime of the service. It will be automatically unregistered when
  /// lastRegistrationTimeMs + lifetime == currentTime.
  private int lifetime;
  /// The last time this Service was registered with the server.
  private long lastRegistrationTimeMs;

  public Service(InetAddress ip, int iport, int data) {
    this(ip, iport, data, null);
  }

  /** Create a Service with a specified IP, port, data, and name. */
  public Service(InetAddress ip, int iport, int data, String name) {
    this.ip = ip;
    this.iport = iport;
    this.data = data;
    this.name = name;
  }

  /** Sets the lifetime of this Service, i.e. how many ms it will be stored
      on the server before being unregistered. */
  public void setLifetime(int lifetime) {
    this.lifetime = lifetime;
  }

  /** Gets the lifetime of this Service, i.e. how many ms it will be stored
      on the server before being unregistered.
      @return The lifetime. */
  public int getLifetime() {
    return this.lifetime;
  }

  /** Sets the last time the Service was registered with the server in ms. */
  public void setLastRegistrationTimeMs(long ms) {
    this.lastRegistrationTimeMs = ms;
  }

  /** Gets the last time the Service was registered with the server in ms.
      @return The registration time. */
  public long getLastRegistrationTimeMs() {
    return this.lastRegistrationTimeMs;
  }

  @Override
  public String toString() {
    if (name == null) {
      return ip.getHostAddress() + ":" + iport + " (data: " + data + ")";
    } else {
      return name + " at " + ip.getHostAddress() + ":" + iport;
    }
  }
}
