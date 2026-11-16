package arm32x.minecraft.commandblockide.util;

import java.io.ByteArrayOutputStream;

public final class PacketMerger {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private int request, size;
    public byte[] append(int request, int offset, int total, byte[] chunk) {
        if (offset == 0) { bytes.reset(); size = total; this.request = request; }
        if (this.request != request || total != size || offset != bytes.size() || offset + chunk.length > size) {
            bytes.reset();
            throw new IllegalArgumentException("Invalid function chunk sequence");
        }
        bytes.write(chunk, 0, chunk.length);
        if (bytes.size() != size) return null;
        byte[] result = bytes.toByteArray();
        bytes.reset();
        return result;
    }
}
