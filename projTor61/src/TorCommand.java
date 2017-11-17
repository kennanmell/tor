package src;

/** Represents the 8 types of commands used by the agent and server to communicate. */
public enum TorCommand {
  OPEN, OPENED, OPEN_FAILED, CREATE, CREATED, CREATE_FAILED, DESTROY, RELAY;

  /** Converts this `TorCommand` to a byte.
      @return A `byte` corresponding to the `TorCommand`.
      @throws IllegalArgumentException if the `TorCommand` is not recognized. */
  public byte toByte() {
    switch (this) {
    case CREATE:        return 1;
    case CREATED:       return 2;
    case RELAY:         return 3;
    case DESTROY:       return 4;
    case OPEN:          return 5;
    case OPENED:        return 6;
    case OPEN_FAILED:   return 7;
    case CREATE_FAILED: return 8;
    default: throw new IllegalArgumentException();
    }
  }

  /** Converts a byte to a `TorCommand`.
      @return A `TorCommand` associated with the byte, or `null` if there is
              no `TorCommand` associated with it. */
  public static TorCommand fromByte(byte b) {
    switch (b) {
    case 1: return TorCommand.CREATE;
    case 2: return TorCommand.CREATED;
    case 3: return TorCommand.RELAY;
    case 4: return TorCommand.DESTROY;
    case 5: return TorCommand.OPEN;
    case 6: return TorCommand.OPENED;
    case 7: return TorCommand.OPEN_FAILED;
    case 8: return TorCommand.CREATE_FAILED;
    default: return null;
    }
  }
}
