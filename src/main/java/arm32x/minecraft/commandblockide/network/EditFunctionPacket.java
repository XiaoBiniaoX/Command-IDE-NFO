package arm32x.minecraft.commandblockide.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Tells the client to open the function editor.
 */
public record EditFunctionPacket(ResourceLocation functionId, int lineCount) {
	public static void encode(EditFunctionPacket packet, FriendlyByteBuf buf) {
		buf.writeResourceLocation(packet.functionId());
		buf.writeVarInt(packet.lineCount());
	}

	public static EditFunctionPacket decode(FriendlyByteBuf buf) {
		return new EditFunctionPacket(buf.readResourceLocation(), buf.readVarInt());
	}

	public static void handle(EditFunctionPacket packet, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> ClientPacketHandler.handleEditFunction(packet)));
		ctx.setPacketHandled(true);
	}
}
