package src;

import java.util.HashMap;
import java.util.Map;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class CircuitInitThread extends Thread {
	private RegAgentThread regThread;
	private RouterInfo routerInfo;
	private static final int TIMEOUT = 10000;

    public CircuitInitThread(RouterInfo routerInfo, RegAgentThread regThread) {
    	this.regThread = regThread;
    	this.routerInfo = routerInfo;
    }

    @Override
    public void run() {
    	while (true) {
    		if (resetCircuit()) {
    			return;
    		}
    	}
    }

    public static boolean resetCircuit() {
		// expensive operation, get all available services from registration thread
		Set<Service> services = regThread.getAllServices();

		// check that there is at least one router available
		if (services.size() == 0) {
			return false;
		}

		Service firstStop = getRandomService(services);
		int thisAgentID = routerInfo.getAgentID();
		int nextAgentID = firstStop.getAgentID();
		Socket gatewaySocket = null;
		DataOutputStream output = null;
		DataInputStream input = null;

		// keep looping until we have established circuit or we have run out of possible services
		while (true) {
			// check if we already have a TCP connection
	        if (routerInfo.getRouterSocket(nextAgentID) != null) {
	        	gatewaySocket = routerInfo.getRouterSocket(nextAgentID);
	        } else {
				// Keep asking registration thread for new routers until we can make a connection
				while (gatewaySocket == null) {
					if (services.size() == 0) {
						// register self again		
						return false;
					}
					try {
			    		gatewaySocket = new Socket(firstStop.ip, firstStop.port);
			    		routerInfo.addRouterSocket(nextAgentID, gatewaySocket);
			    	} catch (IOException e) {
			    		gatewaySocket = null;
			    		services.remove(firstStop);
			    		firstStop = getRandomService(services);
			    		nextAgentID = firstStop.getAgentID();
			    	}
			    }
			}

			// send messaages to make circuit
			try {
				// TCP connection has been established to first hop router.
				int gatewayCircuitID = routerInfo.getNextOddCircuitID(nextAgentID);
				RouterEntry gatewayEntry = new RouterEntry(gatewaySocket, gatewatCircuitID);

				// set gateway router
				routerInfo.setGatewayEntry(gatewayEntry);

				// create output/input streams
				output = new DataOutputStream(gatewaySocket.getOutputStream());
				input = new DataInputStream(gatewaySocket.getInputStream());

				// try sending open message to first hop router
				byte[] open = TorCommandManager.makeOpen(thisAgentID, nextAgentID);
				output.write(open);
				gatewaySocket.setSoTimeout(TIMEOUT);

				// see if opened was received
				byte[] response = new byte[TorCommandManager.CELLSIZE];
				input.read(response);
				boolean matched = TorCommandManager.getCircuitID(open) == TorCommandManager.getCircuitID(response)
				                  && TorCommandManager.getOpenerID(open) == TorCommandManager.getOpenerID(response)
				                  && TorCommandManager.getOpenedID(open) == TorCommandManager.getOpenedID(response);
				if (TorCommandManager.getCommand(response) != TorCommand.OPENED || !matched) {
					throw new IOException();
				}

				// try sending create message to first hop router
				byte[] create = TorCommandManager.makeCreate(gatewayCircuitID);
				output.write(create);
				gatewaySocket.setSoTimeout(TIMEOUT);

				// see if created was received
				byte[] response = new byte[TorCommandManager.CELLSIZE];
				input.read(response);
				if (TorCommandManager.getCommand(response) != TorCommand.CREATED
						|| TorCommandManager.getCircuitID(response) != gatewayCircuitID) {
					throw new IOException();
				}

				// send 2 extends
				int extended = 0;
		        while (extended < 2) {
					// get the appropriate command
					Service currStop = getRandomService(services);
					String address = currStop.ip.getHostAddress() + ":" + firstStop.port + "\0" + currStop.getAgentID();
					byte[] body = address.getBytes();
					byte[] command = TorCommandManager.makeRelay(gatewayCircuitID, 0, RelayCommand.EXTEND, body);

					// send extend command
					output.write(command);
					gatewaySocket.setSoTimeout(TIMEOUT);

					try {
						byte[] response = new byte[TorCommandManager.CELLSIZE];
						input.read(response);
						if (TorCommandManager.getRelayCommand(response) != RelayCommand.EXTENDED
								|| TorCommandManager.getCircuitID(reponse) != gatewayCircuitID) {
							throw new IOException();
						}
					} catch (IOException e) {
						services.remove(currStop);
						if (services.size() == 0) {
							routerInfo.removeRouterSocket(nextAgentID, gatewaySocket);
							closeHandling(gatewaySocket, output, input);
							// register self
							return false;
						}
						continue;
					}
					extended++;
				}
				break;
			} catch (IOException e) {
				routerInfo.removeRouterSocket(nextAgentID, gatewaySocket);
				closeHandling(gatewaySocket, output, input);
				gatewaySocket = null;
				services.remove(firstStop);
				if (services.size() == 0) {
					// register self
					return false;
				}
				firstStop = getRandomService(services);
				nextAgentID = firstStop.getAgentID();
				continue;
			}
		}
		return true;
	}

	public static void silentCloseSocket(Socket socket) {
		if (socket == null) {
			return;
		} 

		try {
			socket.close();
		} catch (IOException e) {
			// no op
		}
	}

	public static void silentCloseOutput(DataOutputStream output) {
		if (output == null) {
			return;
		}

		try {
			output.close();
		} catch (IOException e) {
			// no op
		}
	}

	public static void silentCloseInput(DataOutputStream input) {
		if (input == null) {
			return;
		}

		try {
			input.close();
		} catch (IOException e) {
			// no op
		}
	}

	public static boolean closeHandling(Socket socket, DataOutputStream output, DataInputStream input) {
		silentCloseSocket(socket);
		silentCloseOutput(output);
		silentCloseInput(input);
        routerInfo.setGatewayEntry(null);
        return false;
	}

	public static Service getRandomService(Set<Service> services) {
		List<Service> temp = new ArrayList(services);
		if (temp.size() == 0) {
			return null;
		}
		Collections.shuffle(temp);
		return temp.get(0);
	}
}
