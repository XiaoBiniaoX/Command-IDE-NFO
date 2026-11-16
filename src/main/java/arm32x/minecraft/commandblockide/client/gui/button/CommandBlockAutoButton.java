package arm32x.minecraft.commandblockide.client.gui.button;

import arm32x.minecraft.commandblockide.client.Dirtyable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class CommandBlockAutoButton extends IconButton implements Dirtyable {
	private boolean auto = false;

	private boolean dirty = false;

	public CommandBlockAutoButton(int x, int y) {
		super(x, y, 16, 16);
		setTooltip(Tooltip.create(getTooltipText()));
	}

	@Override
	protected ResourceLocation getTexture() {
		return auto
			? ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/gunpowder.png")
			: ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/redstone.png");
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return wrapDefaultNarrationMessage(getTooltipText());
	}

	private Component getTooltipText() {
		return auto
			? Component.translatable("advMode.mode.autoexec.bat")
			: Component.translatable("advMode.mode.redstoneTriggered");
	}

	@Override
	public void onPress() {
		auto = !auto;
		dirty = true;
		updateTooltip();
	}

	@Override
	public boolean isDirty() { return dirty; }

	public boolean isAuto() {
		return auto;
	}

	public void setAuto(boolean auto) {
		this.auto = auto;
		updateTooltip();
	}

	private void updateTooltip() {
		setTooltip(Tooltip.create(getTooltipText()));
	}
}
