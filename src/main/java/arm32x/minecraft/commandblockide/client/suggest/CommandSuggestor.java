package arm32x.minecraft.commandblockide.client.suggest;

import arm32x.minecraft.commandblockide.client.gui.MultilineTextFieldWidget;
import arm32x.minecraft.commandblockide.client.processor.MultilineCommandProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.util.math.BlockPos;
import arm32x.minecraft.commandblockide.command.LegacyCommandParser;
import arm32x.minecraft.commandblockide.client.CommandBlockIDEClient;
import arm32x.minecraft.commandblockide.network.Packets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CommandSuggestor {
    private MultilineTextFieldWidget field;
    private String requestText;
    private int requestCursor, start, end, selected, x, y, width, first;
    private String[] matches = new String[0];
    private boolean pending, completeSingle;
    private int request;
    private long sentAt;
    private String prefix;
    private int tokenEnd;
    public boolean active() { return matches.length > 0; }
    public void hide() { matches = new String[0]; }
    public boolean request(MultilineTextFieldWidget field, BlockPos pos, boolean completeSingle) {
        boolean custom = CommandBlockIDEClient.serverHasMod();
        if (!custom && pending && Minecraft.getSystemTime() - sentAt < 2000) return false;
        if (field.getText().startsWith("#")) return true;
        this.field = field; this.completeSingle = completeSingle;
        requestText = field.getText(); requestCursor = field.getCursor();
        start = requestCursor;
        while (start > 0 && !Character.isWhitespace(requestText.charAt(start - 1))) start--;
        end = requestCursor;
        tokenEnd = end;
        while (tokenEnd < requestText.length() && !Character.isWhitespace(requestText.charAt(tokenEnd))) tokenEnd++;
        prefix = MultilineCommandProcessor.getInstance().processCommand(requestText.substring(0, requestCursor)).getLeft();
        if (prefix.startsWith("/")) prefix = prefix.substring(1);
        if (prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32500) return true;
        pending = true; hide();
        sentAt = Minecraft.getSystemTime(); request = Packets.nextRequest();
        if (custom) Packets.CHANNEL.sendToServer(new Packets.Suggest(request, prefix, pos));
        else Minecraft.getMinecraft().getConnection().sendPacket(new CPacketTabComplete(prefix, pos, true));
        return true;
    }
    public void receive(Packets.Suggestions packet) {
        if (packet.request == request) receive(packet.matches.toArray(new String[0]));
    }
    public void receive(String[] matches) {
        if (!pending) return;
        pending = false;
        if (!field.focused || !requestText.equals(field.getText()) || requestCursor != field.getCursor()) return;
        Set<String> filtered = new LinkedHashSet<>();
        String partial = requestText.substring(start, end);
        if (start == 0 && partial.startsWith("/")) partial = partial.substring(1);
        for (String match : matches) {
            String candidate = match.startsWith("/") ? match.substring(1) : match;
            if (candidate.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) filtered.add(candidate);
        }
        // Literal branches from the legacy grammar, never the entire command list at an argument position.
        int nested = LegacyCommandParser.nestedStart(prefix);
        String input = prefix.substring(nested);
        if (input.contains(" ")) {
            com.mojang.brigadier.suggestion.Suggestions local = LegacyCommandParser.DISPATCHER.getCompletionSuggestions(
                LegacyCommandParser.DISPATCHER.parse(input, new Object())).join();
            for (com.mojang.brigadier.suggestion.Suggestion suggestion : local.getList()) {
                if (suggestion.getText().toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) filtered.add(suggestion.getText());
            }
        }
        // 1.12 selectors have no Brigadier provider. Complete keys within the selector's current segment.
        if (partial.startsWith("@") && partial.contains("[") && !partial.contains("]")) {
            int segment = Math.max(partial.lastIndexOf('['), partial.lastIndexOf(',')) + 1;
            String typed = partial.substring(segment);
            if (!typed.contains("=")) {
                for (String key : new String[]{"x=", "y=", "z=", "dx=", "dy=", "dz=", "r=", "rm=", "c=", "type=", "name=", "tag=", "team=", "m=", "l=", "lm=", "rx=", "rxm=", "ry=", "rym="}) {
                    if (key.startsWith(typed)) filtered.add(partial.substring(0, segment) + key);
                }
            }
        }
        this.matches = filtered.toArray(new String[0]); selected = 0; first = 0;
        if (completeSingle && this.matches.length == 1) accept();
    }
    public void move(int direction) {
        if (active()) selected = (selected + direction + matches.length) % matches.length;
    }
    public void accept() {
        if (!active()) return;
        if (!field.editable || !requestText.equals(field.getText()) || field.getCursor() != requestCursor) { hide(); return; }
        field.setCursor(start, false); field.setCursor(tokenEnd, true);
        String completion = matches[selected];
        if (start == 0 && requestText.startsWith("/")) completion = "/" + completion;
        field.replaceSelection(completion); hide();
    }
    public boolean click(int mouseX, int mouseY) {
        if (!active() || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + Math.min(8, matches.length) * 12) return false;
        selected = first + (mouseY - y) / 12; accept(); return true;
    }
    public void draw(FontRenderer font, int screenWidth, int screenHeight) {
        if (!active() || !field.focused || !field.editable) return;
        if (!field.getText().equals(requestText) || field.getCursor() != requestCursor) { hide(); return; }
        width = 40;
        for (String match : matches) width = Math.max(width, font.getStringWidth(match) + 8);
        width = Math.min(width, screenWidth - 16);
        int rows = Math.min(8, matches.length);
        first = Math.max(0, Math.min(first, matches.length - rows));
        if (selected < first) first = selected;
        if (selected >= first + rows) first = selected - rows + 1;
        x = Math.max(8, Math.min(field.cursorX(), screenWidth - width - 8));
        y = Math.max(8, Math.min(field.cursorY() + 12, screenHeight - 36 - rows * 12));
        Gui.drawRect(x, y, x + width, y + rows * 12, 0xFF101010);
        for (int i = 0; i < rows; i++) {
            if (first + i == selected) Gui.drawRect(x, y + i * 12, x + width, y + i * 12 + 12, 0xFF344B64);
            font.drawString(font.trimStringToWidth(matches[first + i], width - 8), x + 4, y + i * 12 + 2, 0xFFFFFF);
        }
    }
}
