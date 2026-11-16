package arm32x.minecraft.commandblockide;

import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.server.command.EditFunctionCommand;
import arm32x.minecraft.commandblockide.server.function.FunctionIO;
import arm32x.minecraft.commandblockide.util.PacketMerger;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command Block IDE for Minecraft 1.20.1 (Forge).
 *
 * <p>Replaces the command block GUI to allow editing multiple command blocks
 * at once, and adds an editor for functions in datapacks.</p>
 */
@Mod(CommandBlockIDE.MOD_ID)
public final class CommandBlockIDE {
	public static final String MOD_ID = "commandblockide";

	private static final Logger LOGGER = LoggerFactory.getLogger(CommandBlockIDE.class);

	/**
	 * Reassembles split function payloads, one merger per player.
	 */
	private static final Map<UUID, PacketMerger> FUNCTION_MERGERS = new HashMap<>();

	public CommandBlockIDE() {
		Packets.register();

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		LOGGER.info("Command Block IDE loaded.");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		EditFunctionCommand.register(event.getDispatcher());
	}

	/**
	 * Handles a (possibly fragmented) function payload sent by a client.
	 */
	public static void handleApplyFunction(ServerPlayer player, byte[] payload) {
		PacketMerger merger = FUNCTION_MERGERS.computeIfAbsent(player.getUUID(), uuid -> new PacketMerger());
		Optional<FriendlyByteBuf> maybeMerged;
		try {
			maybeMerged = merger.append(new FriendlyByteBuf(Unpooled.wrappedBuffer(payload)));
		} catch (PacketMerger.InvalidSplitPacketException e) {
			LOGGER.error("Invalid split packet received from {}:", player.getGameProfile().getName(), e);
			return;
		}
		if (maybeMerged.isPresent()) {
			FriendlyByteBuf merged = maybeMerged.get();
			ResourceLocation functionId = merged.readResourceLocation();
			int lineCount = merged.readVarInt();
			String[] lines = new String[lineCount];
			for (int index = 0; index < lineCount; index++) {
				lines[index] = merged.readUtf(Integer.MAX_VALUE >> 2);
			}

			player.server.execute(() -> {
				Component feedbackMessage = FunctionIO.saveFunction(player.server, functionId, Arrays.asList(lines));
				player.sendSystemMessage(feedbackMessage);
			});
		}
	}
}
