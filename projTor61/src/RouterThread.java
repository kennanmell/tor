package src;

import java.util.HashMap;
import java.util.Map;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class RouterThread extends Thread {
	private RegAgentThread regThread;
	private RouterInfo routerInfo;
	private ServerSocket serverSocket;
	private static final int TIMEOUT = 10000;

    public RouterThread(RouterInfo routerInfo, RegAgentThread regThread) {
    	this.regThread = regThread;
    	this.routerInfo = routerInfo;
    }

	public void run() {
	  // 2. create streams

	  // 3. carry traffic
	}
}