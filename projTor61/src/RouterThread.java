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
	private static final int TIMEOUT = 10000;

    public RouterThread(RouterInfo routerInfo, RegAgentThread regThread) {
    	this.regThread = regThread;
    	this.routerInfo = routerInfo;
    }

	public void run() {
	  // 1. establish self circuit and gateway entry

	  // 2. create stream

	  // 3. carry traffic

	  // do .join on circuitInitThread to wait for it to finish settin up own circuit

	}
}