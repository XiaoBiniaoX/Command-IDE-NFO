package arm32x.minecraft.commandblockide.client.gui.editor;

import arm32x.minecraft.commandblockide.client.gui.MultilineTextFieldWidget;
import arm32x.minecraft.commandblockide.client.processor.MultilineCommandProcessor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import net.minecraft.client.gui.FontRenderer;

public class CommandEditor {
    public final int index;
    public final MultilineTextFieldWidget field;
    public boolean loaded, highlighted;
    protected String original = "";
    public CommandEditor(FontRenderer font, int index, int maxLength) {
        this.index = index;
        field = new MultilineTextFieldWidget(font, maxLength);
        field.editable = false;
    }
    public int height() { return field.getHeight(); }
    public String singleLine() { return MultilineCommandProcessor.getInstance().processCommand(field.getText()).getLeft(); }
    public boolean isDirty() { return !field.getText().equals(original); }
    public void update(String location, String command) {
        field.setText(MultilineCommandStorage.getRobust(location, command));
        original = field.getText(); loaded = true; field.editable = true;
    }
    public void store(String location) { MultilineCommandStorage.add(location, field.getText(), singleLine()); }
    public void saved() { original = field.getText(); }
}
