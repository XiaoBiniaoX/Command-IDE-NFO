package arm32x.minecraft.commandblockide.mixin.client;

import arm32x.minecraft.commandblockide.client.gui.screen.CommandBlockIDEScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleUpdateTileEntity", at = @At("RETURN"))
    private void ide$update(SPacketUpdateTileEntity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof CommandBlockIDEScreen) {
            ((CommandBlockIDEScreen) mc.currentScreen).update(packet.getPos());
        }
    }
}
