package sk.upjs.kopr;

import sk.upjs.kopr.copy.server.Server;

public class ServerLauncher extends Thread {

	public static void main(String[] args) {
		Server server = new Server();
		server.start();
	}
}
