package arm32x.minecraft.commandblockide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells the client to open the function editor.
 */
public record EditFunctionPacket(ResourceLocation functionId, int lineCount) implements CustomPacketPayload {
	public static final Type<EditFunctionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("commandblockide", "edit_function"));
	public static final StreamCodec<FriendlyByteBuf, EditFunctionPacket> STREAM_CODEC =
		StreamCodec.ofMember(EditFunctionPacket::encode, EditFunctionPacket::decode);

	@Override
	public Type<EditFunctionPacket> type() {
		return TYPE;
	}
	public static void encode(EditFunctionPacket packet, FriendlyByteBuf buf) {
		buf.writeResourceLocation(packet.functionId());
		buf.writeVarInt(packet.lineCount());
	}

	public static EditFunctionPacket decode(FriendlyByteBuf buf) {
		return new EditFunctionPacket(buf.readResourceLocation(), buf.readVarInt());
	}

}
