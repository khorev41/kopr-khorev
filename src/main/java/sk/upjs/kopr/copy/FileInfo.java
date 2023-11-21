package sk.upjs.kopr.copy;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class FileInfo implements Serializable {

	@Serial
	private static final long serialVersionUID = -1361912600329298754L;

	public final String fileName;
	public final long offset;
	public final long size;

	public FileInfo(String fileName, long offset,long size) {
		this.fileName = fileName;
		this.offset = offset;
		this.size = size;
	}

	@Override
	public String toString() {
		return fileName + ": "+offset ;
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileName, offset, size);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileInfo other = (FileInfo) obj;
		return Objects.equals(fileName, other.fileName) && size == other.size;
	}
	
	

	
	
}
