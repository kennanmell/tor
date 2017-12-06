// thread that reads from tor socket and either puts in tor buffer or 
// web server buffer 
// browser to tor socket write directly
public class RouterSocketReader extends Thread {
	// reads from tor sockets and puts message into buffer. spawns a buffer reader thread to write data to socket? or have a buffer reader waiting at the buffer
	// spawn thread
	private static final int TIMEOUT = 10000;
	private RouterInfo routerInfo;
	private Socket routerSocket;
	private boolean addedMapping;
	// local router table from circuit ID to buffer
	private Map<Integer, LinkedBlockingDeque<byte[]>> circuitIDToBuffer;
	// stores outstanding server socket connections
	private Map<TerminalEntry, LinkedBlockingDeque<byte[]>> terminalEntryToBuffer;

	public RouterSocketReader(Socket routerSocket, RouterInfo routerInfo) {
		this.routerSocket = routerSocket;
		this.routerInfo = routerInfo; 
		this.addedMapping = false;
		this.circuitIDToBuffer = new HashMap<Integer, LinkedBlockingDeque<byte[]>>();
	}

    @Override
	public void run() {
		// read from socket
		DataInputStream input;
		DataOutputStream output;
		int openerID;

		try {
			// setup input and output
			input = new DataInputStream(routerSocket.getInputStream());
			output = new DataOutputStream(routerSocket.getOutputStream());
			routerSocket.setSoTimeout(TIMEOUT);
			
			// read first message
			byte[] message = new byte[TorCommandManager.CELLSIZE];
			input.read(message);
			
			// check if message is open
			if (TorCommandManager.getCommand(message) != TorCommand.OPEN) {
				silentClose(routerSocket, openerID, input, output);
				return;
			}

			// get opener IDs, -1 if message type is incorrect
			openerID = TorCommandManager.getOpenerID(message);
			int openedID = TorCommandManager.getOpenedID(message);

			// incorrect agent ID, send open failed or
			// we already have connection to router, send open failed, close duplicate socket
			if (routerInfo.getAgentID() != openedID || routerInfo.containsRouterSocket(openerID)){
				byte[] openFailed = TorCommandManager.makeOpenFailed(openerID, openedID);
				output.write(openFailed);
				silentClose(routerSocket, openerID, input, output);
				return;
			}

			// add socket to agentiD->socket map
			routerInfo.addRouterSocket(openerID, routerSocket);
			addedMapping = true;

			// send opened
			byte[] response = TorCommandManager.makeOpened(openerID, openedID);
			output.write(response);

			// Create buffer that and thread that will read buffer and send back to this Router Socket
			BlockingQueue<byte[]> torBuffer = new LinkedBlockingDeque<byte[]>();
			(new RouterBufferReader(routerSocket, torBuffer)).start();

			// read incoming tor cell.
			// on create, 
			// if receive relay, check (routerSocket, circuitID). if present, put on buffer (reader thread checks for errors)
			// Intented recipient if (couterSocket, circuitID) was not found. 
			// On begin, get streamID/circuitID, make TCP socket connection, make buffer, put in (circuitID, streamID) -> buffer map
			// 

			// start another bufferReader thread that has socket

			//put in (circuitID, streamID) map
			// 


			// on data, lookup socket associated with (stream, circuitID)

			while (true) {
				byte[] message = new byte[512];
				int res = input.read(message);
				if (res == -1) {
					return;
				}
				int circuitID = TorCommandManager.getCircuitID(message);
				if (TorCommandManager.getCommand(message) == TorCommand.CREATE) {
					// add to map of circuitID to buffer, null value
					if (circuitIDToBuffer.containsKey(circuitID)) {
						torBuffer.add(TorCommandManager.makeCreatedFailed(circuitID));
					} else {
						circuitIDToBuffer.put(circuitID, null);
						torBuffer.add(TorCommandManager.makeCreated(circuitID));
					}
				} else if (TorCommandManager.getCommand(message) == TorCommand.RELAY) {
					RelayCommand relayCmd = TorCommandManager.getRelayCommand(message);
					int streamID = TorCommandManager.getStreamID(message);
					switch(relayCmd) {
						case RelayCommand.BEGIN:
							// check if socket already exists?
							String[] address = TorCommandManager.getBody(message).toString().trim().split(":");
							String ip = address[0];
							int port = Integer.parseInt(address[1]);

							// web server socket
							Socket terminalSocket = new Socket(ip, port);
							BlockingQueue<byte[]> terminalBuffer = new LinkedBlockingDeque<byte[]>();
							
							// add terminal entry to map (circuitID, streamID) -> Terminal Buffer
							TerminalEntry terminalEntry = new TerminalEntry(circuitID, streamID);
							TerminalEntryToBuffer.put(terminalEntry, terminalBuffer);

							// start new socket reader thread
							(new TerminalBufferReader(terminalSocket, terminalBuffer)).start();
							(new TerminalSocketReader(terminalSocket, torBuffer)).start();



							// make a new Terminal Reader thread



							new TerminalSocketReader()
						case RelayCommand.DATA:
							byte[] data = TorCommandManager.getBody(message);
							TerminalEntryToBuffer.get(new TerminalEntry(circuitID, streamID)).add(data);





					}
				}
			}

				RouterEntry self = new RouterEntry(routerSocket, )
			routerInfo.addEntry(   , null)

		    // read second message
			message = new byte[TorCommandManager.CELLSIZE];
			input.read(message);

			// check if message is create
			if (TorCommandManager.getCommand(message) != TorCommand.CREATE) {
				byte[] response = TorCommandManager.makeOpenFailed(openerID, openedID);
				output.write(response);
				silentClose(routerSocket, openerID, input, output);
				return;
			}

			// get prev entry for router table
			int circuitID = TorCommandManager.getCircuitID(message);
			RouterEntry prevEntry = new RouterEntry(routerSocket, circuitID);

			// check if routing table already has circuitID
			if (routerInfo.containsEntry(prevEntry)) {
				byte[] response = TorCommandManager.makeCreatedFailed(circuitID);
				output.write(response);
				silentClose(routerSocket, openerID, input, output);
				return;
			}

			// created received successfully
			// send created
			byte[] response = TorCommandManager.makeCreated(circuitID);
			output.write(response);



			











		} catch (IOException e) {
			silentClose(routerSocket, openerID, input, output);
			return;
		}
		
	}

	public static void silentClose(DataInputStream input) {
		if (input == null) {
			return;
		}
		try {
			input.close();
		} catch(IOException e) {
		}
	}

	public static void silentClose(DataOutputStream output) {
		if (output == null) {
			return;
		}
		try {
			output.close();
		} catch(IOException e) {
		}
	}

	public static void silentClose(Socket socket) {
		if (socket == null) {
			return;
		}
		try {
			socket.close();
		} catch (IOException e) {
		}
	}

	public static void silentClose(Socket socket, int openerID, 
								   DataInputStream input, DataOutputStream output) {
		if (addedMapping) {
			routerInfo.remove(openerID);
		}
		silentClose(socket);
		silentClose(input);
		silentClose(output);
	}
}