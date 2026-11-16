package arm32x.minecraft.commandblockide.network;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import arm32x.minecraft.commandblockide.util.PacketSplitter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sends the contents of a function to the server. The payload may be a
 * fragment of a larger message; the fragments are reassembled by
 * {@link arm32x.minecraft.commandblockide.util.PacketMerger}.
 */
public record ApplyFunctionPacket(byte[] payload) implements CustomPacketPayload {
	public static final Type<ApplyFunctionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("commandblockide", "apply_function"));
	public static final StreamCodec<FriendlyByteBuf, ApplyFunctionPacket> STREAM_CODEC =
		StreamCodec.ofMember(ApplyFunctionPacket::encode, ApplyFunctionPacket::decode);

	@Override
	public Type<ApplyFunctionPacket> type() {
		return TYPE;
	}
	public static void encode(ApplyFunctionPacket packet, FriendlyByteBuf buf) {
		buf.writeByteArray(packet.payload());
	}

	public static ApplyFunctionPacket decode(FriendlyByteBuf buf) {
		return new ApplyFunctionPacket(buf.readByteArray(PacketSplitter.CHUNK_SIZE));
	}

	public static void handle(ApplyFunctionPacket packet, IPayloadContext context) {
		ServerPlayer sender = (ServerPlayer)context.player();
		if (!sender.createCommandSourceStack().hasPermission(2)) return;
		CommandBlockIDE.handleApplyFunction(sender, packet.payload());
	}
}
