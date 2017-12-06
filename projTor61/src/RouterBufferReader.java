// thread that reads from tor router buffer and sends to router Socket
public class RouterBufferReader extends Thread {
	private static final BUFFER_TIMEOUT = 25000;
	private RouterInfo routerInfo;
	private BlockingQueue<byte[]> torBuffer;

	public RouterBufferReader(Socket routerSocket, BlockingQueue<byte[]> torBuffer) {
		this.routerInfo = routerInfo;
		this.torBuffer = torBuffer;
		this.routerSocket;
	}

	@Override
	public void run() {
		DataOutputStream output = new DataOutputStream(routerSocket.getOutputStream());
		while(true){
			byte[] torCell = torBuffer.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS);
			if (torCell.length == 512) {
				output.write(torCell);
			}
		}
	}
}