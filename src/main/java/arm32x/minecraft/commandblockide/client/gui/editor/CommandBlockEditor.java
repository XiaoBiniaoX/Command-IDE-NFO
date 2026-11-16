package arm32x.minecraft.commandblockide.client.gui.editor;

import arm32x.minecraft.commandblockide.client.Dirtyable;
import arm32x.minecraft.commandblockide.client.gui.button.CommandBlockAutoButton;
import arm32x.minecraft.commandblockide.client.gui.button.CommandBlockTrackOutputButton;
import arm32x.minecraft.commandblockide.client.gui.button.CommandBlockTypeButton;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import arm32x.minecraft.commandblockide.client.update.DataCommandUpdateRequester;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

public final class CommandBlockEditor extends CommandEditor {
	private final CommandBlockEntity blockEntity;

	private final EditBox lastOutputField;

	private final CommandBlockTypeButton typeButton;
	private final CommandBlockAutoButton autoButton;
	private final CommandBlockTrackOutputButton trackOutputButton;

	private boolean commandFieldDirty = false;

	public CommandBlockEditor(Screen screen, Font font, int x, int y, int width, int height, CommandBlockEntity blockEntity, int index) {
		super(screen, font, x, y, width, height, 40, 20, index);
		this.blockEntity = blockEntity;

		commandField.setMaxLength(32500);

		lastOutputField = new EditBox(
			font,
			commandField.getX(), commandField.getY(),
			commandField.getWidth(), commandField.getHeight(),
			Component.translatable("advMode.previousOutput")
				.append(Component.translatable("commandBlockIDE.narrator.editorIndex", index + 1))
		);
		lastOutputField.setEditable(false);
		lastOutputField.setMaxLength(32500);
		lastOutputField.setValue(Component.translatable("commandBlockIDE.unloaded").getString());
		lastOutputField.visible = false;

		typeButton = addDrawableChild(new CommandBlockTypeButton(x + 20, y));
		typeButton.setBlockType(blockEntity.getMode());
		typeButton.active = false;

		autoButton = addDrawableChild(new CommandBlockAutoButton(x + 40, y));
		autoButton.setAuto(typeButton.getBlockType() == CommandBlockEntity.Mode.SEQUENCE);
		autoButton.active = false;

		trackOutputButton = addDrawableChild(new CommandBlockTrackOutputButton(x + width - 16, y));
		trackOutputButton.setTrackingOutput(true);
		trackOutputButton.active = false;
	}

	public void save(ClientPacketListener networkHandler) {
		BaseCommandBlock executor = blockEntity.getCommandBlock();
		networkHandler.send(new ServerboundSetCommandBlockPacket(
			BlockPos.containing(executor.getPosition()),
			getSingleLineCommand(),
			typeButton.getBlockType(),
			trackOutputButton.isTrackingOutput(),
			typeButton.isConditional(),
			autoButton.isAuto()
		));
		executor.setTrackOutput(trackOutputButton.isTrackingOutput());
		if (!trackOutputButton.isTrackingOutput()) {
			executor.setLastOutput(null);
		}
	}

	public void saveMultilineCommand() {
		Minecraft client = Minecraft.getInstance();
		String world = getWorldName(client);

		MultilineCommandStorage.getInstance().add(commandField.getText(), getSingleLineCommand(), client.isSingleplayer(), world, blockEntity.getBlockPos());
	}

	public void update() {
		// A delayed refresh (or another player's update) must not erase local edits.
		if (isLoaded() && isDirty()) return;
		BaseCommandBlock executor = blockEntity.getCommandBlock();
		Minecraft client = Minecraft.getInstance();
		commandField.setText(MultilineCommandStorage.getInstance().getRobust(
			executor.getCommand(),
			processor,
			client.isSingleplayer(),
			getWorldName(client),
			BlockPos.containing(executor.getPosition())
		));
		typeButton.setBlockType(blockEntity.getMode());
		typeButton.setConditional(blockEntity.isConditional());
		autoButton.setAuto(blockEntity.isAutomatic());
		trackOutputButton.setTrackingOutput(executor.isTrackOutput());

		String lastOutput = executor.getLastOutput().getString();
		if (lastOutput.equals("")) {
			lastOutput = Component.translatable("commandBlockIDE.lastOutput.none").getString();
		}
		lastOutputField.setValue(lastOutput);

		suggestor.setWindowActive(commandField.isFocused());
		suggestor.updateCommandInfo();

		commandFieldDirty = false;
		setLoaded(true);
	}

	public void requestUpdate(LocalPlayer player) {
		DataCommandUpdateRequester.getInstance().requestUpdate(player, blockEntity);
	}

	@Override
	public void commandChanged(String newCommand) {
		if (!newCommand.equals(blockEntity.getCommandBlock().getCommand())) {
			commandFieldDirty = true;
		}
		super.commandChanged(newCommand);
	}

	@Override
	protected void renderCommandField(GuiGraphics context, int mouseX, int mouseY, float delta) {
		if (trackOutputButton.isMouseOver(mouseX, mouseY)) {
			commandField.visible = false;
			lastOutputField.visible = true;
			lastOutputField.render(context, mouseX, mouseY, delta);
		} else {
			commandField.visible = true;
			lastOutputField.visible = false;
			commandField.render(context, mouseX, mouseY, delta);
		}
	}

	@Override
	public void setLoaded(boolean loaded) {
		super.setLoaded(loaded);
		typeButton.active = loaded;
		autoButton.active = loaded;
		trackOutputButton.active = loaded;
	}

	@Override
	public boolean isDirty() {
		return commandFieldDirty
			|| Stream.<Dirtyable>of(typeButton, autoButton, trackOutputButton).anyMatch(Dirtyable::isDirty);
	}

	@Override
	public void setY(int y) {
		super.setY(y);

		lastOutputField.setY(commandField.getY());

		typeButton.setY(y);
		autoButton.setY(y);
		trackOutputButton.setY(y);
	}

	@Override
	public void setWidth(int width) {
		super.setWidth(width);

		lastOutputField.setWidth(commandField.getWidth());

		trackOutputButton.setX(getX() + width - 16);
	}

	/**
	 * The name of the world (or server) the command block is in. It is used as
	 * part of the key of the multiline command storage.
	 */
	public static String getWorldName(Minecraft client) {
		var singleplayer = client.getSingleplayerServer();
		if (singleplayer != null) return singleplayer.getWorldData().getLevelName();
		var server = client.getCurrentServer();
		if (server != null) return server.ip;
		var connection = client.getConnection();
		return connection == null ? "" : connection.getConnection().getRemoteAddress().toString();
	}
}
