package arm32x.minecraft.commandblockide.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Splits a {@link FriendlyByteBuf} into several chunks to avoid size limitations.
 */
public final class PacketSplitter implements Iterable<FriendlyByteBuf> {
	public static final int CHUNK_SIZE = 32500;
	public static final int HEADER_MAGIC = 1397771337 /* SPLI */;

	private final FriendlyByteBuf source;

	public PacketSplitter(FriendlyByteBuf source) {
		this.source = source;
	}

	/**
	 * Writes a {@code PacketSplitter} header to the provided {@link
	 * FriendlyByteBuf}. This should be written to the {@code FriendlyByteBuf}
	 * first, before any other data.
	 * @param buf The {@code FriendlyByteBuf} to write the header to.
	 */
	public static void writeHeader(FriendlyByteBuf buf) {
		buf.writeInt(HEADER_MAGIC);
		buf.writeInt(0);
	}

	/**
	 * Updates the chunk count in the {@code PacketSplitter} header of the
	 * provided {@link FriendlyByteBuf}.
	 * @param buf The {@code FriendlyByteBuf} to update the chunk count of. It
	 *            must contain a header written by {@link
	 *            PacketSplitter#writeHeader(FriendlyByteBuf)}.
	 * @throws MissingHeaderException if {@code buf} does not contain a {@code
	 *         PacketSplitter} header.
	 */
	public static void updateChunkCount(FriendlyByteBuf buf) {
		int start = buf.readerIndex();
		if (buf.getInt(start) != HEADER_MAGIC) {
			throw new MissingHeaderException();
		}
		int chunkCount = (int)Math.ceil(buf.readableBytes() / (double)CHUNK_SIZE);
		buf.setInt(start + 4, chunkCount);
	}

	@Override
	public @NotNull Iterator<FriendlyByteBuf> iterator() {
		if (source.getInt(source.readerIndex()) != HEADER_MAGIC) {
			throw new MissingHeaderException();
		}
		return new BufferIterator();
	}

	private class BufferIterator implements Iterator<FriendlyByteBuf> {
		@Override
		public boolean hasNext() {
			return source.readableBytes() > 0;
		}

		@Override
		public FriendlyByteBuf next() {
			int length = Math.min(CHUNK_SIZE, source.readableBytes());
			if (length <= 0) {
				throw new NoSuchElementException();
			}
			return new FriendlyByteBuf(source.readSlice(length));
		}
	}

	public static class MissingHeaderException extends RuntimeException {
		@Override
		public String getMessage() {
			return "Missing PacketSplitter header.";
		}
	}
}
