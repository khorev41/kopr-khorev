package sk.upjs.kopr.tools;


public class FilePathChanger {
	
	public static String dirRoot = PropertiesManager.getInstance().getDirectory();
	public static String pathToSave = PropertiesManager.getInstance().getPathToSave();

	public static String getLastDirectoryName(String path) {
		String[] parts = path.split("\\\\");
		return parts[parts.length - 1];
	}

	public static String modifyBasePath(String original) {
		return original.replace(dirRoot, pathToSave + "\\"+getLastDirectoryName(dirRoot) + "_copy");
	}
}
