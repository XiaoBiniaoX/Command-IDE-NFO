package arm32x.minecraft.commandblockide.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge play payloads. Handlers run on the main game thread.
 */
public final class Packets {
	public static final String PROTOCOL_VERSION = "1";

	public static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar(PROTOCOL_VERSION).optional();
		registrar.playToServer(ApplyFunctionPacket.TYPE, ApplyFunctionPacket.STREAM_CODEC, ApplyFunctionPacket::handle);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			registrar.playToClient(EditFunctionPacket.TYPE, EditFunctionPacket.STREAM_CODEC, ClientPacketHandler::handleEditFunction);
			registrar.playToClient(UpdateFunctionCommandPacket.TYPE, UpdateFunctionCommandPacket.STREAM_CODEC, ClientPacketHandler::handleUpdateFunctionCommand);
		} else {
			registrar.playToClient(EditFunctionPacket.TYPE, EditFunctionPacket.STREAM_CODEC, (packet, context) -> { });
			registrar.playToClient(UpdateFunctionCommandPacket.TYPE, UpdateFunctionCommandPacket.STREAM_CODEC, (packet, context) -> { });
		}
	}

	private Packets() {
	}
}
