package sk.upjs.kopr.copy.client;

public class ClientLauncher extends Thread {
	
	@Override
	public void start() {
		Client client = new Client();
		try {
			client.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
