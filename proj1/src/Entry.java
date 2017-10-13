package src;

public class Entry {
	private int serviceIP;
	private int port;
	private int serviceData;
	
	public Entry(int serviceIP, int port, int serviceData) {
		this.serviceIP = serviceIP;
		this.port = port;
		this.serviceData = serviceData;
	}

	public int getIP() {
		return serviceIP;
	}

	public int getPort() {
		return port;
	}

	public int getData() {
		return serviceData;
	}
}