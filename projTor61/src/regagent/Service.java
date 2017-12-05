package regagent;

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

  public int getAgentID() {
    String[] nameChunks = name.split("-");
    String groupNum = nameChunks[nameChunks.length - 2];
    String InstanceNum = nameChunks[nameChunks.length - 1];
    return ((Integer.parseInt(groupNum) << 16) | Integer.parseInt(InstanceNum));
  }

  @Override
  public String toString() {
    if (name == null) {
      return ip.getHostAddress() + ":" + iport + " (data: " + data + ")";
    } else {
      return name + " at " + ip.getHostAddress() + ":" + iport;
    }
  }

  // Two Services are equal if their inetaddress and iport are equal.
  @Override
  public boolean equals(Object obj) {
      if (obj == null || !Service.class.isAssignableFrom(obj.getClass())) {
          return false;
      }
      final Service other = (Service) obj;
      if ((this.ip.equals(other.ip)) && (this.iport == other.iport)) {
        return true;
      }
      return false;
  }

     // The abstract value of a Service is determined solely by its ip and port.
  @Override
  public int hashCode() {
    int hash = 1610612741;
    hash = (37 * hash) + this.ip.hashCode();
    hash = (37 * hash) + this.iport;
    return hash;
  }
}
