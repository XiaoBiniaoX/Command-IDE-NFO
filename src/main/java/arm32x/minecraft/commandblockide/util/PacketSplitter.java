package arm32x.minecraft.commandblockide.util;

import java.util.Arrays;
import java.util.function.BiConsumer;

public final class PacketSplitter {
    public static final int CHUNK_SIZE = 30000;
    public static final int MAX_SIZE = 16 * 1024 * 1024;
    private PacketSplitter() { }
    public static void split(byte[] bytes, BiConsumer<Integer, byte[]> send) {
        if (bytes.length > MAX_SIZE) throw new IllegalArgumentException("Function exceeds 16 MiB");
        for (int start = 0; start < bytes.length; start += CHUNK_SIZE) {
            send.accept(start, Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + CHUNK_SIZE)));
        }
    }
}
