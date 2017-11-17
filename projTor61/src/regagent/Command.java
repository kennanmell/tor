package regagent;

/** Represents the 7 types of commands used by the agent and server to communicate. */
public enum Command {
  REGISTER, REGISTERED, FETCH, FETCHRESPONSE, UNREGISTER, PROBE, ACK;

  /** Converts this `Command` to a byte.
      @return A `byte` corresponding to the `Command`.
      @throws IllegalArgumentException if the `Command` is not recognized. */
  public byte toByte() {
    switch (this) {
    case REGISTER:      return 1;
    case REGISTERED:    return 2;
    case FETCH:         return 3;
    case FETCHRESPONSE: return 4;
    case UNREGISTER:    return 5;
    case PROBE:         return 6;
    case ACK:           return 7;
    default: throw new IllegalArgumentException();
    }
  }

  /** Converts a byte to a `Command`.
      @return A `Command` associated with the byte, or `null` if there is
              no `Command` associated with it. */
  public static Command fromByte(byte b) {
    switch (b) {
    case 1: return Command.REGISTER;
    case 2: return Command.REGISTERED;
    case 3: return Command.FETCH;
    case 4: return Command.FETCHRESPONSE;
    case 5: return Command.UNREGISTER;
    case 6: return Command.PROBE;
    case 7: return Command.ACK;
    default: return null;
    }
  }
}
