package arm32x.minecraft.commandblockide.client.gui.button;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public abstract class IconButton extends AbstractButton {
	protected final int iconWidth;
	protected final int iconHeight;

	public IconButton(int x, int y, int width, int height) {
		this(x, y, width, height, width, height);
	}

	public IconButton(int x, int y, int width, int height, int iconWidth, int iconHeight) {
		super(x, y, width, height, Component.empty());
		this.iconWidth = iconWidth;
		this.iconHeight = iconHeight;
	}

	@Override
	public final Component getMessage() {
		return Component.empty();
	}

	@Override
	protected abstract MutableComponent createNarrationMessage();

	protected abstract ResourceLocation getTexture();

	/**
	 * Whether the standard Minecraft button background will be rendered. This
	 * does not affect the clickable area or the tooltip.
	 */
	public boolean drawsBackground() {
		return false;
	}

	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
		boolean drawsBackground = drawsBackground();
		if (drawsBackground) {
			super.renderWidget(context, mouseX, mouseY, delta);
		}

		var texture = getTexture();

		int iconX = getX() + (width - iconWidth) / 2;
		int iconY = getY() + (height - iconHeight) / 2;

		if (drawsBackground) {
			float brightness = active ? 1.0f : (float)0xA0 / 0xFF;
			RenderSystem.setShaderColor(brightness / 4, brightness / 4, brightness / 4, alpha);
			context.blit(texture, iconX + 1, iconY + 1, 0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight);
			RenderSystem.setShaderColor(brightness, brightness, brightness, alpha);
			context.blit(texture, iconX, iconY, 0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight);
		} else {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableDepthTest();

			if (active) {
				RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, 0.25f * alpha);
				context.blit(texture, iconX + 1, iconY + 1, 0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight);
			}
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, active ? alpha : 0.5f * alpha);
			context.blit(texture, iconX, iconY, 0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight);

			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			RenderSystem.disableDepthTest();
			RenderSystem.disableBlend();
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, createNarrationMessage());
	}
}
