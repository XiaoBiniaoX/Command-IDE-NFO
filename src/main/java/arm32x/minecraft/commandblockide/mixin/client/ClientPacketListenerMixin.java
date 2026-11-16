package arm32x.minecraft.commandblockide.mixin.client;

import arm32x.minecraft.commandblockide.client.gui.screen.CommandBlockIDEScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleBlockEntityData", at = @At("TAIL"))
	public void onBlockEntityData(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof CommandBlockIDEScreen screen && minecraft.level != null) {
			BlockPos pos = packet.getPos();
			BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
			if (blockEntity instanceof CommandBlockEntity) {
				screen.update(pos);
			}
		}
	}
}
