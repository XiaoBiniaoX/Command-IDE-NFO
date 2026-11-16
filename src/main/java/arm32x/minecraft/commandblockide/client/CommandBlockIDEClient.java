package arm32x.minecraft.commandblockide.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entry points of the mod.
 *
 * <p>Forge has no equivalent of Fabric's {@code ClientModInitializer}; the
 * client-specific behaviour is wired up from the common mod class and the
 * network layer instead.</p>
 */
public final class CommandBlockIDEClient {
	public static void showErrorScreen(Exception ex, @Nullable String currentAction) {
		if (currentAction != null) {
			LOGGER.error("Error screen shown while " + currentAction + ":", ex);
		} else {
			LOGGER.error("Error screen shown:", ex);
		}
		Minecraft client = Minecraft.getInstance();
		var previousScreen = client.screen;
		client.setScreen(new AlertScreen(
			() -> client.setScreen(previousScreen),
			currentAction != null
				? Component.translatable("commandBlockIDE.errorWithContext", currentAction)
				: Component.translatable("commandBlockIDE.error"),
			Component.literal(ex.toString())
		));
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(CommandBlockIDEClient.class);
}
