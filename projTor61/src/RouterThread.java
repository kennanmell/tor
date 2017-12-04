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
	  // 1. establish self circuit
      (new CircuitInitThread(this.routerInfo, this.regThread)).start();
      
      






	  // 2. create stream
	  // 3. carry traffic

	}
     // make this into a new thread so we can service other routers at the same time we set up our own thread
    // 1. establish circuit, set gateway entry, return false on failure

}