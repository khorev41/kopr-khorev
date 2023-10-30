package sk.upjs.kopr.copy.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

public class MyFileWriter {
	private static Map<File, MyFileWriter> instances = new HashMap<>();
	
	private RandomAccessFile raf;
	
	private MyFileWriter(File file, long fileSize) throws IOException {
		raf = new RandomAccessFile(file,"rw");
		raf.setLength(fileSize);
	}
	
	public synchronized static MyFileWriter getInstance(File file, long fileSize) throws IOException {
		MyFileWriter instance = instances.get(file);
		if (instance == null) {
			instance = new MyFileWriter(file,fileSize);
			instances.put(file, instance);
		}
		return instance;
	}
	
	public synchronized void write(long fileOffset, byte[] data, int dataOffset, int dataLength) throws IOException {
		raf.seek(fileOffset);
		raf.write(data, dataOffset, dataLength);
		
	}
	
	public void close() {
		try {
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
