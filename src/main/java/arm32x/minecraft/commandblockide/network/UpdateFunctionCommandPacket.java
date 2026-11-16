package arm32x.minecraft.commandblockide.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sends a single line of a function to the client.
 */
public record UpdateFunctionCommandPacket(int index, String line) {
	public static void encode(UpdateFunctionCommandPacket packet, FriendlyByteBuf buf) {
		buf.writeVarInt(packet.index());
		buf.writeUtf(packet.line());
	}

	public static UpdateFunctionCommandPacket decode(FriendlyByteBuf buf) {
		return new UpdateFunctionCommandPacket(buf.readVarInt(), buf.readUtf());
	}

	public static void handle(UpdateFunctionCommandPacket packet, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> ClientPacketHandler.handleUpdateFunctionCommand(packet)));
		ctx.setPacketHandled(true);
	}
}
