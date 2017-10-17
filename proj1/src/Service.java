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
  /// The approximate time in ms that this service's registration on the server will expire.
  public long expirationTimeMillis;

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

  @Override
  public String toString() {
    if (name == null) {
      return ip.getHostAddress() + ":" + iport + " (data: " + data + ")";
    } else {
      return name + " at " + ip.getHostAddress() + ":" + iport;
    }
  }

  @Override
  public boolean equals(Object other) {
    // The abstract value of a Service is determined solely by its ip and port
    // but we assume that all Services registered by an agent have the same ip.
    return (other instanceof Service) ? ((Service) other).iport == this.iport : false;
  }

  @Override
  public int hashCode() {
    // The abstract value of a Service is determined solely by its ip and port
    // but we assume that all Services registered by an agent have the same ip.
    return this.iport;
  }
}
