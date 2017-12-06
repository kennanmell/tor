// thread that reads from tor socket and either puts in tor buffer or 
// web server buffer 
// browser to tor socket write directly
public class TerminalSocketReader extends Thread {
	// reads from tor sockets and puts message into buffer. spawns a buffer reader thread to write data to socket? or have a buffer reader waiting at the buffer
	// spawn thread
	private static final int TIMEOUT = 10000;
	private RouterInfo routerInfo;
	private Socket routerSocket;
	private boolean addedMapping;
	// local router table from circuit ID to 
	private Map<Integer, > routerTable;


	public TerminalSocketReader(Socket routerSocket, RouterInfo routerInfo) {
		this.routerSocket = routerSocket;
		this.routerInfo = routerInfo; 
		this.addedMapping = false;
	}

    @Override
	public void run() {
		
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