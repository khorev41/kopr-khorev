package sk.upjs.kopr.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesManager {

	private static PropertiesManager instance = null;
	private Properties properties = null;

	private PropertiesManager() {
		properties = new Properties();
		try {
			InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("config.properties");
			properties.load(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static synchronized PropertiesManager getInstance() {
		if (instance == null) {
			instance = new PropertiesManager();
		}
		return instance;
	}

	public String getIP() {
		return this.properties.getProperty("ip");
	}

	public int getPort() {
		return Integer.parseInt(this.properties.getProperty("port"));
	}
	
	public int getNumberOfSockets() {
		return Integer.parseInt(this.properties.getProperty("numberOfSockets"));
	}

	public void setNumberOfSockets(int value) {
		this.properties.setProperty("numberOfSockets", value + "");
	}
	
	public String getDirectory() {
		return this.properties.getProperty("directory");
	}

	public void setDirectory(String value) {
		this.properties.setProperty("directory", value);
	}
	
	public String getPathToSave() {
		return this.properties.getProperty("pathToSave");
	}

	public void setPathToSave(String value) {
		this.properties.setProperty("pathToSave", value);
	}
	
	
}