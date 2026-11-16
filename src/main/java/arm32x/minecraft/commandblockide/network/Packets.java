package arm32x.minecraft.commandblockide.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel.
 *
 * <p>The original mod used Fabric's networking API, which registers a
 * receiver per packet identifier. Forge instead uses a single
 * {@link SimpleChannel} with a stable message discriminator per packet
 * type.</p>
 */
public final class Packets {
	public static final String PROTOCOL_VERSION = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation("commandblockide", "main"),
		() -> PROTOCOL_VERSION,
		PROTOCOL_VERSION::equals,
		PROTOCOL_VERSION::equals
	);

	public static void register() {
		int id = 0;
		// Client to Server
		CHANNEL.registerMessage(id++, ApplyFunctionPacket.class,
			ApplyFunctionPacket::encode, ApplyFunctionPacket::decode, ApplyFunctionPacket::handle);
		// Server to Client
		CHANNEL.registerMessage(id++, EditFunctionPacket.class,
			EditFunctionPacket::encode, EditFunctionPacket::decode, EditFunctionPacket::handle);
		CHANNEL.registerMessage(id++, UpdateFunctionCommandPacket.class,
			UpdateFunctionCommandPacket::encode, UpdateFunctionCommandPacket::decode, UpdateFunctionCommandPacket::handle);
	}

	private Packets() {
	}
}
