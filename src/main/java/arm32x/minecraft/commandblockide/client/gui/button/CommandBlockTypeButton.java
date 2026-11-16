package arm32x.minecraft.commandblockide.client.gui.button;

import arm32x.minecraft.commandblockide.client.Dirtyable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

public final class CommandBlockTypeButton extends IconButton implements Dirtyable {
	private CommandBlockEntity.Mode type = CommandBlockEntity.Mode.REDSTONE;
	private boolean conditional = false;

	private ItemStack blockItem = new ItemStack(Blocks.COMMAND_BLOCK);

	private boolean dirty = false;

	public CommandBlockTypeButton(int x, int y) {
		super(x, y, 16, 16);
		updateTooltip();
	}

	@Override
	public void onPress() {
		if (Screen.hasShiftDown()) {
			conditional = !conditional;
		} else {
			switch (type) {
				case REDSTONE -> type = CommandBlockEntity.Mode.AUTO;
				case AUTO -> type = CommandBlockEntity.Mode.SEQUENCE;
				case SEQUENCE -> type = CommandBlockEntity.Mode.REDSTONE;
			}
			blockItem = new ItemStack(getBlock());
		}
		dirty = true;
		updateTooltip();
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return wrapDefaultNarrationMessage(getTooltipText());
	}

	private Component getTooltipText() {
		StringBuilder keyBuilder = new StringBuilder("commandBlockIDE.type.");
		keyBuilder.append(type.name().toLowerCase());
		if (conditional) {
			keyBuilder.append("Conditional");
		}
		return Component.translatable(keyBuilder.toString());
	}

	@Override
	protected ResourceLocation getTexture() {
		StringBuilder idBuilder = new StringBuilder("textures/block/");
		switch (type) {
			case REDSTONE:
				break;
			case AUTO:
				idBuilder.append("repeating_");
				break;
			case SEQUENCE:
				idBuilder.append("chain_");
				break;
		}
		idBuilder.append("command_block_");
		if (conditional) {
			idBuilder.append("conditional");
		} else {
			idBuilder.append("side");
		}
		idBuilder.append(".png");
		return new ResourceLocation("minecraft", idBuilder.toString());
	}

	/**
	 * The block that corresponds to the current type.
	 */
	private Block getBlock() {
		return switch (type) {
			case REDSTONE -> Blocks.COMMAND_BLOCK;
			case AUTO -> Blocks.REPEATING_COMMAND_BLOCK;
			case SEQUENCE -> Blocks.CHAIN_COMMAND_BLOCK;
		};
	}

	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// Render the real block (like the vanilla command block screen does)
		// instead of a flat slice of its texture. The flat texture only shows
		// the console panel, which makes it hard to tell the three command
		// block types apart at a glance.
		float brightness = active ? 1.0f : (float)0xA0 / 0xFF;
		RenderSystem.setShaderColor(brightness, brightness, brightness, alpha);
		context.renderItem(blockItem, getX(), getY());
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
	}

	@Override
	public boolean isDirty() { return dirty; }

	private void updateTooltip() {
		setTooltip(Tooltip.create(getTooltipText()));
	}

	public CommandBlockEntity.Mode getBlockType() {
		return type;
	}

	public void setBlockType(CommandBlockEntity.Mode type) {
		this.type = type;
		this.blockItem = new ItemStack(getBlock());
		updateTooltip();
	}

	public boolean isConditional() {
		return conditional;
	}

	public void setConditional(boolean conditional) {
		this.conditional = conditional;
		updateTooltip();
	}
}
