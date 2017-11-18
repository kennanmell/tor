package src;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Arrays;

public class TorCommandManager {
  // Size of a Tor61 cell
  public static final int CELLSIZE = 512;
  
  // returns a byte array of size 512 with circuitID and command inserted
  // as first two fields.
  // treat circuitID as an unsigned short 
  private static byte[] makeCommonHeader(int circuitID, TorCommand command) {
  	byte[] cell = new byte[CELLSIZE];
  	ByteBuffer.wrap(cell).putShort(0, (short) circuitID).put(2, command.toByte());
  	return cell;
  }

  // Returns byte array of OPEN cell.
  public static byte[] makeOpen(int circuitID, int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.OPEN);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }
  
  // Returns byte array of OPENED cell.
  public static byte[] makeOpened(int circuitID, int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.OPENED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }

  // Returns byte array of OPEN cell.
  public static byte[] makeOpenFailed(int circuitID, int openerID, int openedID) {
    byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.OPEN_FAILED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }

  // Returns byte array of CREATE cell.
  public static byte[] makeCreate(int circuitID) {
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATE);
  }

  // Returns byte array of CREATED cell.
  public static byte[] makeCreated(int circuitID) {
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATED);
  }

  // Returns byte array of CREATED_FAILED cell.
  public static byte[] makeCreatedFailed(int circuitID) {
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.CREATE_FAILED);
  }

  // Returns byte array of DESTROY cell.
  public static byte[] makeDestroy(int circuitID) {
    return TorCommandManager.makeCommonHeader(circuitID, TorCommand.DESTROY);
  }

  // Returns byte array of RELAY cell.
  public static byte[] makeRelay(int circuitID, int streamID, RelayCommand relayCommand, byte[] body) {
  	byte[] cell = TorCommandManager.makeCommonHeader(circuitID, TorCommand.RELAY);
  	ByteBuffer.wrap(cell).putShort(3, (short) streamID).putShort(11, (short) body.length).put(13, relayCommand.toByte());
  	System.arraycopy(body, 0, cell, 14, body.length);
  	return cell;
  }

  // Returns Tor Command of cell.
  public static TorCommand getCommand(byte[] cell) {
  	return TorCommand.fromByte(cell[3]);
  }

  // Returns circuit ID of cell.
  public static int getCircuitID(byte[] cell) {
  	 return (int) ByteBuffer.wrap(cell).getShort(0);
  }

  // Returns Opener ID of OPEN/OPENED/OPENED_FAILED cell
  public static int getOpenerID(byte[] cell) {
    TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).getShort(3);
  }

  // Returns OpenedID of OPEN/OPENED/OPENED_FAILED cell
  public static int getOpenedID(byte[] cell) {
    TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).getShort(7);
  }

  // Returns Stream ID of RELAY cell
  public static int getStreamID(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).getShort(3);
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
    return (int) ByteBuffer.wrap(cell).getShort(11);
  }

  // Returns RELAY command of RELAY cell.
  public static byte getRelayCommand(byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return ByteBuffer.wrap(cell).get(13);
  }

  // Returns list view of body
  public static List<Byte> getBody(Byte[] cell) {
  	TorCommand command = TorCommandManager.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return null;
    }
    List<Byte> b = (List<Byte>) Arrays.asList(cell);
    return b.subList(14, cell.length);
  }
}