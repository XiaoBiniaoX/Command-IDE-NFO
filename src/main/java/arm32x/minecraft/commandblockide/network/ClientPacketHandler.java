package arm32x.minecraft.commandblockide.network;

import arm32x.minecraft.commandblockide.client.gui.screen.CommandFunctionIDEScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client-side half of the network layer.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
	public static void handleEditFunction(EditFunctionPacket packet) {
		Minecraft.getInstance().execute(() -> {
			Minecraft.getInstance().setScreen(new CommandFunctionIDEScreen(packet.functionId(), packet.lineCount()));
		});
	}

	public static void handleUpdateFunctionCommand(UpdateFunctionCommandPacket packet) {
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().screen instanceof CommandFunctionIDEScreen) {
				((CommandFunctionIDEScreen)Minecraft.getInstance().screen).update(packet.index(), packet.line());
			}
		});
	}

	private ClientPacketHandler() {
	}
}
