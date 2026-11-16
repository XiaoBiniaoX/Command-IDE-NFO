package arm32x.minecraft.commandblockide.mixin.client;

import arm32x.minecraft.commandblockide.client.update.DataCommandUpdateRequester;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
	@Inject(
		method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
		at = @At("HEAD"),
		cancellable = true
	)
	public void onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
		if (message.getContents() instanceof TranslatableContents content
			&& content.getKey().equals("commands.data.block.query"))
		{
			boolean handled = DataCommandUpdateRequester.getInstance().handleFeedback(Minecraft.getInstance(), content);
			if (handled) {
				ci.cancel();
			}
		}
	}
}
