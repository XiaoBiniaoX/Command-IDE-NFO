package arm32x.minecraft.commandblockide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sends a single line of a function to the client.
 */
public record UpdateFunctionCommandPacket(int index, String line) implements CustomPacketPayload {
	public static final int MAX_LINE_LENGTH = 32767;
	public static final Type<UpdateFunctionCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("commandblockide", "update_function_command"));
	public static final StreamCodec<FriendlyByteBuf, UpdateFunctionCommandPacket> STREAM_CODEC =
		StreamCodec.ofMember(UpdateFunctionCommandPacket::encode, UpdateFunctionCommandPacket::decode);

	@Override
	public Type<UpdateFunctionCommandPacket> type() {
		return TYPE;
	}
	public static void encode(UpdateFunctionCommandPacket packet, FriendlyByteBuf buf) {
		buf.writeVarInt(packet.index());
		buf.writeUtf(packet.line(), MAX_LINE_LENGTH);
	}

	public static UpdateFunctionCommandPacket decode(FriendlyByteBuf buf) {
		return new UpdateFunctionCommandPacket(buf.readVarInt(), buf.readUtf(MAX_LINE_LENGTH));
	}

}
