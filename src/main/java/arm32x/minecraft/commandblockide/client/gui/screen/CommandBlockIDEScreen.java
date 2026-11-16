package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.client.CommandBlockIDEClient;
import arm32x.minecraft.commandblockide.client.CommandChainTracer;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandBlockEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import arm32x.minecraft.commandblockide.network.Packets;
import java.util.List;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.math.BlockPos;

public final class CommandBlockIDEScreen extends CommandIDEScreen<CommandBlockEditor> {
    private final TileEntityCommandBlock starting;
    private long saveStarted;
    private boolean saveFailed;
    public CommandBlockIDEScreen(TileEntityCommandBlock starting) { this.starting = starting; }
    @Override protected void firstInit() {
        List<BlockPos> positions = new CommandChainTracer(mc.world).trace(starting.getPos());
        for (BlockPos pos : positions) {
            TileEntity tile = mc.world.getTileEntity(pos);
            if (!(tile instanceof TileEntityCommandBlock)) continue;
            CommandBlockEditor editor = new CommandBlockEditor(fontRenderer, editors.size(), (TileEntityCommandBlock) tile);
            editors.add(editor);
            if (pos.equals(starting.getPos())) { focusedIndex = editor.index; editor.highlighted = true; }
        }
        status = starting.getPos().getX() + ", " + starting.getPos().getY() + ", " + starting.getPos().getZ();
        if (CommandBlockIDEClient.serverHasMod()) {
            for (int i = 0; i < positions.size(); i += 1024) {
                Packets.CHANNEL.sendToServer(new Packets.RefreshBlocks(positions.subList(i, Math.min(i + 1024, positions.size()))));
            }
        }
        // Until the vanilla/custom NBT response arrives, do not permit saving default blank client data.
    }
    public void update(BlockPos pos) {
        TileEntity tile = mc.world.getTileEntity(pos);
        if (!(tile instanceof TileEntityCommandBlock)) return;
        for (CommandBlockEditor editor : editors) if (editor.tile.getPos().equals(pos)) {
            editor.tile = (TileEntityCommandBlock) tile; editor.update();
        }
        layout();
        if (focusedIndex >= 0 && editors.get(focusedIndex).tile.getPos().equals(pos)) focus(focusedIndex);
    }
    @Override public boolean save() {
        for (CommandBlockEditor editor : editors) if (editor.pendingRequest != 0) return false;
        for (CommandBlockEditor editor : editors) {
            if (editor.loaded && editor.singleLine().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32500) {
                status = "#" + (editor.index + 1) + ": " + net.minecraft.client.resources.I18n.format("commandBlockIDE.commandTooLong");
                return false;
            }
        }
        for (CommandBlockEditor editor : editors) if (editor.loaded) editor.store(editor.location());
        if (!MultilineCommandStorage.save()) { status = "Cannot save local formatting; edits kept."; closeAfterSave = false; return false; }
        saveFailed = false; saveStarted = net.minecraft.client.Minecraft.getSystemTime();
        for (CommandBlockEditor editor : editors) editor.save();
        if (!CommandBlockIDEClient.serverHasMod() && isDirty()) {
            status = "Sent to vanilla server; save cannot be confirmed."; closeAfterSave = false; return false;
        }
        for (CommandBlockEditor editor : editors) if (editor.pendingRequest != 0) return false;
        return true;
    }
    public void result(Packets.SaveResult result) {
        boolean found = false;
        for (CommandBlockEditor editor : editors) if (editor.pendingRequest == result.request) {
            found = true; editor.pendingRequest = 0; editor.field.editable = true;
            if (result.success) editor.saved(); else { status = result.message; saveFailed = true; closeAfterSave = false; }
        }
        if (!found) return;
        for (CommandBlockEditor editor : editors) if (editor.pendingRequest != 0) return;
        if (!saveFailed) { status = "Command blocks saved."; if (closeAfterSave) mc.displayGuiScreen(null); }
    }
    @Override public void updateScreen() {
        super.updateScreen();
        if (!sessionAvailable) return;
        if (net.minecraft.client.Minecraft.getSystemTime() - saveStarted <= 30000) return;
        for (CommandBlockEditor editor : editors) if (editor.pendingRequest != 0) {
            editor.pendingRequest = 0; editor.field.editable = true; closeAfterSave = false;
            status = "Save reply timed out; server outcome unknown. Edits kept.";
        }
    }
}
