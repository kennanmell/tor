package src;

/** Represents the 8 types of commands used by the agent and server to communicate. */
public enum RelayCommand {
  BEGIN, DATA, END, CONNECTED, EXTEND, EXTENDED, BEGIN_FAILED, EXTEND_FAILED;

  /** Converts this `RelayCommand` to a byte.
      @return A `byte` corresponding to the `RelayCommand`.
      @throws IllegalArgumentException if the `RelayCommand` is not recognized. */
  public byte toByte() {
    switch (this) {
    case BEGIN:        return 1;
    case DATA:       return 2;
    case END:         return 3;
    case CONNECTED:       return 4;
    case EXTEND:          return 6;
    case EXTENDED:        return 7;
    case BEGIN_FAILED:   return 0xb;
    case EXTEND_FAILED: return 0xc;
    default: throw new IllegalArgumentException();
    }
  }

  /** Converts a byte to a `RelayCommand`.
      @return A `RelayCommand` associated with the byte, or `null` if there is
              no `RelayCommand` associated with it. */
  public static RelayCommand fromByte(byte b) {
    switch (b) {
    case 1: return RelayCommand.BEGIN;
    case 2: return RelayCommand.DATA;
    case 3: return RelayCommand.END;
    case 4: return RelayCommand.CONNECTED;
    case 6: return RelayCommand.EXTEND;
    case 7: return RelayCommand.EXTENDED;
    case 0xb: return RelayCommand.BEGIN_FAILED;
    case 0xc: return RelayCommand.EXTEND_FAILED;
    default: return null;
    }
  }
}
