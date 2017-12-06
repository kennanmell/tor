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

	public RouterSocketReader(Socket routerSocket, RouterInfo routerInfo) {
		this.routerSocket = routerSocket;
		this.routerInfo = routerInfo; 
		this.addedMapping = false;
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

			// send opened
			byte[] response = TorCommandManager.makeOpened(openerID, openedID);
			output.write(response);

			// add agentID -> TCP connection mapping
			routerInfo.addRouterSocket(openerID, routerSocket);
			addedMapping = true;

			// add buffer for TCP Router Socket
			BlockingQueue<byte[]> torBuffer = new LinkedBlockingDeque<byte[]>();
			routerInfo.addTorBuffer(openerID, torBuffer);

			// start buffer reader thread
			(new RouterBufferReader(routerInfo, torBuffer)).start();

			// keep reading from Tor Router socket, write to either Tor Buffer or Webserver Buffer
			while (true) {
				byte[] message = new byte[512];
				int res = input.read(message);
				if (res == -1) {

				}
			}



			// 











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