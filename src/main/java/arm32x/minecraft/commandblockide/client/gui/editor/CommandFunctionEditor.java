package arm32x.minecraft.commandblockide.client.gui.editor;

import arm32x.minecraft.commandblockide.client.gui.editor.CommandBlockEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class CommandFunctionEditor extends CommandEditor {
	private @Nullable String originalCommand;

	private boolean dirty = false;

	public CommandFunctionEditor(Screen screen, Font font, int x, int y, int width, int height, int index) {
		super(screen, font, x, y, width, height, 0, 0, index);

		suggestor.ide$setAllowComments(true);
		suggestor.ide$setSlashForbidden(true);
	}

	public void saveMultilineCommand(ResourceLocation function) {
		Minecraft client = Minecraft.getInstance();
		String world = CommandBlockEditor.getWorldName(client);

		MultilineCommandStorage.getInstance().add(commandField.getText(), getSingleLineCommand(), client.isSingleplayer(), world, function, index);
	}

	public void update(ResourceLocation functionId, String command) {
		originalCommand = command;
		Minecraft client = Minecraft.getInstance();
		commandField.setText(MultilineCommandStorage.getInstance().getRobust(
			command,
			processor,
			client.isSingleplayer(),
			CommandBlockEditor.getWorldName(client),
			functionId,
			index
		));

		suggestor.setWindowActive(commandField.isFocused());
		suggestor.updateCommandInfo();

		dirty = false;
		setLoaded(true);
	}

	@Override
	public void commandChanged(String newCommand) {
		if (!newCommand.equals(originalCommand)) {
			dirty = true;
		}
		super.commandChanged(newCommand);
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}
}
