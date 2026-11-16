package arm32x.minecraft.commandblockide.network;

import arm32x.minecraft.commandblockide.client.gui.screen.CommandFunctionIDEScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client-side half of the network layer.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
	public static void handleEditFunction(EditFunctionPacket packet, IPayloadContext context) {
		Minecraft.getInstance().setScreen(new CommandFunctionIDEScreen(packet.functionId(), packet.lineCount()));
	}

	public static void handleUpdateFunctionCommand(UpdateFunctionCommandPacket packet, IPayloadContext context) {
		if (Minecraft.getInstance().screen instanceof CommandFunctionIDEScreen screen) {
			screen.update(packet.index(), packet.line());
		}
	}

	private ClientPacketHandler() {
	}
}
