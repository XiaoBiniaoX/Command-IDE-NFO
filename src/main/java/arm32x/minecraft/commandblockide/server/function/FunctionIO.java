package arm32x.minecraft.commandblockide.server.function;

import arm32x.minecraft.commandblockide.mixin.server.FunctionManagerAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import arm32x.minecraft.commandblockide.util.ContentHash;
import java.util.List;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

public final class FunctionIO {
    private FunctionIO() { }

    public static Path path(MinecraftServer server, ResourceLocation id) throws IOException {
        // ResourceLocation in 1.12.2 does not enforce safe filesystem segments.
        if (!id.getNamespace().matches("[a-z0-9_-]+") || !id.getPath().matches("[a-z0-9_./-]+")) {
            throw new IOException("Invalid function name: " + id);
        }
        Path root = ((FunctionManagerAccessor) server.getFunctionManager()).ide$getFunctionDir().toPath().toRealPath();
        Path file = root.resolve(id.getNamespace()).resolve(id.getPath() + ".mcfunction").normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(root)) {
            throw new IOException("Function file not found or outside functions directory: " + id);
        }
        return file;
    }

    public static List<String> loadFunction(MinecraftServer server, ResourceLocation id) throws IOException {
        if (server.getFunctionManager().getFunction(id) == null) throw new IOException("No such function: " + id);
        return Files.readAllLines(path(server, id), StandardCharsets.UTF_8);
    }

    public static void saveFunction(MinecraftServer server, ResourceLocation id, String revision, List<String> lines) throws IOException {
        FunctionManager manager = server.getFunctionManager();
        if (manager.getFunction(id) == null) throw new IOException("No such function: " + id);
        Path file = path(server, id);
        if (!ContentHash.of(String.join("\n", Files.readAllLines(file, StandardCharsets.UTF_8))).equals(revision)) {
            throw new IOException("Function changed since opening. Reopen it before saving; your edits have been kept.");
        }
        // Validate before touching the file. Vanilla checks command names, not full argument syntax.
        FunctionObject.create(manager, lines);
        Path temporary = Files.createTempFile(file.getParent(), "commandblockide-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        manager.reload();
    }
}
