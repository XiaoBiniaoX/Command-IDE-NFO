package arm32x.minecraft.commandblockide.client.storage;

import arm32x.minecraft.commandblockide.client.CommandBlockIDEClient;
import arm32x.minecraft.commandblockide.client.processor.CommandProcessor;
import arm32x.minecraft.commandblockide.util.AtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the multiline versions of commands.
 *
 * <p>The original 1.20.4 mod used MessagePack for this file. MessagePack is
 * not part of Minecraft's runtime and is not available offline here, so the
 * storage format was ported to JSON, which uses the Gson library that
 * Minecraft already ships with. The logical contents of the file are
 * unchanged.</p>
 */
public final class MultilineCommandStorage {
	private static @Nullable MultilineCommandStorage instance = null;

	public static MultilineCommandStorage getInstance() {
		if (instance == null) {
			instance = new MultilineCommandStorage();
		}
		return instance;
	}

	private MultilineCommandStorage() { }

	private final Map<String, String> commands = new HashMap<>();
	private final Map<CommandBlockLocation, String> blocks = new HashMap<>();
	private final Map<CommandFunctionLocation, String> functions = new HashMap<>();

	public void add(String multiline, String singleLine, boolean isSingleplayer, String world, BlockPos pos) {
		if (!singleLine.isBlank()) {
			String singleLineHash = toHex(hash(singleLine));
			commands.put(singleLineHash, multiline);
			blocks.put(new CommandBlockLocation(isSingleplayer, world, pos), singleLineHash);
		}
	}

	public void add(String multiline, String singleLine, boolean isSingleplayer, String world, ResourceLocation function, int lineIndex) {
		if (!singleLine.isBlank()) {
			String singleLineHash = toHex(hash(singleLine));
			commands.put(singleLineHash, multiline);
			functions.put(new CommandFunctionLocation(isSingleplayer, world, function, lineIndex), singleLineHash);
		}
	}

	public Optional<String> get(byte[] singleLineHash) {
		return Optional.ofNullable(commands.get(toHex(singleLineHash)));
	}

	public Optional<String> get(boolean isSingleplayer, String world, BlockPos pos) {
		var singleLineHash = Optional.ofNullable(blocks.get(new CommandBlockLocation(isSingleplayer, world, pos)));
		return singleLineHash.map(commands::get);
	}

	public Optional<String> get(boolean isSingleplayer, String world, ResourceLocation function, int lineIndex) {
		var singleLineHash = Optional.ofNullable(functions.get(new CommandFunctionLocation(isSingleplayer, world, function, lineIndex)));
		return singleLineHash.map(commands::get);
	}

	public String getRobust(String singleLine, CommandProcessor processor, boolean isSingleplayer, String world, BlockPos pos) {
		return getRobust(singleLine, processor, () -> get(isSingleplayer, world, pos));
	}

	public String getRobust(String singleLine, CommandProcessor processor, boolean isSingleplayer, String world, ResourceLocation function, int lineIndex) {
		return getRobust(singleLine, processor, () -> get(isSingleplayer, world, function, lineIndex));
	}

	private String getRobust(String singleLine, CommandProcessor processor, Supplier<Optional<String>> fallbackSource) {
		byte[] singleLineHash = hash(singleLine);
		var multiline = get(singleLineHash);
		if (multiline.isPresent()) {
			return multiline.get();
		} else {
			multiline = fallbackSource.get();
			if (multiline.isPresent() && processor.processCommand(multiline.get()).getLeft().equals(singleLine)) {
				return multiline.get();
			}
		}
		return singleLine;
	}

	public void remove(boolean isSingleplayer, String world, BlockPos pos) {
		var location = new CommandBlockLocation(isSingleplayer, world, pos);
		blocks.remove(location);
	}

	public void remove(boolean isSingleplayer, String world, ResourceLocation function, int lineIndex) {
		var location = new CommandFunctionLocation(isSingleplayer, world, function, lineIndex);
		functions.remove(location);
	}

	public static boolean save() {
		MultilineCommandStorage instance = getInstance();
		try {
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);

			JsonObject commandsObject = new JsonObject();
			for (var entry : instance.commands.entrySet()) {
				commandsObject.addProperty(entry.getKey(), entry.getValue());
			}
			root.add("commands", commandsObject);

			JsonArray blocksArray = new JsonArray();
			for (var entry : instance.blocks.entrySet()) {
				var location = entry.getKey();
				JsonObject object = new JsonObject();
				object.addProperty("singleplayer", location.isSingleplayer());
				object.addProperty("world", location.world());
				object.addProperty("x", location.pos().getX());
				object.addProperty("y", location.pos().getY());
				object.addProperty("z", location.pos().getZ());
				object.addProperty("hash", entry.getValue());
				blocksArray.add(object);
			}
			root.add("blocks", blocksArray);

			JsonArray functionsArray = new JsonArray();
			for (var entry : instance.functions.entrySet()) {
				var location = entry.getKey();
				JsonObject object = new JsonObject();
				object.addProperty("singleplayer", location.isSingleplayer());
				object.addProperty("world", location.world());
				object.addProperty("function", location.function().toString());
				object.addProperty("line", location.lineIndex());
				object.addProperty("hash", entry.getValue());
				functionsArray.add(object);
			}
			root.add("functions", functionsArray);

			AtomicFiles.write(getFile().toPath(), GSON.toJson(root));
			return true;
		} catch (IOException ex) {
			CommandBlockIDEClient.showErrorScreen(ex, "saving multiline commands");
			return false;
		}
	}

	public static void load() {
		if (!getFile().exists()) {
			return;
		}

		var instance = new MultilineCommandStorage();
		try (Reader reader = new InputStreamReader(new FileInputStream(getFile()), StandardCharsets.UTF_8)) {
			JsonElement rootElement = JsonParser.parseReader(reader);
			if (!rootElement.isJsonObject()) {
				throw new IOException("Unexpected root element in " + getFile());
			}
			JsonObject root = rootElement.getAsJsonObject();

			if (root.has("commands")) {
				JsonObject commandsObject = root.getAsJsonObject("commands");
				for (var entry : commandsObject.entrySet()) {
					instance.commands.put(entry.getKey(), entry.getValue().getAsString());
				}
			}

			if (root.has("blocks")) {
				JsonArray blocksArray = root.getAsJsonArray("blocks");
				for (JsonElement element : blocksArray) {
					JsonObject object = element.getAsJsonObject();
					var location = new CommandBlockLocation(
						object.get("singleplayer").getAsBoolean(),
						object.get("world").getAsString(),
						new BlockPos(
							object.get("x").getAsInt(),
							object.get("y").getAsInt(),
							object.get("z").getAsInt()
						)
					);
					instance.blocks.put(location, object.get("hash").getAsString());
				}
			}

			if (root.has("functions")) {
				JsonArray functionsArray = root.getAsJsonArray("functions");
				for (JsonElement element : functionsArray) {
					JsonObject object = element.getAsJsonObject();
					var location = new CommandFunctionLocation(
						object.get("singleplayer").getAsBoolean(),
						object.get("world").getAsString(),
						ResourceLocation.parse(object.get("function").getAsString()),
						object.get("line").getAsInt()
					);
					instance.functions.put(location, object.get("hash").getAsString());
				}
			}

			MultilineCommandStorage.instance = instance;
		} catch (Exception ex) {
			CommandBlockIDEClient.showErrorScreen(ex, "loading multiline commands");
		}
	}

	private static File getFile() {
		var instance = Minecraft.getInstance();
		return new File(instance.gameDirectory, "commandblockide.json");
	}

	public static byte[] hash(String string) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(string.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			builder.append(Character.forDigit((b >> 4) & 0xF, 16));
			builder.append(Character.forDigit(b & 0xF, 16));
		}
		return builder.toString();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record CommandBlockLocation(boolean isSingleplayer, String world, BlockPos pos) { }
	private record CommandFunctionLocation(boolean isSingleplayer, String world, ResourceLocation function, int lineIndex) { }
}
