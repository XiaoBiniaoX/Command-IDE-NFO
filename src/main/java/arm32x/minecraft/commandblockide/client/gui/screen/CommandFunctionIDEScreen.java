package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.network.ApplyFunctionPacket;
import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.util.PacketSplitter;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandEditor;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandFunctionEditor;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CommandFunctionIDEScreen extends CommandIDEScreen<CommandFunctionEditor> {
	private final ResourceLocation functionId;
	private final int startingLineCount;

	public CommandFunctionIDEScreen(ResourceLocation functionId, int lineCount) {
		this.functionId = functionId;
		this.startingLineCount = lineCount;
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
	}

	public void update(int index, String command) {
		var editor = editors.get(index);
		editor.update(functionId, command);
		setLoaded(true);
		if (getFocused() == editor) {
			setFocusedEditor(editor);
		}
	}

	@Override
	public void save() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		PacketSplitter.writeHeader(buf);
		buf.writeResourceLocation(functionId);
		buf.writeVarInt(editors.size());
		for (CommandEditor editor : editors) {
			buf.writeUtf(editor.getSingleLineCommand(), Integer.MAX_VALUE >> 2);
			if (editor instanceof CommandFunctionEditor functionEditor) {
				functionEditor.saveMultilineCommand(functionId);
			}
		}
		PacketSplitter.updateChunkCount(buf);

		PacketSplitter splitter = new PacketSplitter(buf);
		for (FriendlyByteBuf splitBuf : splitter) {
			byte[] payload = new byte[splitBuf.readableBytes()];
			splitBuf.readBytes(payload);
			Packets.CHANNEL.sendToServer(new ApplyFunctionPacket(payload));
		}

		super.save();
	}
}
