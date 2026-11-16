package arm32x.minecraft.commandblockide.client.gui.editor;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntityCommandBlock.Mode;
import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.client.CommandBlockIDEClient;

public final class CommandBlockEditor extends CommandEditor {
    public TileEntityCommandBlock tile;
    public int pendingRequest;
    private String revision;
    public Mode mode;
    public boolean conditional, automatic, trackOutput;
    private Mode originalMode;
    private boolean originalConditional, originalAutomatic, originalOutput;
    public CommandBlockEditor(FontRenderer font, int index, TileEntityCommandBlock tile) {
        super(font, index, 32500);
        this.tile = tile; mode = tile.getMode();
    }
    public String location() { return "block:" + tile.getPos().toLong(); }
    public void update() {
        if (loaded && isDirty()) return;
        super.update(location(), tile.getCommandBlockLogic().getCommand());
        mode = tile.getMode(); conditional = tile.isConditional(); automatic = tile.isAuto();
        trackOutput = tile.getCommandBlockLogic().shouldTrackOutput();
        saved();
    }
    @Override public boolean isDirty() {
        return super.isDirty() || mode != originalMode || conditional != originalConditional
                || automatic != originalAutomatic || trackOutput != originalOutput;
    }
    @Override public void saved() {
        super.saved(); revision = arm32x.minecraft.commandblockide.util.ContentHash.of(singleLine() + "\n" + mode + ":" + conditional + ":" + automatic + ":" + trackOutput);
        originalMode = mode; originalConditional = conditional;
        originalAutomatic = automatic; originalOutput = trackOutput;
    }
    public void save() {
        if (!loaded || !isDirty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (CommandBlockIDEClient.serverHasMod()) {
            pendingRequest = Packets.nextRequest();
            Packets.CHANNEL.sendToServer(new Packets.ApplyBlock(pendingRequest, mc.world.provider.getDimension(), tile.getPos(), revision,
                singleLine(), mode, conditional, automatic, trackOutput));
            field.editable = false;
            return;
        }
        CommandBlockBaseLogic logic = tile.getCommandBlockLogic();
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        logic.fillInInfo(buffer);
        buffer.writeString(singleLine()); buffer.writeBoolean(trackOutput); buffer.writeString(mode.name());
        buffer.writeBoolean(conditional); buffer.writeBoolean(automatic);
        Minecraft.getMinecraft().getConnection().sendPacket(new CPacketCustomPayload("MC|AutoCmd", buffer));
        logic.setCommand(singleLine()); logic.setTrackOutput(trackOutput);
        if (!trackOutput) logic.setLastOutput(null);
        // Vanilla has no structured acknowledgement. Keep dirty rather than claiming success.
    }
    public String output() {
        return tile.getCommandBlockLogic().getLastOutput() == null ? "" : tile.getCommandBlockLogic().getLastOutput().getUnformattedText();
    }
}
