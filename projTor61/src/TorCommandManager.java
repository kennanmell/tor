package src;

import java.nio.ByteBuffer;

// TorCommandManager is used to make Tor61 messages and to extract fields
// from the messages.
public class TorCommandManager {
  // Size of a Tor61 cell
  public static final int CELLSIZE = 512;
  public static final int MAX_U_SHORT = 65535;

  // Returns byte array of OPEN cell.
  public static byte[] makeOpen(int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(0, TorCommand.OPEN);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openedID);
    return cell;
  }
  
  // Returns byte array of OPENED cell.
  public static byte[] makeOpened(int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(0, TorCommand.OPENED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openedID);
    return cell;
  }

  // Returns byte array of OPEN cell.
  public static byte[] makeOpenFailed(int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(0, TorCommand.OPEN_FAILED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openedID);
    return cell;
  }

  // Returns byte array of CREATE cell.
  public static byte[] makeCreate(int circuitID) {
	  if (circuitID > MAX_U_SHORT) {
		  return null;
	  }
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATE);
  }

  // Returns byte array of CREATED cell.
  public static byte[] makeCreated(int circuitID) {
	  if (circuitID > MAX_U_SHORT) {
		  return null;
	  }
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATED);
  }

  // Returns byte array of CREATED_FAILED cell.
  public static byte[] makeCreatedFailed(int circuitID) {
	  if (circuitID > MAX_U_SHORT) {
		  return null;
	  }
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATE_FAILED);
  }

  // Returns byte array of DESTROY cell.
  public static byte[] makeDestroy(int circuitID) {
	  if (circuitID > MAX_U_SHORT) {
		  return null;
	  }
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.DESTROY);
  }

  // Returns byte array of RELAY cell.
  public static byte[] makeRelay(int circuitID, int streamID, RelayCommand relayCommand, byte[] body) {
  	if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
  		return null;
  	}
	  byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.RELAY);
  	ByteBuffer.wrap(cell).putShort(3, (short) streamID).putShort(11, (short) body.length).put(13, relayCommand.toByte());
  	System.arraycopy(body, 0, cell, 14, body.length);
  	return cell;
  }

  // Returns byte array of RELAY cell.
  public static byte[] makeRelay(int circuitID, int streamID, RelayCommand relayCommand) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.RELAY);
    ByteBuffer.wrap(cell).putShort(3, (short) streamID).put(13, relayCommand.toByte());
    return cell;
  }

  // Returns byte array of RELAY DATA cell.
  public static byte[] makeData(int circuitID, int streamID, byte[] body) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    return makeRelay(circuitID, streamID, RelayCommand.DATA, body);
  }

  // Returns byte array of RELAY END cell.
  public static byte[] makeEnd(int circuitID, int streamID) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    return makeRelay(circuitID, streamID, RelayCommand.END);
  }

  // Returns byte array of RELAY CONNECTED cell.
  public static byte[] makeConnected(int circuitID, int streamID) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    return makeRelay(circuitID, streamID, RelayCommand.CONNECTED);
  }

  // Returns byte array of RELAY BEGIN cell.
  public static byte[] makeBegin(int circuitID, int streamID, String hostIdentifier, int port) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    byte[] body = (hostIdentifier + ":" + port).getBytes();
    byte[] nullString = new byte[body.length + 1];
    System.arraycopy(body, 0, nullString, 0, body.length);
    nullString[nullString.length - 1] = '\0';
    return makeRelay(circuitID, streamID, RelayCommand.BEGIN, nullString);
  }

  // Returns byte array of RELAY BEGIN cell.
  public static byte[] makeExtend(int circuitID, int streamID, String address, int agentID) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    byte[] output = new byte[address.length() + 5];
    byte[] addressBytes = address.getBytes();
    System.arraycopy(addressBytes, 0, output, 0, addressBytes.length);
    ByteBuffer.wrap(output).putChar(addressBytes.length, '\0').putInt(addressBytes.length + 1, agentID);
    return makeRelay(circuitID, streamID, RelayCommand.EXTEND, output);
  }

  // Returns byte array of EXTENDED cell.
  public static byte[] makeExtended(int circuitID, int streamID) {
	  if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
	    return null;
	  }
    return makeRelay(circuitID, streamID, RelayCommand.EXTENDED);
  }

  // Returns byte array of RELAY BEGIN_FAILED cell.
  public static byte[] makeBeginFailed(int circuitID, int streamID) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    return makeRelay(circuitID, streamID, RelayCommand.BEGIN_FAILED);
  }

  // Returns byte array of RELAY EXTEND_FAILED cell.
  public static byte[] makeExtendFailed(int circuitID, int streamID) {
    if (streamID > MAX_U_SHORT || circuitID > MAX_U_SHORT) {
      return null;
    }
    return makeRelay(circuitID, streamID, RelayCommand.EXTEND_FAILED);
  }

  // Returns Tor Command of cell.
  public static TorCommand getCommand(byte[] cell) {
  	return TorCommand.fromByte(cell[2]);
  }

  // Returns circuit ID of cell.
  public static int getCircuitID(byte[] cell) {
  	 return (int) (ByteBuffer.wrap(cell).getShort(0) & 0xffff);
  }

  // Returns Opener ID of OPEN/OPENED/OPENED_FAILED cell
  public static int getOpenerID(byte[] cell) {
    TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).getInt(3);
  }

  // Returns OpenedID of OPEN/OPENED/OPENED_FAILED cell
  public static int getOpenedID(byte[] cell) {
    TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).getInt(7);
  }

  // Returns Stream ID of RELAY cell
  public static int getStreamID(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return (int) (ByteBuffer.wrap(cell).getShort(3) & 0xffff);
  }

  // Returns true if cell has valid 0 region
  public static boolean relayIsValid(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return false;
    }
    return (int) ByteBuffer.wrap(cell).getShort(5) == 0;
  }

  // Returns length of cell body.
  public static int getBodyLength(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return (int) (ByteBuffer.wrap(cell).getShort(11) & 0xffff);
  }

  // Returns RELAY command of RELAY cell.
  public static RelayCommand getRelayCommand(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return null;
    }
    return RelayCommand.fromByte(ByteBuffer.wrap(cell).get(13));
  }

  // Returns body of RELAY
  public static byte[] getBody(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return null;
    }
    int length = getBodyLength(cell);
    byte[] ret = new byte[length];
    System.arraycopy(cell, 14, ret, 0, length);
    return ret;
  }

  private static byte[] makeCommonHeader(int circuitID, TorCommand command) {
    if (circuitID > MAX_U_SHORT) {
      return null;
    }
    byte[] cell = new byte[CELLSIZE];
    ByteBuffer.wrap(cell).putShort(0, (short) circuitID).put(2, command.toByte());
    return cell;
  }
}