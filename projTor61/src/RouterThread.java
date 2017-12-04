package src;

import java.util.HashMap;
import java.util.Map;
import java.net.Socket;

public class RouterThread extends Thread {
	private RegAgentThread regThread;
	private RouterInfo routerInfo;

    public RouterThread(RouterInfo routerInfo, RegAgentThread regThread) {
    	this.regThread = regThread;
    	this.routerInfo = routerInfo;
    }

	public void run() {
	// 1. establish circuit
      // get circuit stops

      






	  // 2. create stream
	  // 3. carry traffic

	}

    // 1. establish circuit
	public static void resetCircuit() {
		Services[] circuitStops = regThread.getNewCircuit();










	}

}