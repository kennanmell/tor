package src;

import java.net.InetAddress;

public class Service {
  public final InetAddress ip;
  public final int iport;
  public final int data;
  public final String name;
  private int lifetime;
  private long lastRegistrationTimeMs;

  public Service(InetAddress ip, int iport, int data, String name) {
    this.ip = ip;
    this.iport = iport;
    this.data = data;
    this.name = name;
  }

  public void setLifetime(int lifetime) {
    this.lifetime = lifetime;
  }

  public int getLifetime() {
    return this.lifetime;
  }

  public void setLastRegistrationTimeMs(long ms) {
    this.lastRegistrationTimeMs = ms;
  }

  public long getLastRegistrationTimeMs() {
    return this.lastRegistrationTimeMs;
  }
}
