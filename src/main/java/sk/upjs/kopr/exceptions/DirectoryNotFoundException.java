package sk.upjs.kopr.exceptions;

import java.io.File;

public class DirectoryNotFoundException extends Throwable {

    private final File directory;

    public DirectoryNotFoundException(File directory) {
        this.directory = directory;
    }

    @Override
    public String toString() {
        return "Directory not found: " + directory.getAbsolutePath();
    }

}
