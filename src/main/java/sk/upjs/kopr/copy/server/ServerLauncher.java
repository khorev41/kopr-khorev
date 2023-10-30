package sk.upjs.kopr.copy.server;

public class ServerLauncher extends Thread {

	@Override
	public void start() {
		Server server = new Server();
		server.start();
	}
}
