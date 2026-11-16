package arm32x.minecraft.commandblockide.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import arm32x.minecraft.commandblockide.command.LegacyCommandParser;
import arm32x.minecraft.commandblockide.client.processor.MultilineCommandProcessor;
import arm32x.minecraft.commandblockide.client.processor.StringMapping;
import arm32x.minecraft.commandblockide.util.Pair;

public final class MultilineTextFieldWidget {
    public int x, y, width;
    public boolean focused, editable = true;
    private final FontRenderer font;
    private final int maxLength;
    private String value = "";
    private int cursor, selection, horizontalScroll;
    public static final int LINE_HEIGHT = 12;
    public LegacyCommandParser.View syntax = new LegacyCommandParser.View();
    private int[] syntaxColors = new int[0];

    public MultilineTextFieldWidget(FontRenderer font, int maxLength) {
        this.font = font; this.maxLength = maxLength;
    }
    public String getText() { return value; }
    public int getCursor() { return cursor; }
    public int getLineCount() { return value.split("\n", -1).length; }
    public int getHeight() { return getLineCount() * LINE_HEIGHT + 8; }
    public int lineStart(int index) { return value.lastIndexOf('\n', index - 1) + 1; }
    private int lineEnd(int index) { int end = value.indexOf('\n', index); return end < 0 ? value.length() : end; }
    public int cursorX() { return x + 4 + font.getStringWidth(value.substring(lineStart(cursor), cursor)) - horizontalScroll; }
    public int cursorY() { return y + 4 + value.substring(0, cursor).split("\n", -1).length * LINE_HEIGHT - LINE_HEIGHT; }
    public boolean atIndent() { return value.substring(lineStart(cursor), cursor).trim().isEmpty(); }
    public void setText(String text) {
        value = text; cursor = value.length(); selection = cursor; parse();
    }
    public void setCursor(int index, boolean keepSelection) {
        cursor = MathHelper.clamp(index, 0, value.length());
        if (!keepSelection) selection = cursor;
        int pixel = font.getStringWidth(value.substring(lineStart(cursor), cursor));
        horizontalScroll = Math.max(0, MathHelper.clamp(horizontalScroll, pixel - Math.max(8, width - 12), pixel));
    }
    public void replaceSelection(String text) {
        StringBuilder filtered = new StringBuilder();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\n' || ChatAllowedCharacters.isAllowedCharacter(ch)) filtered.append(ch);
        }
        int start = Math.min(cursor, selection), end = Math.max(cursor, selection);
        int allowed = Math.max(0, maxLength - value.length() + end - start);
        String insert = filtered.substring(0, Math.min(filtered.length(), allowed));
        value = value.substring(0, start) + insert + value.substring(end);
        setCursor(start + insert.length(), false);
        parse();
    }
    private void parse() {
        Pair<String, StringMapping> processed = MultilineCommandProcessor.getInstance().processCommand(value);
        syntax = LegacyCommandParser.parse(processed.getLeft()); syntaxColors = new int[value.length()];
        int[] palette = {0xAAAAAA, 0x55FFFF, 0xFFFF55, 0x55FF55, 0xFF55FF};
        java.util.Arrays.fill(syntaxColors, value.startsWith("#") ? 0x666666 : 0xAAAAAA);
        for (int[] range : syntax.ranges) {
            int a = processed.getRight().mapIndexOrAfter(range[0]), b = processed.getRight().mapIndexOrAfter(range[1]);
            java.util.Arrays.fill(syntaxColors, Math.min(a, value.length()), Math.min(b, value.length()), palette[range[2]]);
        }
        if (syntax.error >= 0) java.util.Arrays.fill(syntaxColors, Math.min(value.length(), processed.getRight().mapIndexOrAfter(syntax.error)), value.length(), 0xFF5555);
    }
    public boolean keyTyped(char ch, int key) {
        if (!focused || !editable) return false;
        boolean ctrl = GuiScreen.isCtrlKeyDown(), shift = GuiScreen.isShiftKeyDown();
        int start = Math.min(cursor, selection), end = Math.max(cursor, selection);
        if (GuiScreen.isKeyComboCtrlA(key)) { selection = 0; setCursor(value.length(), true); return true; }
        if (GuiScreen.isKeyComboCtrlC(key) || GuiScreen.isKeyComboCtrlX(key)) {
            GuiScreen.setClipboardString(value.substring(start, end));
            if (GuiScreen.isKeyComboCtrlX(key)) replaceSelection("");
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(key)) { replaceSelection(GuiScreen.getClipboardString()); return true; }
        switch (key) {
            case Keyboard.KEY_BACK: case Keyboard.KEY_DELETE:
                if (start == end) {
                    int direction = key == Keyboard.KEY_BACK ? -1 : 1;
                    selection = ctrl ? wordPosition(direction) : MathHelper.clamp(cursor + direction, 0, value.length());
                }
                replaceSelection(""); return true;
            case Keyboard.KEY_LEFT: case Keyboard.KEY_RIGHT:
                int direction = key == Keyboard.KEY_LEFT ? -1 : 1;
                setCursor(ctrl ? wordPosition(direction) : cursor + direction, shift); return true;
            case Keyboard.KEY_HOME: setCursor(ctrl ? 0 : lineStart(cursor), shift); return true;
            case Keyboard.KEY_END: setCursor(ctrl ? value.length() : lineEnd(cursor), shift); return true;
            case Keyboard.KEY_UP: case Keyboard.KEY_DOWN:
                int column = cursor - lineStart(cursor), target;
                if (key == Keyboard.KEY_UP) target = lineStart(cursor) == 0 ? 0 : lineStart(lineStart(cursor) - 1);
                else target = Math.min(value.length(), lineEnd(cursor) + 1);
                setCursor(Math.min(lineEnd(target), target + column), shift); return true;
            case Keyboard.KEY_RETURN: case Keyboard.KEY_NUMPADENTER:
                replaceSelection("\n"); return true;
            case Keyboard.KEY_TAB:
                if (shift && atIndent()) {
                    selection = Math.max(lineStart(cursor), cursor - Math.min(4, cursor - lineStart(cursor)));
                    replaceSelection("");
                } else replaceSelection("    ".substring((cursor - lineStart(cursor)) % 4));
                return true;
            default:
                if (!ctrl && ChatAllowedCharacters.isAllowedCharacter(ch)) { replaceSelection(String.valueOf(ch)); return true; }
                return false;
        }
    }
    private int wordPosition(int direction) {
        int index = cursor;
        if (direction < 0) {
            while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) index--;
            while (index > 0 && !Character.isWhitespace(value.charAt(index - 1))) index--;
        } else {
            while (index < value.length() && !Character.isWhitespace(value.charAt(index))) index++;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        }
        return index;
    }
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + getHeight();
    }
    public void mouse(int mouseX, int mouseY, boolean selecting) {
        String[] lines = value.split("\n", -1);
        int line = MathHelper.clamp((mouseY - y - 4) / LINE_HEIGHT, 0, lines.length - 1);
        int index = 0;
        for (int i = 0; i < line; i++) index += lines[i].length() + 1;
        int pixel = Math.max(0, mouseX - x - 4 + horizontalScroll);
        setCursor(index + font.trimStringToWidth(lines[line], pixel).length(), selecting);
    }
    public void scrollHorizontally(int amount) { horizontalScroll = Math.max(0, horizontalScroll + amount); }

    public void draw(int screenHeight) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + getHeight() + 1, focused ? 0xFFAAAAAA : 0xFF555555);
        Gui.drawRect(x, y, x + width, y + getHeight(), 0xDD111111);
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        int top = Math.max(8, y + 2), bottom = Math.min(screenHeight - 36, y + getHeight() - 2);
        if (bottom <= top) return;
        GL11.glScissor((x + 2) * scale, mc.displayHeight - bottom * scale, Math.max(0, width - 4) * scale, (bottom - top) * scale);
        String[] lines = value.split("\n", -1);
        int offset = 0, selectedStart = Math.min(cursor, selection), selectedEnd = Math.max(cursor, selection);
        boolean comment = value.startsWith("#");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]; int drawY = y + 4 + i * LINE_HEIGHT;
            if (drawY + LINE_HEIGHT >= top && drawY < bottom) {
                int drawX = x + 4 - horizontalScroll;
                int a = Math.max(offset, selectedStart), b = Math.min(offset + line.length(), selectedEnd);
                if (b > a) Gui.drawRect(drawX + font.getStringWidth(line.substring(0, a - offset)), drawY - 1,
                        drawX + font.getStringWidth(line.substring(0, b - offset)), drawY + 10, 0xFF244B78);
                for (int aIndex = 0; aIndex < line.length();) {
                    int color = syntaxColors[offset + aIndex], bIndex = aIndex + 1;
                    while (bIndex < line.length() && syntaxColors[offset + bIndex] == color) bIndex++;
                    String token = line.substring(aIndex, bIndex);
                    font.drawString(token, drawX, drawY, editable ? color : 0x777777);
                    drawX += font.getStringWidth(token);
                    aIndex = bIndex;
                }
            }
            offset += line.length() + 1;
        }
        if (focused && editable && (Minecraft.getSystemTime() / 300) % 2 == 0) {
            Gui.drawRect(cursorX(), cursorY() - 1, cursorX() + 1, cursorY() + 10, 0xFFFFFFFF);
        }
        GL11.glScissor(0, mc.displayHeight - (screenHeight - 36) * scale, mc.displayWidth, (screenHeight - 44) * scale);
    }
}
