package arm32x.minecraft.commandblockide.client.gui.editor;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

public final class CommandFunctionEditor extends CommandEditor {
    private final String location;
    public CommandFunctionEditor(FontRenderer font, int index, ResourceLocation id, String command) {
        super(font, index, 1024 * 1024);
        location = "function:" + id + ":" + index;
        update(location, command);
    }
    public void store() { store(location); }
}
