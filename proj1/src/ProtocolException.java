package src;

/** An exception thrown when a server responds with an unrecognized packet header,
    or when a request fails for an unknown reason. */
public class ProtocolException extends Exception {
  public ProtocolException() {
    super();
  }

  public ProtocolException(String message) {
    super(message);
  }
}
