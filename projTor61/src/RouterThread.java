package src;

import java.util.HashMap;
import java.util.Map;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.net.ServerSocket;
import java.net.Socket;

public class RouterThread extends Thread {
	private RouterInfo routerInfo;
	private ServerSocket serverSocket;
	private static final int TIMEOUT = 10000;

    public RouterThread(RouterInfo routerInfo) {
    	this.routerInfo = routerInfo;
    }

    @Override
	public void run() {
		ServerSocket serverSocket; // A socket that accepts requests and connections.
	    try {
	      serverSocket = new ServerSocket(routerInfo.getPort());
	    } catch (Exception e) {
	      System.out.println("unable to bind to proxy port");
	      return;
	    }
	    while (true) {
       		try {
        		(new RouterSocketReader(serverSocket.accept(), routerInfo)).start();
      	} catch (IOException e) {
        	continue;
      	}
	}
}