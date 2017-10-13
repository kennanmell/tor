package src;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

// Regstistration Message Manager, assumes bytes received are in netork order, conerts to host order
public class RMManager {
	public static final int MAGIC_NUMBER = 0xc461;
	public static final int MIN_PACKET_LENGTH = 4;
	public static final int REGISTER = 1;
	public static final int REGISTERED = 2;
	public static final int FETCH = 3;
	public static final int FETCH_RESPONSE = 4;
	public static final int UNREGISTER = 5;
	public static final int PROBE = 6;
	public static final int ACK = 7;

	// confirms if packet is valid
	public static boolean isValid(DatagramPacket packet) {
		return packet != null && packet.getData() != null && packet.getData().length >= MIN_PACKET_LENGTH
				&& packet.getData()[0] == ((byte) (RMManager.MAGIC_NUMBER >> 8)) &&
				packet.getData()[1] == ((byte) RMManager.MAGIC_NUMBER);
	}

	//  retrieves sequence num
	public static int getSequence(DatagramPacket packet) {
		if (packet == null) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b == null || b.length < MIN_PACKET_LENGTH) {
			return -1;
		}
		return b[2];
	}

	// retrieves type
	public static int getType(DatagramPacket packet) {
		if (packet == null) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b == null || b.length < MIN_PACKET_LENGTH) {
			return -1;
		}
		return b[3];
	}

    // for register or unregister only
	public static int getServiceIP(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTER || type != UNREGISTER) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.lenth < 8) {
			return -1;
		}
		int ip = 0;
		ip += (b[7] << 24);
		ip += (b[6] << 16);
		ip += (b[5] << 8);
		ip += (b[4] >> 24);
		return ip;
	}

	// for register and unregister only
	public static int getPort(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTER || type != UNREGISTER) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 10) {
			return -1;
		}
		int port = 0;
		port += (b[9] << 8);
		port += (b[8] >> 8);
		return port;
	}

	// only for register
	public static int getServiceData(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTER) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 15) {
			return -1;
		}
		int sd = 0;
		sd += (b[13] << 24);
		sd += (b[12] << 16);
		sd += (b[11] << 8);
		sd += (b[10] >> 24);
		return sd;
	}

	// only for register
	public static int getRegisterServiceNameLength(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTER) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 15) {
			return -1;
		}
		return packet.getData()[14];
	}

	// only for fetch
	public static int getFetchServiceNameLength(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != FETCH) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 5) {
			return -1;
		}
		return packet.getData()[4];
	}

	// returns null on error, only for resgister
	public static String getRegisterServiceName(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTER) {
			return null;
		}
		byte b[] = packet.getData();
		if (b.length < 15) {
			return null;
		} else if (b.length < 16) {
			return "";
		}
		return new String(b, 16, b.length - 15);
	}

	// returns null on error, only for resgister
	public static String getFetchServiceName(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != FETCH) {
			return null;
		}
		byte b[] = packet.getData();
		if (b.length < 5) {
			return null;
		} else if (b.length < 6) {
			return "";
		}
		return new String(b, 5, b.length - 5);
	}


	// returns -1 on error, only for registered
	public static int getLifetime(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != REGISTERED) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 6) {
			return -1;
		} 
		int lt = 0;
		lt += (b[5] << 8);
		lt += (b[4] >> 8);
		return lt;
	}

	// only for fetchresponse
	public static int getNumEntries(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != FETCH_RESPONSE) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 5) {
			return -1;
		} 
		return b[4];
	}

	// onyl for fetch response
	public static List<Entry> getEntries(DatagramPacket packet) {
		int type = RMManager.getType(packet);
		if(type != FETCH_RESPONSE) {
			return -1;
		}
		byte b[] = packet.getData();
		if (b.length < 5) {
			return -1;
		} 
		int numEntries = (b.length - 5) / 10;
		List<Entry> entries = new ArrayList<Entry>();
		for (int i = 0; i < numEntries; i++) {
			int currPos = 5 + (10 * i);
			int ip = (b[currPos] | (b[currPos + 1] << 8) 
					| (b[currPos + 2] << 16) | (b[currPos + 3] << 24);
			int port = b[currPos + 4] | (b[currPos + 5] << 8);
			int data = (b[currPos + 6] | (b[currPos + 7] << 8) 
					| (b[currPos + 8] << 16) | (b[currPos + 9] << 24);
			entries.add(new Entry(ip, port, data));
		}
		return entries;
	}

	// creates new register message contents
	public static byte[] register(int sequence, int serviceIP, int port, 
			int serviceData, String serviceName) {
		int nameLength = serviceName.length();
		if (nameLength > 255) {
			return null;
		}
		byte ret[] = new byte[15 + nameLength];
		ret[0] = (byte) 0xc4;
		ret[1] = (byte) 0x61;
		ret[2] = (byte) sequence;
		ret[3] = (byte) 1;
		ret[4] = (byte) (serviceIP >> 24);
		ret[5] = (byte) (serviceIP >> 16);
		ret[6] = (byte) (serviceIP >> 8);
		ret[7] = (byte) serviceIP;
		ret[8] = (byte) (port >> 8);
		ret[9] = (byte) port;
		ret[10] = (byte) (serviceData >> 24);
		ret[11] = (byte) (serviceData >> 16);
		ret[12] = (byte) (serviceData >> 8);
		ret[13] = (byte) serviceData;
		ret[14] = (byte) nameLength;
		for (int i = 15; i < ret.length; i++) {
			ret[i] = (byte) serviceName.charAt(i - 15);
		}
		return ret;
	}

	// creates new fetch message contents
	public static byte[] fetch(int sequence, String serviceName) {
			int nameLength = serviceName.length();
		if (nameLength > 255) {
			return null;
		}
		byte ret[] = new byte[5 + nameLength];
		ret[0] = (byte) 0xc4;
		ret[1] = (byte) 0x61;
		ret[2] = (byte) sequence;
		ret[3] = (byte) 3;
		ret[4] = (byte) nameLength;
		for (int i = 6; i < ret.length; i++) {
			ret[i] = (byte) serviceName.charAt(i - 6);
		}
		return ret;
	}

	// creates new unregister message contents
	public static byte[] unregister(int sequence, int serviceIP, int port) {
		byte ret[] = new byte[10];
		ret[0] = (byte) 0xc4;
		ret[1] = (byte) 0x61;
		ret[2] = (byte) sequence;
		ret[3] = (byte) 5;
		ret[4] = (byte) (serviceIP >> 24);
		ret[5] = (byte) (serviceIP >> 16);
		ret[6] = (byte) (serviceIP >> 8);
		ret[7] = (byte) serviceIP;
		ret[8] = (byte) (port >> 8);
		ret[9] = (byte) port;
		return ret;
	}

	// creates new probe message contents
	public static byte[] probe(int sequence) {
		byte ret[] = new byte[4];
		ret[0] = (byte) 0xc4;
		ret[1] = (byte) 0x61;
		ret[2] = (byte) sequence;
		ret[3] = (byte) 6;
		return ret;
	}

	// creates new ack message contents
	public static byte[] ack(int sequence) {
		byte ret[] = new byte[4];
		ret[0] = (byte) 0xc4;
		ret[1] = (byte) 0x61;
		ret[2] = (byte) sequence;
		ret[3] = (byte) 7;
		return ret;
	}
}