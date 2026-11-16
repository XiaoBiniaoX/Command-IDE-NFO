package arm32x.minecraft.commandblockide.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes the replacement before touching the existing file. */
public final class AtomicFiles {
	public static void write(Path path, String text) throws IOException {
		Path target = path.toAbsolutePath();
		Path temporary = Files.createTempFile(target.getParent(), ".commandblockide-", ".tmp");
		try {
			Files.writeString(temporary, text);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private AtomicFiles() { }
}
