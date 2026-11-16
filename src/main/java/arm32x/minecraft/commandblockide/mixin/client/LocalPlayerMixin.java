package arm32x.minecraft.commandblockide.mixin.client;

import arm32x.minecraft.commandblockide.client.gui.screen.CommandBlockIDEScreen;
import arm32x.minecraft.commandblockide.client.gui.screen.CommandIDEScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@Inject(method = "openCommandBlock", at = @At("HEAD"), cancellable = true)
	public void openCommandBlock(CommandBlockEntity commandBlock, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!Screen.hasAltDown()) {
			if (!(minecraft.screen instanceof CommandIDEScreen)) {
				minecraft.setScreen(new CommandBlockIDEScreen(commandBlock));
			}
			ci.cancel();
		}
	}
}
