package arm32x.minecraft.commandblockide.server.function;

import arm32x.minecraft.commandblockide.mixin.server.PathPackResourcesAccessor;
import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes {@code .mcfunction} files inside datapacks.
 *
 * <p>Minecraft 1.20.1 does not expose the file system path of a datapack
 * resource, so the selected packs are searched for the one that contains the
 * function.</p>
 */
public final class FunctionIO {
	private static final FileToIdConverter FUNCTION_LISTER = new FileToIdConverter("functions", ".mcfunction");

	/**
	 * Loads a function from the filesystem.
	 *
	 * @param server The Minecraft server the function is loaded in.
	 * @param functionId The namespaced ID of the function to load.
	 * @return Either the lines read from the {@code .mcfunction} file, or a
	 *         feedback message to show to the user indicating what went wrong.
	 */
	public static Either<List<String>, Component> loadFunction(MinecraftServer server, ResourceLocation functionId) {
		// Convert the function ID ('some_datapack:some_function') to a resource
		// path ('some_datapack:functions/some_function.mcfunction').
		var functionResourcePath = FUNCTION_LISTER.idToFile(functionId);

		// Make sure the function actually exists in a datapack.
		if (server.getResourceManager().getResource(functionResourcePath).isEmpty()) {
			return Either.right(Component.translatable("commandBlockIDE.loadFunction.failed.noResourcePack", functionId));
		}

		Path path;
		try {
			path = findFunctionFile(server, functionResourcePath);
		} catch (UnsupportedPackException e) {
			return Either.right(Component.translatable("commandBlockIDE.loadFunction.failed.packClassNotSupported", functionId, e.getPackClassName()).withStyle(ChatFormatting.RED));
		}
		if (path == null) {
			return Either.right(Component.translatable("commandBlockIDE.loadFunction.failed.zipNotSupported", functionId).withStyle(ChatFormatting.RED));
		}

		// Read the content of the mcfunction file.
		try {
			return Either.left(Files.readAllLines(path));
		} catch (IOException e) {
			LOGGER.error("IO exception occurred while loading function '" + functionId + "':", e);
			return Either.right(Component.translatable("commandBlockIDE.loadFunction.failed.ioException", functionId).withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Saves a function to the filesystem.
	 *
	 * @param server The Minecraft server the function is loaded in.
	 * @param functionId The namespaced ID of the function to save.
	 * @param lines The lines to write into the {@code .mcfunction} file.
	 * @return The feedback message to show to the user.
	 */
	public static Component saveFunction(MinecraftServer server, ResourceLocation functionId, List<String> lines) {
		var functionResourcePath = FUNCTION_LISTER.idToFile(functionId);

		if (server.getResourceManager().getResource(functionResourcePath).isEmpty()) {
			// Error saving function '...': Not found in any datapack.
			return Component.translatable("commandBlockIDE.saveFunction.failed.noResourcePack", functionId);
		}

		Path path;
		try {
			path = findFunctionFile(server, functionResourcePath);
		} catch (UnsupportedPackException e) {
			return Component.translatable("commandBlockIDE.saveFunction.failed.packClassNotSupported", functionId, e.getPackClassName()).withStyle(ChatFormatting.RED);
		}
		if (path == null) {
			return Component.translatable("commandBlockIDE.saveFunction.failed.zipNotSupported", functionId).withStyle(ChatFormatting.RED);
		}

		// Replace the content of the mcfunction file.
		try {
			Files.write(path, lines, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("IO exception occurred while saving function '" + functionId + "':", e);
			return Component.translatable("commandBlockIDE.saveFunction.failed.ioException", functionId).withStyle(ChatFormatting.RED);
		}

		return Component.translatable("commandBlockIDE.saveFunction.success.file", functionId);
	}

	/**
	 * Finds the file system path of a function inside one of the server's
	 * selected datapacks.
	 *
	 * @param server The server whose packs are searched.
	 * @param functionResourcePath The path of the function resource.
	 * @return The path of the function file, or {@code null} if the function
	 *         lives in a pack that cannot be written to (such as a zip file).
	 * @throws UnsupportedPackException if the pack is of an unsupported class.
	 */
	private static Path findFunctionFile(MinecraftServer server, ResourceLocation functionResourcePath) {
		for (Pack pack : server.getPackRepository().getSelectedPacks()) {
			PackResources resources;
			try {
				resources = pack.open();
			} catch (Exception e) {
				LOGGER.warn("Could not open pack '{}':", pack.getId(), e);
				continue;
			}
			if (resources instanceof PathPackResources pathPack) {
				Path root = ((PathPackResourcesAccessor)pathPack).getRoot();
				Path path = root
					.resolve(PackType.SERVER_DATA.getDirectory())
					.resolve(functionResourcePath.getNamespace())
					.resolve(functionResourcePath.getPath());
				if (Files.isRegularFile(path)) {
					return path;
				}
			} else if (!(resources instanceof net.minecraft.server.packs.FilePackResources)) {
				throw new UnsupportedPackException(resources.getClass().getSimpleName());
			}
		}
		return null;
	}

	/**
	 * Thrown when a datapack uses a resource pack class that the mod does not
	 * know how to handle.
	 */
	public static class UnsupportedPackException extends RuntimeException {
		private final String packClassName;

		public UnsupportedPackException(String packClassName) {
			super("Unsupported resource pack class: " + packClassName);
			this.packClassName = packClassName;
		}

		public String getPackClassName() {
			return packClassName;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(FunctionIO.class);
}
