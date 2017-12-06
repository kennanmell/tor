package src;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Hop {
  public final Socket s;
  public final int circuitId;

  public Hop(Socket s, int circuitId) {
    this.s = s;
    this.circuitId = circuitId;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Hop)) {
      return false;
    }

    Hop otherHop = (Hop) other;

    // Only one socket per ip:port, so use strict == comparison.
    return this.s == otherHop.s && this.circuitId == otherHop.circuitId;
  }

  @Override
  public int hashCode() {
    return s.hashCode() * 5 + circuitId * 13;
  }
}
