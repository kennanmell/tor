package src;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DirectedHopHandlerThread extends Thread {

  private final Socket readSocket;
  private Map<Integer, Socket> serverForStream;
  private Map<Integer, BlockingQueue<byte[]>> bufferForCircuit;

  public DirectedHopHandlerThread(Socket readSocket) {
    this.readSocket = readSocket;
    this.serverForStream = new HashMap<>();
    this.bufferForCircuit = new HashMap<>();
  }

  @Override
  public void run() {
    byte[] buf = new byte[512];
    if (!SocketManager.socketWasInitiated(readSocket)) {
      if (readSocket.getInputStream().read(buf) != 512) {
        readSocket.close();
        return;
      }

      if (commandForCell(buf) == TorCommand.OPEN) {
        
      }
    }
  }

  private TorCommand commandForCell(byte[] buf) {
    return TorCommand.fromByte(buf[2]);
  }
}
