package src;

import java.util.HashMap;
import java.util.Map;
import java.net.Socket;

public class RouterThread extends Thread {
	private RegAgentThread regThread;
	private RouterInfo routerInfo;
	private static final int TIMEOUT = 10000;

    public RouterThread(RouterInfo routerInfo, RegAgentThread regThread) {
    	this.regThread = regThread;
    	this.routerInfo = routerInfo;
    }

	public void run() {
	// 1. establish circuit
      // get circuit stops
      
      






	  // 2. create stream
	  // 3. carry traffic

	}
     // make this into a new thread so we can service other routers at the same time we set up our own thread
    // 1. establish circuit, set gateway entry, return false on failure
	public static boolean resetCircuit() {
		Services[] circuitStops = regThread.getNewCircuit();

		// check that 3 routers are reported
		if (circuitStops.length != 3) {
			return false;
		}

		Service firstStop = circuitStops[0];
		int thisAgentID = routerInfo.getAgentID();
		int nextAgentID = firstStop.getAgentID();
		Socket gatewaySocket = null;
		
		try {
			// check if we already have a TCP connection
	        if (routerInfo.getSocket(nextAgentID) != null) {
	        	gatewaySocket = routerInfo.getSocket(nextAgentID);
	        } else {
				// Keep asking registration thread for new routers until we can make a connection
			    gatewaySocket = new Socket(firstStop.ip, firstStop.port);
			}

			// TCP connection has been established to first hop router. 
			int gatewayCircuitID = routerInfo.getNextOddCircuitID(nextAgentID);
			RouterEntry gatewayEntry = new RouterEntry(gatewaySocket, gatewatCircuitID);

			// set gateway router
			routerInfo.setGatewayEntry(gatewayEntry);
			
			// create output/input streams
			DataOutputStream output = new DataOutputStream(gatewaySocket.getOutputStream());
			DataInputStream input = new DataInputStream(gatewaySocket.getInputStream());
	
	        // try sending open message to first hop router
			byte[] open = TorCommandManager.makeOpen(thisAgentID, nextAgentID);
			output.write(open);
			gatewaySocket.setSoTimeout(TIMEOUT);

			try {
				byte[] response = new byte[TorCommandManager.CELLSIZE];
				input.read(response);
				boolean matched = TorCommandManager.getCircuitID(open) == TorCommandManager.getCircuitID(response)
				                  && TorCommandManager.getOpenerID(open) == TorCommandManager.getOpenerID(response)
				                  && TorCommandManager.getOpenedID(open) == TorCommandManager.getOpenedID(response);
				if (TorCommandManager.getCommand(response) != TorCommand.OPENED || !matched) {
					return closeHandling(gatewaySocket);
				}
			} catch (SocketException e) {
				// timed out
				return closeHandling(gatewaySocket);
			}

			// try sending create message to first hop router
			byte[] create = TorCommandManager.makeCreate(gatewayCircuitID);
			output.write(create);
			gatewaySocket.setSoTimeout(TIMEOUT);

			try {
				byte[] response = new byte[TorCommandManager.CELLSIZE];
				input.read(response);
				if (TorCommandManager.getCommand(response) != TorCommand.CREATED 
						|| TorCommandManager.getCircuitID(response) != gatewayCircuitID) {
					return closeHandling(gatewaySocket);
				}
			} catch (SocketException e) {
				// timed out
				return closeHandling(gatewaySocket);
			}

	        // send open, followed by 2 extends to create circuit
	        for (int i = 1; i < circuitHops.length; i++) {
				// get the appropriate command
				Service currStop = circuitStops[i];
				String address = currStop.ip.getHostAddress() + ":" + firstStop.port + "\0" + currStop.getAgentID();
				byte[] body = address.getBytes();
				byte[] command = TorCommandManager.makeRelay(gatewayCircuitID, 0, RelayCommand.EXTEND, body);
				// send extend command
				output.write(command);
				gatewaySocket.setSoTimeout(TIMEOUT);
				try {
					byte[] response = new byte[TorCommandManager.CELLSIZE];
					input.read(response);

				} catch (SocketException e) {

				}	
			}
		} catch (IOException e) {
        	return closeHandling(gatewaySocket);
		}
		return true;
	}

	public static silentClose(Socket s) {
		if (s == null) {
			return;
		} try {
			s.close();
		} catch (IOException e) {
		}
	}

	public static boolean closeHandling(Socket s) {
		silentClose(s);
        routerInfo.setGatewayEntry(null);
        return false;
	}
}