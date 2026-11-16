package arm32x.minecraft.commandblockide.util;

import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Merges the {@link FriendlyByteBuf}s created by a {@link PacketSplitter} back
 * into one {@code FriendlyByteBuf}.
 */
public final class PacketMerger implements AutoCloseable {
	private FriendlyByteBuf destination;
	private int chunksRemaining = -1;

	public PacketMerger() { }

	/**
	 * Appends the provided {@link FriendlyByteBuf} to the result and returns the
	 * result if it is complete. The {@code PacketMerger} will return to its
	 * original state once the merged packet is complete.
	 * @param buf The {@code FriendlyByteBuf} to append.
	 * @return The merged {@code FriendlyByteBuf} if the appended packet was the
	 *         last, otherwise an empty {@link Optional}.
	 * @throws InvalidSplitPacketException if the header of the first packet is
	 * 		   invalid, or if the first packet is missing a {@link
	 * 		   PacketSplitter} header.
	 */
	public Optional<FriendlyByteBuf> append(FriendlyByteBuf buf) throws InvalidSplitPacketException {
		if (chunksRemaining == -1) {
			if (buf.readableBytes() < 8) {
				throw new InvalidSplitPacketException("Truncated PacketSplitter header.");
			}
			int header = buf.readInt();
			if (header != PacketSplitter.HEADER_MAGIC) {
				throw new InvalidSplitPacketException("Missing PacketSplitter header.");
			}
			int declaredChunks = buf.readInt();
			if (declaredChunks <= 0) {
				throw new InvalidSplitPacketException("Invalid chunk count " + declaredChunks + ".");
			}
			chunksRemaining = declaredChunks;
			destination = new FriendlyByteBuf(Unpooled.buffer());
		}
		destination.writeBytes(buf);
		if (--chunksRemaining == 0) {
			FriendlyByteBuf merged = destination;
			destination = null;
			chunksRemaining = -1;
			return Optional.of(merged);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public void close() {
		if (destination != null) {
			destination.release();
			destination = null;
		}
		chunksRemaining = -1;
	}

	public static class InvalidSplitPacketException extends Exception {
		public InvalidSplitPacketException(String message) {
			super(message);
		}
	}
}
