import java.net.InetAddress;

public class Service {
  public final InetAddress ip;
  public final int iport;
  public final int data;
  public final String name;

  public Service(InetAddress ip, int iport, int data, String name) {
    this.ip = ip;
    this.iport = iport;
    this.data = data;
    this.name = name;
  }
}
