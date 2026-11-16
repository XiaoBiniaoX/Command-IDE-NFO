package arm32x.minecraft.commandblockide.client.gui;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/**
 * A minimal, self-contained container of GUI elements.
 *
 * <p>This is the 1.20.1 port of the original mod's {@code Container}, which was
 * based on Yarn's {@code AbstractParentElement}. Minecraft 1.20.1 has no
 * standalone equivalent of {@code AbstractParentElement}, so the handful of
 * behaviours the mod relied on are implemented here directly.</p>
 */
public abstract class Container implements ContainerEventHandler, Renderable, NarratableEntry {
	protected final List<GuiEventListener> children = new ArrayList<>();
	protected final List<Renderable> drawables = new ArrayList<>();

	private @Nullable GuiEventListener focused = null;
	private boolean dragging = false;

	protected <T extends GuiEventListener & Renderable & NarratableEntry> T addDrawableChild(T child) {
		drawables.add(child);
		children.add(child);
		return child;
	}

	protected <T extends Renderable> T addDrawable(T drawable) {
		drawables.add(drawable);
		return drawable;
	}

	protected <T extends GuiEventListener & NarratableEntry> T addSelectableChild(T child) {
		children.add(child);
		return child;
	}

	protected void remove(GuiEventListener child) {
		drawables.remove(child);
		children.remove(child);
		if (focused == child) {
			focused = null;
		}
	}

	protected void clearChildren() {
		drawables.clear();
		children.clear();
		focused = null;
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return children;
	}

	@Override
	public @Nullable GuiEventListener getFocused() {
		return focused;
	}

	@Override
	public void setFocused(@Nullable GuiEventListener focused) {
		this.focused = focused;
	}

	@Override
	public boolean isFocused() {
		return focused != null;
	}

	@Override
	public void setFocused(boolean focused) {
		if (this.focused != null) {
			this.focused.setFocused(focused);
		}
	}

	public boolean isDragging() {
		return dragging;
	}

	public void setDragging(boolean dragging) {
		this.dragging = dragging;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		return focused != null && focused.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return focused != null && focused.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (GuiEventListener child : children) {
			if (child.mouseClicked(mouseX, mouseY, button)) {
				setFocused(child);
				if (button == 0) {
					setDragging(true);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		setDragging(false);
		return focused != null && focused.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return focused != null && dragging && button == 0
			&& focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
		for (GuiEventListener child : children) {
			if (child.mouseScrolled(mouseX, mouseY, scrollX, delta)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		for (Renderable drawable : drawables) {
			drawable.render(context, mouseX, mouseY, delta);
		}
	}

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		NarratableEntry.NarrationPriority priority = NarratableEntry.NarrationPriority.NONE;
		for (GuiEventListener child : children) {
			if (child instanceof NarratableEntry entry) {
				NarratableEntry.NarrationPriority childPriority = entry.narrationPriority();
				if (childPriority.ordinal() > priority.ordinal()) {
					priority = childPriority;
				}
			}
		}
		return priority;
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		for (GuiEventListener child : children) {
			if (child instanceof NarratableEntry entry) {
				entry.updateNarration(output);
			}
		}
	}
}
