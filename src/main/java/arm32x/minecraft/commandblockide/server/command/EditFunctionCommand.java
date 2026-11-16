package arm32x.minecraft.commandblockide.server.command;

import arm32x.minecraft.commandblockide.network.EditFunctionPacket;
import arm32x.minecraft.commandblockide.network.UpdateFunctionCommandPacket;
import arm32x.minecraft.commandblockide.server.function.FunctionIO;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EditFunctionCommand {
	/**
	 * Like the suggestion provider of the vanilla {@code function} command, but
	 * only returns functions and not tags.
	 */
	public static final SuggestionProvider<CommandSourceStack> SUGGESTION_PROVIDER = (ctx, builder) -> {
		ServerFunctionManager functionManager = ctx.getSource().getServer().getFunctions();
		return SharedSuggestionProvider.suggestResource(functionManager.getFunctionNames(), builder);
	};

	private static final SimpleCommandExceptionType MOD_NOT_INSTALLED_EXCEPTION =
		new SimpleCommandExceptionType(Component.translatable("commands.editfunction.failed.modNotInstalled"));
	private static final DynamicCommandExceptionType FUNCTION_NOT_FOUND_EXCEPTION =
		new DynamicCommandExceptionType(text -> Component.translatable("commands.editfunction.failed.functionNotFound", text));
	// loadFunction returns a Component on failure, so we use that.
	private static final DynamicCommandExceptionType FUNCTION_LOAD_FAILED_EXCEPTION =
		new DynamicCommandExceptionType(text -> (Message)text);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("editfunction")
			.requires(source -> source.hasPermission(2))
			.then(Commands.argument("name", ResourceLocationArgument.id())
				.suggests(SUGGESTION_PROVIDER)
				.executes(ctx -> execute(ctx.getSource(), ResourceLocationArgument.getId(ctx, "name")))
			)
		);
	}

	private static int execute(CommandSourceStack source, ResourceLocation functionId) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayer();
		if (player == null || !player.connection.hasChannel(EditFunctionPacket.TYPE)
			|| !player.connection.hasChannel(UpdateFunctionCommandPacket.TYPE)) {
			throw MOD_NOT_INSTALLED_EXCEPTION.create();
		}

		if (source.getServer().getFunctions().get(functionId).isEmpty()) {
			throw FUNCTION_NOT_FOUND_EXCEPTION.create(functionId);
		}

		Either<List<String>, Component> loadResult = FunctionIO.loadFunction(source.getServer(), functionId);
		if (loadResult.right().isPresent()) {
			throw FUNCTION_LOAD_FAILED_EXCEPTION.create(loadResult.right().get());
		}
		var lines = loadResult.left().orElseThrow(); // Should be present
		if (lines.stream().anyMatch(line -> line.length() > UpdateFunctionCommandPacket.MAX_LINE_LENGTH)) {
			throw FUNCTION_LOAD_FAILED_EXCEPTION.create(Component.literal("Command Block IDE: function contains a line longer than 32767 characters."));
		}

		PacketDistributor.sendToPlayer(player, new EditFunctionPacket(functionId, lines.size()));

		for (int index = 0; index < lines.size(); index++) {
			PacketDistributor.sendToPlayer(player,
				new UpdateFunctionCommandPacket(index, lines.get(index)));
		}

		return 1;
	}
}
