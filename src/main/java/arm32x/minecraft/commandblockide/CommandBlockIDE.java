package arm32x.minecraft.commandblockide;

import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.server.command.EditFunctionCommand;
import arm32x.minecraft.commandblockide.server.function.FunctionIO;
import arm32x.minecraft.commandblockide.util.PacketMerger;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command Block IDE for Minecraft 1.21.1 (NeoForge).
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

	public CommandBlockIDE(IEventBus modEventBus) {
		modEventBus.addListener(Packets::register);
		modEventBus.addListener(this::commonSetup);
		NeoForge.EVENT_BUS.register(this);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		LOGGER.info("Command Block IDE loaded.");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		EditFunctionCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		PacketMerger merger = FUNCTION_MERGERS.remove(event.getEntity().getUUID());
		if (merger != null) merger.close();
	}

	@SubscribeEvent
	public void onServerStopped(ServerStoppedEvent event) {
		FUNCTION_MERGERS.values().forEach(PacketMerger::close);
		FUNCTION_MERGERS.clear();
	}

	/**
	 * Handles a (possibly fragmented) function payload sent by a client.
	 */
	public static void handleApplyFunction(ServerPlayer player, byte[] payload) {
		PacketMerger merger = FUNCTION_MERGERS.computeIfAbsent(player.getUUID(), uuid -> new PacketMerger());
		FriendlyByteBuf fragment = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
		Optional<FriendlyByteBuf> maybeMerged;
		try {
			maybeMerged = merger.append(fragment);
		} catch (PacketMerger.InvalidSplitPacketException | RuntimeException e) {
			FUNCTION_MERGERS.remove(player.getUUID());
			merger.close();
			LOGGER.error("Invalid split packet received from {}:", player.getGameProfile().getName(), e);
			player.sendSystemMessage(Component.literal("Command Block IDE: invalid function save data."));
			return;
		} finally {
			fragment.release();
		}
		if (maybeMerged.isPresent()) {
			FUNCTION_MERGERS.remove(player.getUUID());
			FriendlyByteBuf merged = maybeMerged.get();
			ResourceLocation functionId;
			List<String> lines;
			try {
				functionId = merged.readResourceLocation();
				int lineCount = merged.readVarInt();
				if (lineCount < 0 || lineCount > merged.readableBytes()) {
					throw new IllegalArgumentException("Invalid function line count.");
				}
				String[] decoded = new String[lineCount];
				for (int index = 0; index < lineCount; index++) {
					decoded[index] = merged.readUtf(SharedConstants.MAX_FUNCTION_COMMAND_LENGTH);
				}
				if (merged.isReadable()) throw new IllegalArgumentException("Trailing function save data.");
				lines = Arrays.asList(decoded);
			} catch (RuntimeException e) {
				LOGGER.error("Invalid function received from {}:", player.getGameProfile().getName(), e);
				player.sendSystemMessage(Component.literal("Command Block IDE: invalid function save data."));
				return;
			} finally {
				merged.release();
			}
			// NeoForge registers this handler on the server main thread.
			player.sendSystemMessage(FunctionIO.saveFunction(player.server, functionId, lines));
		}
	}
}
