
public class RouterSocketReader extends Thread {
	// reads from tor sockets and puts message into buffer. spawns a buffer reader thread to write data to socket? or have a buffer reader waiting at the buffer
	// spawn thread
	private static final int TIMEOUT = 10000;
	private RouterInfo routerInfo;
	private Socket routerSocket;

	public RouterSocketReader(Socket routerSocket, RouterInfo routerInfo) {
		this.routerSocket = routerSocket;
		this.routerInfo = routerInfo; 
	}

    @Override
	public void run() {
		// read from socket
		DataInputStream input;
		DataOutputStream output;
		try {
			input = new DataInputStream(routerSocket.getInputStream());
			output = new DataOutputStream(gatewaySocket.getOutputStream());
			routerSocket.setSoTimeout(TIMEOUT);
		} catch (IOException e) {
			return;
		}

		byte[] message = new byte[TorCommandManager.CELLSIZE];
		try {
			input.read(message);
		} catch (IOException e) {
			return;
		}

		if (TorCommandManager.getCommand(message) != TorCommand.OPEN) {
			byte[] response = TorCommandManager.makeOpenFailed(TorCommandManager.getOpenerID(message), TorCommandManager.getOpenedID(message));
			output.write(response);
			return;
		}











		BlockingQueue<byte[]> torBuffer = new LinkedBlockingDeque<byte[]>();
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

	public static void silentClose(Socket socket, DataInputStream input, DataOutputStream output) {
		silentClose(socket);
		silentClose(input);
		silentClose(output);
	}
}