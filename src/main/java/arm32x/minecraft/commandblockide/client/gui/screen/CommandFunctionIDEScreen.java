package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.client.gui.editor.CommandFunctionEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import arm32x.minecraft.commandblockide.network.Packets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ResourceLocation;

public final class CommandFunctionIDEScreen extends CommandIDEScreen<CommandFunctionEditor> {
    private final ResourceLocation id;
    private final List<String> lines;
    private boolean saving;
    private String revision;
    private int request;
    private long saveStarted;
    public CommandFunctionIDEScreen(ResourceLocation id, String revision, List<String> lines) { this.id = id; this.revision = revision; this.lines = lines; }
    @Override protected void firstInit() {
        for (int i = 0; i < lines.size(); i++) editors.add(new CommandFunctionEditor(fontRenderer, i, id, lines.get(i)));
        focusedIndex = editors.isEmpty() ? -1 : 0;
        status = id.toString();
    }
    @Override public boolean save() {
        if (saving) return false;
        for (CommandFunctionEditor editor : editors) editor.store();
        if (!MultilineCommandStorage.save()) { status = "Cannot save local formatting; edits kept."; closeAfterSave = false; return false; }
        if (!isDirty()) return true;
        List<String> commands = new ArrayList<>();
        for (CommandFunctionEditor editor : editors) commands.add(editor.singleLine());
        request = Packets.nextRequest();
        try { Packets.sendFunction(id, revision, commands, null, request); }
        catch (IllegalArgumentException ex) { status = ex.getMessage(); closeAfterSave = false; return false; }
        saving = true; status = id + " ...";
        saveStarted = net.minecraft.client.Minecraft.getSystemTime();
        for (CommandFunctionEditor editor : editors) editor.field.editable = false;
        return false;
    }
    public void result(Packets.SaveResult result) {
        if (!saving || result.request != request) return;
        saving = false; status = result.success ? id.toString() : result.message;
        for (CommandFunctionEditor editor : editors) {
            editor.field.editable = true;
            if (result.success) editor.saved();
        }
        if (result.success) {
            List<String> commands = new ArrayList<>(); for (CommandFunctionEditor editor : editors) commands.add(editor.singleLine());
            revision = arm32x.minecraft.commandblockide.util.ContentHash.of(String.join("\n", commands));
            if (closeAfterSave) mc.displayGuiScreen(null);
        }
        else closeAfterSave = false;
    }
    @Override public void updateScreen() {
        super.updateScreen();
        if (!sessionAvailable) return;
        if (saving && net.minecraft.client.Minecraft.getSystemTime() - saveStarted > 30000) {
            saving = false; closeAfterSave = false; status = "Save reply timed out; server outcome unknown. Edits kept.";
            for (CommandFunctionEditor editor : editors) editor.field.editable = true;
        }
    }
}
