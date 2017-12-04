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
      



	  // 2. create strema 	

	}

}