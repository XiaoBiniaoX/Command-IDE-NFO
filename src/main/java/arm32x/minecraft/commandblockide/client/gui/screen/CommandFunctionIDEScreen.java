package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.network.ApplyFunctionPacket;
import arm32x.minecraft.commandblockide.network.UpdateFunctionCommandPacket;
import arm32x.minecraft.commandblockide.client.CommandBlockIDEClient;
import net.neoforged.neoforge.network.PacketDistributor;
import arm32x.minecraft.commandblockide.util.PacketSplitter;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandFunctionEditor;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CommandFunctionIDEScreen extends CommandIDEScreen<CommandFunctionEditor> {
	private final ResourceLocation functionId;
	private final int startingLineCount;
	private int remainingLines;

	public CommandFunctionIDEScreen(ResourceLocation functionId, int lineCount) {
		this.functionId = functionId;
		this.startingLineCount = lineCount;
		this.remainingLines = lineCount;
	}

	@Override
	protected void firstInit() {
		for (int index = 0; index < startingLineCount; index++) {
			CommandFunctionEditor editor = new CommandFunctionEditor(this, font, 8, 20 * index + 8, width - 16, 16, index);
			if (index == 0) {
				setFocusedEditor(editor);
			}
			addEditor(editor);
		}

		statusText = Component.literal(functionId.toString()).withStyle(ChatFormatting.GRAY).getVisualOrderText();

		super.firstInit();
		setLoaded(remainingLines == 0);
	}

	public void update(int index, String command) {
		if (index < 0 || index >= editors.size()) return;
		var editor = editors.get(index);
		if (!editor.isLoaded()) remainingLines--;
		editor.update(functionId, command);
		setLoaded(remainingLines == 0);
		if (getFocused() == editor) {
			setFocusedEditor(editor);
		}
	}

	@Override
	public boolean save() {
		if (!canSave()) return false;
		if (editors.stream().anyMatch(editor -> editor.getSingleLineCommand().length() > UpdateFunctionCommandPacket.MAX_LINE_LENGTH)) {
			CommandBlockIDEClient.showErrorScreen(new IllegalArgumentException("Function lines cannot exceed 32767 characters."), "saving function");
			return false;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			PacketSplitter.writeHeader(buf);
			buf.writeResourceLocation(functionId);
			buf.writeVarInt(editors.size());
			for (CommandFunctionEditor editor : editors) {
				buf.writeUtf(editor.getSingleLineCommand(), Integer.MAX_VALUE >> 2);
				editor.saveMultilineCommand(functionId);
			}
			PacketSplitter.updateChunkCount(buf);
			if (!super.save()) return false;

			PacketSplitter splitter = new PacketSplitter(buf);
			for (FriendlyByteBuf splitBuf : splitter) {
				byte[] payload = new byte[splitBuf.readableBytes()];
				splitBuf.readBytes(payload);
				PacketDistributor.sendToServer(new ApplyFunctionPacket(payload));
			}

			return true;
		} finally {
			buf.release();
		}
	}
}
