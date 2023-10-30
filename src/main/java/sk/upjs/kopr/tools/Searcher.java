package sk.upjs.kopr.tools;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;

public class Searcher {
	private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

	public static List<File> search(File file) {
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		FileSearcherTask initialTask = new FileSearcherTask(file, executor);
		Future<List<File>> futureResult = executor.submit(initialTask);

		try {
			return futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		} finally {
			executor.shutdown();
		}
	}
}
