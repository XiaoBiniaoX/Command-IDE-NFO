package arm32x.minecraft.commandblockide.client;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import arm32x.minecraft.commandblockide.CommonProxy;
import arm32x.minecraft.commandblockide.client.gui.screen.CommandBlockIDEScreen;
import arm32x.minecraft.commandblockide.client.gui.screen.CommandFunctionIDEScreen;
import arm32x.minecraft.commandblockide.mixin.client.GuiCommandBlockAccessor;
import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.util.PacketMerger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import arm32x.minecraft.commandblockide.client.gui.screen.CommandIDEScreen;
import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

public final class CommandBlockIDEClient extends CommonProxy {
    private final PacketMerger merger = new PacketMerger();
    @Override public void init() { MinecraftForge.EVENT_BUS.register(this); }
    @SubscribeEvent public void openGui(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiCommandBlock) {
            event.setGui(new CommandBlockIDEScreen(((GuiCommandBlockAccessor) event.getGui()).ide$getCommandBlock()));
        }
    }
    public static boolean serverHasMod() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) return false;
        NetworkDispatcher dispatcher = NetworkDispatcher.get(mc.getConnection().getNetworkManager());
        return mc.isSingleplayer() || dispatcher != null && dispatcher.getModList().containsKey(CommandBlockIDE.MOD_ID);
    }
    @Override public void receiveFunction(Packets.FunctionChunk packet, INetHandler handler) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.world == null || mc.player == null || mc.getConnection() != handler) return;
            try {
                byte[] bytes = merger.append(packet.request, packet.offset, packet.total, packet.bytes);
                if (bytes == null) return;
                String text = new String(bytes, StandardCharsets.UTF_8);
                int separator = text.indexOf('\n'), revisionEnd = text.indexOf('\n', separator + 1);
                if (separator < 1 || revisionEnd < 0) throw new IllegalArgumentException("Invalid function response");
                ResourceLocation id = new ResourceLocation(text.substring(0, separator));
                if (mc.currentScreen instanceof CommandIDEScreen && ((CommandIDEScreen<?>) mc.currentScreen).isDirty()) {
                    mc.player.sendMessage(new TextComponentString("Close or save the current editor before opening another function.")); return;
                }
                mc.displayGuiScreen(new CommandFunctionIDEScreen(id, text.substring(separator + 1, revisionEnd),
                    Arrays.asList(text.substring(revisionEnd + 1).split("\n", -1))));
            } catch (IllegalArgumentException ex) {
                CommandBlockIDE.LOGGER.warn("Invalid function response", ex);
                mc.player.sendMessage(new TextComponentString("Cannot open function: " + ex.getMessage()));
            }
        });
    }
    @Override public void receiveResult(Packets.SaveResult packet, INetHandler handler) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.player == null || mc.getConnection() != handler) return;
            TextComponentString message = new TextComponentString(packet.message);
            message.getStyle().setColor(packet.success ? TextFormatting.GREEN : TextFormatting.RED);
            mc.player.sendMessage(message);
            if (mc.currentScreen instanceof CommandFunctionIDEScreen) {
                ((CommandFunctionIDEScreen) mc.currentScreen).result(packet);
            }
            else if (mc.currentScreen instanceof CommandBlockIDEScreen) ((CommandBlockIDEScreen) mc.currentScreen).result(packet);
        });
    }
    @Override public void receiveSuggestions(Packets.Suggestions packet, INetHandler handler) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.getConnection() == handler && mc.currentScreen instanceof CommandIDEScreen) {
                ((CommandIDEScreen<?>) mc.currentScreen).receiveSuggestions(packet);
            }
        });
    }
}
