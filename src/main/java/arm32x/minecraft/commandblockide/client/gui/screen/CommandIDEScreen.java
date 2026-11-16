package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.client.gui.editor.CommandBlockEditor;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import arm32x.minecraft.commandblockide.client.suggest.CommandSuggestor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityCommandBlock.Mode;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ITabCompleter;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import arm32x.minecraft.commandblockide.network.Packets;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.world.World;

public abstract class CommandIDEScreen<E extends CommandEditor> extends GuiScreen implements ITabCompleter {
    protected final List<E> editors = new ArrayList<>();
    protected int focusedIndex = -1, scrollOffset, maxScroll;
    protected String status = "";
    protected boolean initialized, closeAfterSave;
    protected final CommandSuggestor suggestor = new CommandSuggestor();
    private boolean draggingScrollbar, draggingSelection;
    private long suggestionAt;
    private NetHandlerPlayClient connection;
    private World editorWorld;
    protected boolean sessionAvailable;
    protected abstract void firstInit();
    public abstract boolean save();

    @Override public void initGui() {
        if (mc.world == null || mc.player == null || mc.getConnection() == null) { mc.displayGuiScreen(null); return; }
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        buttonList.add(new GuiButton(0, 8, height - 28, 24, 20, ""));
        buttonList.add(new GuiButton(1, width - 216, height - 28, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(2, width - 108, height - 28, 100, 20, I18n.format("gui.cancel")));
        if (!initialized) {
            connection = mc.getConnection(); editorWorld = mc.world;
            sessionAvailable = true;
            MultilineCommandStorage.load(); firstInit(); initialized = true;
        }
        layout();
        if (focusedIndex >= 0) focus(focusedIndex);
    }
    protected void layout() {
        int y = 8 - scrollOffset;
        for (E editor : editors) {
            editor.field.x = editor instanceof CommandBlockEditor ? 66 : 28;
            editor.field.y = y;
            editor.field.width = Math.max(16, width - editor.field.x - (editor instanceof CommandBlockEditor ? 30 : 12));
            y += editor.height() + 4;
        }
        maxScroll = Math.max(0, y + scrollOffset - (height - 36));
    }
    protected void focus(int index) {
        if (editors.isEmpty()) return;
        for (E editor : editors) editor.field.focused = false;
        focusedIndex = index; suggestor.hide(); suggestionAt = net.minecraft.client.Minecraft.getSystemTime() + 200;
        if (index < 0) return;
        E editor = editors.get(index);
        editor.field.focused = true;
        layout();
        int pixelY = editor.field.cursorY();
        if (pixelY < 8) scrollOffset = Math.max(0, scrollOffset + pixelY - 8);
        else if (pixelY > height - 50) scrollOffset = Math.min(maxScroll, scrollOffset + pixelY - height + 50);
        layout();
    }
    private void changeFocus(int direction) {
        if (editors.isEmpty()) return;
        int index = focusedIndex;
        for (int i = 0; i < editors.size(); i++) {
            index = (index + direction + editors.size()) % editors.size();
            if (editors.get(index).loaded) { focus(index); return; }
        }
    }
    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (mc.player == null || mc.world != editorWorld || mc.getConnection() != connection) { mc.displayGuiScreen(null); return; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_S) { closeAfterSave = false; save(); return; }
        if (isCtrlKeyDown() && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_TAB)) {
            changeFocus(key == Keyboard.KEY_UP || key == Keyboard.KEY_TAB && isShiftKeyDown() ? -1 : 1); return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (suggestor.active()) suggestor.hide();
            else if (focusedIndex >= 0) focus(-1);
            else mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (focusedIndex < 0) { closeAfterSave = true; if (save()) mc.displayGuiScreen(null); return; }
            if (isCtrlKeyDown()) { focus(-1); return; }
            if (suggestor.active()) { suggestor.accept(); layout(); return; }
        }
        if (focusedIndex < 0) return;
        E editor = editors.get(focusedIndex);
        if (suggestor.active()) {
            if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) { suggestor.move(key == Keyboard.KEY_UP ? -1 : 1); return; }
            if (key == Keyboard.KEY_TAB) { if (isShiftKeyDown()) suggestor.move(-1); else suggestor.accept(); layout(); return; }
        }
        if (editor.field.editable && (key == Keyboard.KEY_TAB && (editor.field.getText().isEmpty() || !editor.field.atIndent()) && !isShiftKeyDown()
                || key == Keyboard.KEY_SPACE && isCtrlKeyDown())) {
            suggestionAt = 0;
            suggestor.request(editor.field, editor instanceof CommandBlockEditor ? ((CommandBlockEditor) editor).tile.getPos() : mc.player.getPosition(), true);
            return;
        }
        if (editor.field.keyTyped(ch, key)) {
            suggestor.hide(); layout();
            suggestionAt = net.minecraft.client.Minecraft.getSystemTime() + 200;
            int pixelY = editor.field.cursorY();
            if (pixelY < 8 || pixelY > height - 50) focus(focusedIndex);
        }
    }
    @Override public void updateScreen() {
        sessionAvailable = mc.player != null && mc.world == editorWorld && mc.getConnection() == connection;
        if (!sessionAvailable) { mc.displayGuiScreen(null); return; }
        if (suggestionAt == 0 || net.minecraft.client.Minecraft.getSystemTime() < suggestionAt || focusedIndex < 0) return;
        E editor = editors.get(focusedIndex);
        if (editor.field.editable && (editor.field.getText().isEmpty() || !editor.field.atIndent())) {
            if (!suggestor.request(editor.field, editor instanceof CommandBlockEditor ? ((CommandBlockEditor) editor).tile.getPos() : mc.player.getPosition(), false)) return;
        }
        suggestionAt = 0;
    }
    @Override public void setCompletions(String... matches) { suggestor.receive(matches); layout(); }
    public void receiveSuggestions(Packets.Suggestions packet) { suggestor.receive(packet); layout(); }
    @Override protected void actionPerformed(GuiButton button) {
        if (!sessionAvailable) return;
        if (button.id == 2) mc.displayGuiScreen(null);
        else { closeAfterSave = button.id == 1; if (save() && closeAfterSave) mc.displayGuiScreen(null); }
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) return;
        if (mouseY >= height - 36) { super.mouseClicked(mouseX, mouseY, button); return; }
        if (mouseY < 8) return;
        if (suggestor.click(mouseX, mouseY)) { layout(); return; }
        if (mouseX >= width - 5 && maxScroll > 0) { draggingScrollbar = true; mouseClickMove(mouseX, mouseY, button, 0); return; }
        for (int i = 0; i < editors.size(); i++) {
            E editor = editors.get(i); int y = editor.field.y;
            if (editor instanceof CommandBlockEditor && editor.field.editable && mouseY >= y && mouseY < y + 18) {
                CommandBlockEditor block = (CommandBlockEditor) editor;
                if (mouseX >= 28 && mouseX < 46) {
                    if (isShiftKeyDown()) block.conditional = !block.conditional;
                    else block.mode = block.mode == Mode.REDSTONE ? Mode.AUTO : block.mode == Mode.AUTO ? Mode.SEQUENCE : Mode.REDSTONE;
                    return;
                }
                if (mouseX >= 46 && mouseX < 64) { block.automatic = !block.automatic; return; }
                if (mouseX >= width - 28 && mouseX < width - 10) { block.trackOutput = !block.trackOutput; return; }
            }
            if (editor.field.contains(mouseX, mouseY) && editor.loaded) {
                editor.field.mouse(mouseX, mouseY, isShiftKeyDown()); focus(i); draggingSelection = true; return;
            }
        }
        focus(-1);
    }
    @Override protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (draggingScrollbar) {
            scrollOffset = MathHelper.clamp((mouseY - 8) * maxScroll / Math.max(1, height - 44), 0, maxScroll); layout();
        } else if (draggingSelection && focusedIndex >= 0) editors.get(focusedIndex).field.mouse(mouseX, mouseY, true);
    }
    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        draggingScrollbar = false; draggingSelection = false; super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int direction = wheel > 0 ? -1 : 1;
        if (suggestor.active()) suggestor.move(direction);
        else if (isShiftKeyDown() && focusedIndex >= 0) editors.get(focusedIndex).field.scrollHorizontally(direction * 30);
        else { scrollOffset = MathHelper.clamp(scrollOffset + direction * 50, 0, maxScroll); layout(); }
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
    public boolean isDirty() { for (E editor : editors) if (editor.loaded && editor.isDirty()) return true; return false; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout(); drawDefaultBackground();
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - (height - 36) * scale, mc.displayWidth, Math.max(0, height - 44) * scale);
        String tooltip = null;
        for (E editor : editors) {
            int y = editor.field.y;
            if (y + editor.height() < 8 || y >= height - 36) continue;
            String number = (editor.isDirty() && editor.loaded ? "*" : "") + (editor.index + 1);
            fontRenderer.drawString(number, 24 - fontRenderer.getStringWidth(number), y + 4,
                    editor.highlighted || editor.field.focused ? 0xFFFFFF : 0x888888);
            if (editor.loaded) editor.field.draw(height);
            else fontRenderer.drawString(I18n.format("commandBlockIDE.unloaded"), editor.field.x + 4, y + 4, 0x777777);
            if (editor instanceof CommandBlockEditor) {
                CommandBlockEditor block = (CommandBlockEditor) editor;
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(block.mode == Mode.REDSTONE ? Blocks.COMMAND_BLOCK
                        : block.mode == Mode.AUTO ? Blocks.REPEATING_COMMAND_BLOCK : Blocks.CHAIN_COMMAND_BLOCK), 28, y);
                RenderHelper.disableStandardItemLighting();
                fontRenderer.drawString(block.automatic ? "A" : "R", 50, y + 4, 0xCCCCCC);
                fontRenderer.drawString(block.trackOutput ? "O" : "-", width - 24, y + 4, 0xCCCCCC);
                if (block.conditional) drawRect(28, y + 16, 44, y + 18, 0xFF55FFFF);
                if (mouseY >= y && mouseY < y + 18) {
                    if (mouseX >= 28 && mouseX < 46) tooltip = I18n.format("commandBlockIDE.type." + block.mode.name().toLowerCase(java.util.Locale.ROOT)
                            + (block.conditional ? "Conditional" : "")) + " (Shift)";
                    else if (mouseX >= 46 && mouseX < 64) tooltip = I18n.format(block.automatic ? "advMode.autoexec.bat" : "advMode.autoexec.redstone");
                    else if (mouseX >= width - 28 && mouseX < width - 10) {
                        tooltip = I18n.format(block.trackOutput ? "commandBlockIDE.lastOutput.on" : "commandBlockIDE.lastOutput.off");
                        drawRect(editor.field.x, y, editor.field.x + editor.field.width, y + editor.height(), 0xFF111111);
                        fontRenderer.drawString(fontRenderer.trimStringToWidth(block.output().isEmpty() ? I18n.format("commandBlockIDE.lastOutput.none")
                                : block.output(), editor.field.width - 8), editor.field.x + 4, y + 4, 0xAAAAAA);
                    }
                }
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        suggestor.draw(fontRenderer, width, height);
        if (maxScroll > 0) {
            int track = height - 44, bar = Math.max(8, track * track / (maxScroll + track));
            int top = 8 + scrollOffset * (track - bar) / maxScroll;
            drawRect(width - 4, top, width - 1, top + bar, 0x80FFFFFF);
        }
        drawRect(0, height - 36, width, height, 0xEE181818);
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(new ResourceLocation("commandblockide", "textures/gui/icons/save.png"));
        drawModalRectWithCustomSizedTexture(12, height - 26, 0, 0, 16, 16, 16, 16);
        String shown = status;
        if (focusedIndex >= 0) {
            arm32x.minecraft.commandblockide.command.LegacyCommandParser.View syntax = editors.get(focusedIndex).field.syntax;
            if (syntax.error >= 0) shown += " | " + syntax.message + " | " + syntax.usage;
            else if (!syntax.usage.isEmpty()) shown += " | " + syntax.usage;
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(shown, Math.max(0, width - 264)), 40, height - 22, 0xAAAAAA);
        if (mouseX >= 8 && mouseX < 32 && mouseY >= height - 28) tooltip = I18n.format("commandBlockIDE.save") + " (Ctrl+S)";
        else if (mouseY >= height - 36 && focusedIndex >= 0) {
            arm32x.minecraft.commandblockide.command.LegacyCommandParser.View syntax = editors.get(focusedIndex).field.syntax;
            tooltip = syntax.error >= 0 ? syntax.message + " | " + syntax.usage : syntax.supported ? syntax.usage : "This command's argument syntax is not verified.";
        }
        if (tooltip != null && mouseY >= 8 && mouseY < height - 36) drawHoveringText(tooltip, mouseX, mouseY);
        else if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY);
    }
}
