package arm32x.minecraft.commandblockide.client.storage;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import arm32x.minecraft.commandblockide.client.processor.MultilineCommandProcessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class MultilineCommandStorage {
    private static final Map<String, String> COMMANDS = new HashMap<>();
    private static final Map<String, String> LOCATIONS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;
    private MultilineCommandStorage() { }

    public static String worldKey() {
        Minecraft mc = Minecraft.getMinecraft();
        String world = mc.isSingleplayer() ? "local:" + mc.getIntegratedServer().getFolderName()
                : "server:" + (mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : mc.getConnection().getNetworkManager().getRemoteAddress());
        return world + ":dim:" + mc.world.provider.getDimension();
    }

    public static void add(String location, String multiline, String singleLine) {
        String hash = hash(singleLine);
        COMMANDS.put(hash, multiline);
        LOCATIONS.put(worldKey() + ":" + location, hash);
    }

    public static String getRobust(String location, String singleLine) {
        String multiline = COMMANDS.get(hash(singleLine));
        if (multiline == null) multiline = COMMANDS.get(LOCATIONS.get(worldKey() + ":" + location));
        return multiline != null && MultilineCommandProcessor.getInstance().processCommand(multiline).getLeft().equals(singleLine)
                ? multiline : singleLine;
    }

    private static Path file() { return Minecraft.getMinecraft().gameDir.toPath().resolve("commandblockide-1.12.2.json"); }

    public static void load() {
        if (loaded) return;
        loaded = true;
        COMMANDS.clear(); LOCATIONS.clear();
        if (!Files.exists(file())) return;
        try (Reader reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("commands").entrySet()) {
                COMMANDS.put(entry.getKey(), entry.getValue().getAsString());
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("locations").entrySet()) {
                LOCATIONS.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | RuntimeException ex) {
            COMMANDS.clear(); LOCATIONS.clear();
            CommandBlockIDE.LOGGER.error("Cannot load multiline command storage", ex);
        }
    }

    public static boolean save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("commands", GSON.toJsonTree(COMMANDS)); root.add("locations", GSON.toJsonTree(LOCATIONS));
        Path temporary = file().resolveSibling("commandblockide-1.12.2.json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(root, writer); }
            Files.move(temporary, file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) { CommandBlockIDE.LOGGER.error("Cannot save multiline command storage", ex); return false; }
    }

    private static String hash(String string) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(string.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
}
