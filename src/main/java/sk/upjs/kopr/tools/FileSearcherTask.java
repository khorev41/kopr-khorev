package sk.upjs.kopr.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class FileSearcherTask implements Callable<List<File>> {
	private final File currentDir;
	private final ExecutorService executor;

	public FileSearcherTask(final File fileToSend, ExecutorService executor) {
		if (fileToSend == null || !fileToSend.exists()) {
			throw new IllegalArgumentException();
		}
		this.currentDir = fileToSend;
		this.executor = executor;
	}

	@Override
	public List<File> call() {
		File[] files = currentDir.listFiles();
		if (files == null) {
			System.out.println("Directory " + currentDir + " is not accessible");
			return new ArrayList<File>();
		}

		List<File> filesToReturn = new ArrayList<>();
		List<Future<List<File>>> futures = new LinkedList<>();

		for (File currentFile : files) {
			if (currentFile.isFile()) {
				filesToReturn.add(currentFile);
			}
			if (currentFile.isDirectory()) {
				FileSearcherTask task = new FileSearcherTask(currentFile, executor);
				futures.add(executor.submit(task));
			}
		}

		for (Future<List<File>> future : futures) {
			try {
				filesToReturn.addAll(future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		return filesToReturn;
	}
}
