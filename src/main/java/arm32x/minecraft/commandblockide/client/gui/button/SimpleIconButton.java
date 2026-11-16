package arm32x.minecraft.commandblockide.client.gui.button;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class SimpleIconButton extends IconButton {
	private boolean drawsBackground;
	private ResourceLocation texture;
	private final Consumer<SimpleIconButton> pressAction;

	public SimpleIconButton(int x, int y, String iconName, Tooltip tooltip, Consumer<SimpleIconButton> pressAction) {
		this(x, y, iconName, tooltip, true, pressAction);
	}

	public SimpleIconButton(int x, int y, String iconName, Tooltip tooltip, boolean drawsBackground, Consumer<SimpleIconButton> pressAction) {
		super(x, y, 20, 20, 16, 16);
		this.drawsBackground = drawsBackground;
		this.texture = new ResourceLocation("commandblockide", "textures/gui/icons/" + iconName + ".png");
		this.pressAction = pressAction;
	}

	@Override
	public void onPress() {
		pressAction.accept(this);
	}

	@Override
	protected ResourceLocation getTexture() {
		return texture;
	}

	public void setTexture(ResourceLocation texture) {
		this.texture = texture;
	}

	@Override
	public boolean drawsBackground() {
		return drawsBackground;
	}

	public void setDrawsBackground(boolean drawsBackground) {
		this.drawsBackground = drawsBackground;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return wrapDefaultNarrationMessage(Component.empty());
	}
}
