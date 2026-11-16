package arm32x.minecraft.commandblockide.network;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sends the contents of a function to the server. The payload may be a
 * fragment of a larger message; the fragments are reassembled by
 * {@link arm32x.minecraft.commandblockide.util.PacketMerger}.
 */
public record ApplyFunctionPacket(byte[] payload) {
	public static void encode(ApplyFunctionPacket packet, FriendlyByteBuf buf) {
		buf.writeByteArray(packet.payload());
	}

	public static ApplyFunctionPacket decode(FriendlyByteBuf buf) {
		return new ApplyFunctionPacket(buf.readByteArray());
	}

	public static void handle(ApplyFunctionPacket packet, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		ServerPlayer sender = ctx.getSender();
		if (sender != null) {
			CommandBlockIDE.handleApplyFunction(sender, packet.payload());
		}
		ctx.setPacketHandled(true);
	}
}
