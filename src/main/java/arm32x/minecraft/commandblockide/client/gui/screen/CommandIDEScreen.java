package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.client.Dirtyable;
import arm32x.minecraft.commandblockide.client.gui.ToolbarSeparator;
import arm32x.minecraft.commandblockide.client.gui.button.SimpleIconButton;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import arm32x.minecraft.commandblockide.client.update.DataCommandUpdateRequester;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

public abstract class CommandIDEScreen<E extends CommandEditor> extends Screen implements Dirtyable {
	protected final List<E> editors = new ArrayList<>();
	protected int combinedEditorHeight = Integer.MAX_VALUE;
	private boolean initialized = false;

	private SimpleIconButton saveButton;

	private int scrollOffset = 0, maxScrollOffset = Integer.MAX_VALUE;
	public static final double SCROLL_SENSITIVITY = 50.0;

	private boolean draggingScrollbar = false;
	private double mouseYAtScrollbarDragStart = 0;
	private int scrollOffsetAtScrollbarDragStart = 0;

	protected @Nullable FormattedCharSequence statusText = null;
	private int statusTextX = 0;
	private static final ClientTooltipPositioner STATUS_TEXT_POSITIONER = (screenWidth, screenHeight, x, y, width, height) -> {
		// Ignore everything else and just return (x, y).
		return new Vector2i(x, y);
	};

	public CommandIDEScreen() {
		super(Component.empty());
	}

	@Override
	protected void init() {
		statusTextX = addToolbarWidgets(List.of(
			saveButton = new SimpleIconButton(0, 0, "save", Tooltip.create(Component.translatable("commandBlockIDE.save")), b -> save()),
			new ToolbarSeparator()
		));

		// Done button
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> { if (save()) onClose(); })
			.bounds(width - 216, height - 28, 100, 20)
			.build());
		// Cancel button
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
			.bounds(width - 108, height - 28, 100, 20)
			.build());

		if (!initialized) {
			firstInit();
			initialized = true;
		} else {
			initAfterFirst();
		}
		setLoaded(editors.stream().allMatch(CommandEditor::isLoaded));
	}

	protected void firstInit() {
		setLoaded(false);

		// Make sure 'combinedEditorHeight' is set.
		repositionEditors();
		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
		// Make sure the scroll offset is in range.
		setScrollOffset(getScrollOffset());

		MultilineCommandStorage.load();
	}

	protected void initAfterFirst() {
		for (CommandEditor editor : editors) {
			addWidget(editor);
			editor.setWidth(width - 16);
		}

		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
		GuiEventListener element = getFocused();
		if (element instanceof CommandEditor) {
			setFocusedEditor((CommandEditor)element);
		}
	}

	protected void addEditor(E editor) {
		editor.setHeightChangedListener(height -> repositionEditors());
		editors.add(editor);
		addWidget(editor);
	}

	protected boolean canSave() {
		return isLoaded() && minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null;
	}

	public boolean save() {
		return MultilineCommandStorage.save();
	}

	@Override
	public void removed() {
		DataCommandUpdateRequester.getInstance().clear();
		super.removed();
	}

	@Override
	public boolean shouldCloseOnEsc() { return false; }

	@Override
	public void onClose() {
		super.onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (handleSpecialKey(keyCode)) {
			return true;
		} else if (getFocused() != null) {
			return getFocused().keyPressed(keyCode, scanCode, modifiers);
		} else {
			// Bypass the special cases for Escape and Tab added in the Screen
			// class to maintain full control over keyboard shortcuts.
			return false;
		}
	}

	private boolean handleSpecialKey(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			GuiEventListener focused = getFocused();
			if (focused == null) {
				// TODO: Warn about unsaved changes.
				onClose();
				return true;
			}
			if (focused instanceof CommandEditor editor) {
				if (editor.isSuggestorActive()) {
					editor.setSuggestorActive(false);
					return true;
				} else {
					editor.setFocused(false);
				}
			}
			setFocused(null);
			return true;
		} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			GuiEventListener focused = getFocused();
			if (focused == null) {
				if (save()) onClose();
				return true;
			}
			if (Screen.hasControlDown() && focused instanceof CommandEditor editor) {
				if (editor.isSuggestorActive()) {
					editor.setSuggestorActive(false);
					return true;
				} else {
					editor.setFocused(false);
				}
				setFocused(null);
				return true;
			}
			return false;
		} else if (keyCode == GLFW.GLFW_KEY_UP && Screen.hasControlDown() || keyCode == GLFW.GLFW_KEY_TAB && Screen.hasControlDown() && Screen.hasShiftDown()) {
			changeFocus(false);
			return true;
		} else if (keyCode == GLFW.GLFW_KEY_DOWN && Screen.hasControlDown() || keyCode == GLFW.GLFW_KEY_TAB && Screen.hasControlDown() && !Screen.hasShiftDown()) {
			changeFocus(true);
			return true;
		} else if (keyCode == GLFW.GLFW_KEY_S && Screen.hasControlDown()) {
			saveButton.playDownSound(minecraft.getSoundManager());
			save();
			return true;
		} else {
			return false;
		}
	}

	// This must be overridden because the superclass' implementation
	// short-circuits on success, which breaks text field focus.
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX > width - 4 && button == 0) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollbarPosition = Math.round((float)getScrollOffset() / height * scrollbarHeight);

			if (mouseY >= scrollbarPosition && mouseY <= scrollbarPosition + scrollbarHeight) {
				setDragging(true);
				draggingScrollbar = true;
				mouseYAtScrollbarDragStart = mouseY;
				scrollOffsetAtScrollbarDragStart = getScrollOffset();
			} else if (mouseY < scrollbarPosition) {
				setScrollOffset((int)Math.round(getScrollOffset() - SCROLL_SENSITIVITY * 5));
			} else if (mouseY > scrollbarPosition + scrollbarHeight) {
				setScrollOffset((int)Math.round(getScrollOffset() + SCROLL_SENSITIVITY * 5));
			}
			return true;
		}

		GuiEventListener focusedChild = null;
		for (GuiEventListener child : children()) {
			if (child.mouseClicked(mouseX, mouseY, button) && focusedChild == null) {
				focusedChild = child;
			}
		}
		setFocused(focusedChild);
		if (button == 0) {
			setDragging(true);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		} else {
			return super.mouseReleased(mouseX, mouseY, button);
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && draggingScrollbar) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollOffsetDelta = (int)Math.round((mouseY - mouseYAtScrollbarDragStart) / scrollbarHeight * height);
			setScrollOffset(scrollOffsetAtScrollbarDragStart + scrollOffsetDelta);
			return true;
		} else {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
		for (CommandEditor editor : editors) {
			if (editor.mouseScrolled(mouseX, mouseY, scrollX, delta)) return true;
		}

		double amount = Screen.hasShiftDown() ? 0 : delta;
		if (maxScrollOffset != 0 && amount != 0 && mouseY < height - 36 && !Screen.hasShiftDown()) {
			setScrollOffset(getScrollOffset() - (int)Math.round(amount * SCROLL_SENSITIVITY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setScrollOffset(int offset) {
		scrollOffset = Mth.clamp(offset, 0, maxScrollOffset);
		repositionEditors();
	}

	protected void repositionEditors() {
		int heightAccumulator = 8;
		for (CommandEditor editor : editors) {
			editor.setY(heightAccumulator - scrollOffset);
			heightAccumulator += editor.getHeight() + 4;
		}
		combinedEditorHeight = heightAccumulator - 12;
		// This potentially leaves the scroll offset at an out-of-range value
		// to avoid scrolling without the user intending to. The scroll offset
		// will be clamped when the user next scrolls.
		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
	}

	/**
	 * Adds the provided toolbar widgets to the screen in order.
	 * @param widgets The list of widgets to add.
	 * @return The X coordinate at which the next widget would have been placed.
	 */
	private int addToolbarWidgets(List<AbstractWidget> widgets) {
		int x = 8;
		for (AbstractWidget widget : widgets) {
			widget.setX(x);
			widget.setY(height - 28);
			x += widget.getWidth() + 4;
			addRenderableWidget(widget);
		}
		return x;
	}

	private void changeFocus(boolean lookForwards) {
		GuiEventListener element = getFocused();
		int index = editors.indexOf(element);
		for (int checked = 0; checked < editors.size(); checked++) {
			index = index < 0 ? 0 : Math.floorMod(index + (lookForwards ? 1 : -1), editors.size());
			CommandEditor editor = editors.get(index);
			if (editor.isLoaded()) {
				if (element != null) element.setFocused(false);
				setFocusedEditor(editor);
				return;
			}
		}
	}

	public void setFocusedEditor(CommandEditor editor) {
		setFocused(editor);
		editor.setFocused(true);

		// Ensure the focused editor is on-screen
		repositionEditors();
		int top = editor.getY() + scrollOffset;
		int bottom = top + editor.getHeight();
		setScrollOffset(Mth.clamp(getScrollOffset(), bottom - height + 36, top - 8));
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// Avoid this.renderBackground because it's a no-op (see below).
		super.renderBackground(context, mouseX, mouseY, delta);

		for (CommandEditor editor : editors) {
			editor.render(context, mouseX, mouseY, delta);
		}
		for (CommandEditor editor : editors) {
			// This is done in a separate loop to ensure it's rendered on top.
			editor.renderSuggestions(context, mouseX, mouseY);
		}

		if (maxScrollOffset > 0) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollbarPosition = Math.round((float)getScrollOffset() / height * scrollbarHeight);
			context.fill(width - 3, scrollbarPosition + 1, width - 1, scrollbarPosition + scrollbarHeight - 1, 0x3FFFFFFF);
		}

		var pose = context.pose();
		pose.pushPose();
		pose.translate(0.0, 0.0, 10.0);

		super.render(context, mouseX, mouseY, delta);
		if (statusText != null) {
			context.renderTooltip(font, List.of(statusText), STATUS_TEXT_POSITIONER, statusTextX + 5, height - 22);
		}

		pose.popPose();
	}

	@Override
	public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// No-op. This is a hack to prevent the background from being drawn a
		// second time from super.render.
	}

	@Override
	public boolean isDirty() {
		return editors.stream().anyMatch(Dirtyable::isDirty);
	}

	public boolean isLoaded() {
		return saveButton.active;
	}

	protected void setLoaded(boolean loaded) {
		saveButton.active = loaded;
	}
}
