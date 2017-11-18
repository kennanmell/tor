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
    byte[] cell = TorCommandHandler.makeCommonHeader(circuitID, TorCommand.OPEN);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }
  
  // Returns byte array of OPENED cell.
  public static byte[] makeOpened(int circuitID, int openerID, int openedID) {
    byte[] cell = TorCommandHandler.makeCommonHeader(circuitID, TorCommand.OPENED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }

  // Returns byte array of OPEN cell.
  public static byte[] makeOpenFailed(int circuitID, int openerID, int openedID) {
    byte[] cell = TorCommandHandler.makeCommonHeader(circuitID, TorCommand.OPEN_FAILED);
    ByteBuffer.wrap(cell).putInt(3, openerID).putInt(7, openerID);
    return cell;
  }

  // Returns byte array of CREATE cell.
  public static byte[] makeCreate(int circuitID) {
    return TorCommandHandler.makeCommonHeader(circuitID, TorCommand.CREATE);
  }

  // Returns byte array of CREATED cell.
  public static byte[] makeCreated(int circuitID) {
    return TorCommandHandler.makeCommonHeader(circuitID, TorCommand.CREATED);
  }

  // Returns byte array of CREATED_FAILED cell.
  public static byte[] makeCreatedFailed(int circuitID) {
    return TorCommandHandler.makeCommonHeader(circuitID, TorCommand.CREATE_FAILED);
  }

  // Returns byte array of DESTROY cell.
  public static byte[] makeDestroy(int circuitID) {
    return TorCommandHandler.makeCommonHeader(circuitID, TorCommand.DESTROY);
  }

  // Returns byte array of RELAY cell.
  public static byte[] makeRelay(int circuitID, int streamID, RelayCommand relayCommand, byte[] body) {
  	byte[] cell = TorCommandHandler.makeCommonHeader(circuitID, TorCommand.RELAY);
  	ByteBuffer.wrap(cell).putShort(3, (short) streamID).putShort(11, (short) body.length).put(13, relaycommand.toByte());
  	System.arraycopy(body, 0, cell, 14, body.length);
  	return cell;
  }

  // Returns Tor Command of cell.
  public TorCommand getCommand(byte[] cell) {
  	return cell[3];
  }

  // Returns circuit ID of cell.
  public int getCircuitID(byte[] cell) {
  	 return (int) ByteBuffer.wrap(cell).getShort(0);
  }

  // Returns Opener ID of OPEN/OPENED/OPENED_FAILED cell
  public int getOpenerID(byte[] cell) {
    TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.getShort(3);
  }

  // Returns OpenedID of OPEN/OPENED/OPENED_FAILED cell
  public int getOpenedID(byte[] cell) {
    TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.OPEN && command != TorCommand.OPENED && command != TorCommand.OPEN_FAILED) {
    	return -1;
    }
    return ByteBuffer.getShort(7);
  }

  // Returns Stream ID of RELAY cell
  public int getStreamID(byte[] cell) {
  	TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return ByteBuffer.getShort(3);
  }

  // Returns true if cell has valid 0 region
  public boolean relayIsValid(byte[] cell) {
  	TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return false;
    }
    return (int) ByteBuffer.getShort(5) == 0;
  }

  // Returns length of cell body.
  public int getBodyLength(byte[] cell) {
  	TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return (int) ByteBuffer.getShort(11);
  }

  // Returns RELAY command of RELAY cell.
  public byte getRelayCommand(byte[] cell) {
  	TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return -1;
    }
    return (int) ByteBuffer.getShort(13);
  }

  // Returns list view of body
  public List<Byte> getBody(byte[] cell) {
  	TorCommand command = TorCommandHandler.getCommand(cell);
    if (command != TorCommand.RELAY) {
    	return null;
    }
    return Arrays.asList(cell).subList(14, cell.length);
  }
}