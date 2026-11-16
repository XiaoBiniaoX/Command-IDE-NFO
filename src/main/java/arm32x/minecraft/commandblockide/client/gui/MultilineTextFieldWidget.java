package arm32x.minecraft.commandblockide.client.gui;

import arm32x.minecraft.commandblockide.util.OrderedTexts;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

/**
 * A multi-line text field.
 *
 * <p>Retains the Forge port's editing engine and syntax-highlighting API.</p>
 */
public class MultilineTextFieldWidget extends AbstractWidget {
	/**
	 * The amount of time the cursor will spend being either visible or
	 * invisible before switching to the other state.
	 */
	private static final long CURSOR_BLINK_INTERVAL_MS = 300;

	// TODO: Allow the user to configure this or to indent with tabs.
	// Note that both the text field renderer and the command processor do not
	// support tabs yet.
	private static final int INDENT_SIZE = 4;

	public static final double SCROLL_SENSITIVITY = 15.0;

	private static final int DEFAULT_TEXT_COLOR = 14737632;
	private static final int DEFAULT_UNEDITABLE_TEXT_COLOR = 7368816;

	private final Font font;

	private String value = "";
	private int maxLength = 32;
	private boolean editable = true;
	private boolean bordered = true;

	private int cursorPos = 0;
	private int selectionPos = 0;

	private int textColor = DEFAULT_TEXT_COLOR;
	private int textColorUneditable = DEFAULT_UNEDITABLE_TEXT_COLOR;

	private long lastSwitchFocusTime = 0;

	private boolean horizontalScrollEnabled;
	private int horizontalScroll = 0;
	private boolean verticalScrollEnabled;
	private int verticalScroll = 0;

	private int lineHeight = 12;
	private SyntaxHighlighter syntaxHighlighter = SyntaxHighlighter.NONE;

	private @Nullable Runnable cursorChangeListener = null;
	private @Nullable Consumer<String> changedListener = null;

	/**
	 * True while the text is being changed by the code rather than by the user.
	 *
	 * <p>Used to tell "the user typed something" apart from "the editor was
	 * just filled with the contents of a command block".</p>
	 */
	private boolean programmaticChange = false;

	public MultilineTextFieldWidget(Font font, int x, int y, int width, int height, Component message, boolean horizontalScrollEnabled, boolean verticalScrollEnabled) {
		super(x, y, width, height, message);
		this.font = font;
		this.horizontalScrollEnabled = horizontalScrollEnabled;
		this.verticalScrollEnabled = verticalScrollEnabled;
	}

	public MultilineTextFieldWidget(Font font, int x, int y, int width, int height, Component message) {
		this(font, x, y, width, height, message, true, true);
	}

	public Font getFont() {
		return font;
	}

	// ------------------------------------------------------------------
	// Text state
	// ------------------------------------------------------------------

	public void setText(String text) {
		if (text == null) {
			return;
		}
		if (text.length() > maxLength) {
			text = text.substring(0, maxLength);
		}
		this.value = text;
		this.cursorPos = this.value.length();
		this.selectionPos = this.cursorPos;
		this.programmaticChange = true;
		try {
			onValueChanged();
		} finally {
			this.programmaticChange = false;
		}
	}

	/**
	 * Whether the current change came from the code instead of the user.
	 */
	public boolean isProgrammaticChange() {
		return programmaticChange;
	}

	public String getText() {
		return value;
	}

	public String getSelectedText() {
		int start = Math.min(cursorPos, selectionPos);
		int end = Math.max(cursorPos, selectionPos);
		return value.substring(start, end);
	}

	public void setChangedListener(@Nullable Consumer<String> changedListener) {
		this.changedListener = changedListener;
	}

	public void setCursorChangeListener(@Nullable Runnable cursorChangeListener) {
		this.cursorChangeListener = cursorChangeListener;
	}

	private void onValueChanged() {
		if (changedListener != null) {
			changedListener.accept(value);
		}
	}

	private void onCursorChanged() {
		if (cursorChangeListener != null) {
			cursorChangeListener.run();
		}
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
		if (value.length() > maxLength) {
			value = value.substring(0, maxLength);
			cursorPos = Math.min(cursorPos, value.length());
			selectionPos = Math.min(selectionPos, value.length());
			onValueChanged();
		}
	}

	public int getMaxLength() {
		return maxLength;
	}

	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	public boolean isEditable() {
		return editable;
	}

	public void setBordered(boolean bordered) {
		this.bordered = bordered;
	}

	public boolean isBordered() {
		return bordered;
	}

	public void setTextColor(int textColor) {
		this.textColor = textColor;
	}

	public void setTextColorUneditable(int textColorUneditable) {
		this.textColorUneditable = textColorUneditable;
	}

	// ------------------------------------------------------------------
	// Cursor and selection
	// ------------------------------------------------------------------

	public int getCursor() {
		return cursorPos;
	}

	public void setCursor(int cursor, boolean hasShiftDown) {
		this.cursorPos = Mth.clamp(cursor, 0, value.length());
		if (!hasShiftDown) {
			this.selectionPos = this.cursorPos;
		}
		scrollToEnsureCursorVisible();
		onCursorChanged();
	}

	public void setSelectionStart(int cursor) {
		setCursor(cursor, true);
	}

	public void setSelectionEnd(int index) {
		this.selectionPos = Mth.clamp(index, 0, value.length());
		onCursorChanged();
	}

	public boolean hasSelection() {
		return cursorPos != selectionPos;
	}

	public int getSelectionStart() {
		return Math.min(cursorPos, selectionPos);
	}

	public int getSelectionEnd() {
		return Math.max(cursorPos, selectionPos);
	}

	public void moveCursor(int offset, boolean hasShiftDown) {
		setCursor(cursorPos + offset, hasShiftDown);
	}

	public void moveCursorToStart() {
		setCursor(0, false);
	}

	public void moveCursorToEnd() {
		setCursor(value.length(), false);
	}

	/**
	 * Replaces the current selection (or inserts at the cursor) with the given
	 * text. An empty string deletes the selection.
	 */
	public void replaceSelection(String text) {
		if (text == null) {
			return;
		}
		int start = Math.min(cursorPos, selectionPos);
		int end = Math.max(cursorPos, selectionPos);
		if (text.isEmpty()) {
			if (start == end) {
				return;
			}
			value = value.substring(0, start) + value.substring(end);
			setCursor(start, false);
			onValueChanged();
			return;
		}
		int allowed = maxLength - value.length() + (end - start);
		if (allowed <= 0) {
			return;
		}
		String filtered = StringUtil.filterText(text, true);
		if (filtered.length() > allowed) {
			filtered = filtered.substring(0, allowed);
		}
		value = value.substring(0, start) + filtered + value.substring(end);
		setCursor(start + filtered.length(), false);
		onValueChanged();
	}

	/**
	 * Deletes {@code characterOffset} characters relative to the cursor.
	 */
	public void delete(int characterOffset) {
		if (value.isEmpty()) {
			return;
		}
		if (hasSelection()) {
			replaceSelection("");
			return;
		}
		int target = cursorPos + characterOffset;
		if (target < 0 || target > value.length()) {
			return;
		}
		int start = Math.min(cursorPos, target);
		int end = Math.max(cursorPos, target);
		value = value.substring(0, start) + value.substring(end);
		setCursor(start, false);
		onValueChanged();
	}

	// ------------------------------------------------------------------
	// Lines
	// ------------------------------------------------------------------

	public int getLineCount() {
		int count = 1;
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) == '\n') {
				count++;
			}
		}
		return count;
	}

	public int getCurrentLineIndex() {
		return getLineIndex(cursorPos);
	}

	public int getLineIndex(int charIndex) {
		charIndex = Mth.clamp(charIndex, 0, value.length());
		int line = 0;
		for (int i = 0; i < charIndex; i++) {
			if (value.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	public int getLineStartBefore(int charIndex) {
		return value.lastIndexOf('\n', Math.max(charIndex, 0) - 1) + 1;
	}

	public int getLineEndAfter(int charIndex) {
		int index = value.indexOf('\n', Math.max(charIndex, 0));
		return index == -1 ? value.length() : index;
	}

	public String getLine(int lineIndex) {
		Line line = getLineRange(lineIndex);
		return value.substring(line.beginIndex(), line.endIndex());
	}

	public Line getLineRange(int lineIndex) {
		int index = 0;
		int start = 0;
		while (index < lineIndex) {
			int next = value.indexOf('\n', start);
			if (next == -1) {
				break;
			}
			start = next + 1;
			index++;
		}
		return new Line(start, getLineEndAfter(start));
	}

	// Naming things is hard.
	public boolean isBeforeFirstNonWhitespaceCharacterInLine(int charIndex) {
		return value
			.substring(getLineStartBefore(charIndex), Math.min(charIndex, value.length()))
			.chars()
			.allMatch(Character::isWhitespace);
	}

	public int getLineHeight() {
		return lineHeight;
	}

	public void setLineHeight(int lineHeight) {
		this.lineHeight = lineHeight;
	}

	// ------------------------------------------------------------------
	// Scrolling
	// ------------------------------------------------------------------

	protected int getHorizontalScroll() {
		return horizontalScroll;
	}

	protected int getMaxHorizontalScroll() {
		return Math.max(0, Arrays.stream(value.split("\n"))
			.mapToInt(font::width)
			.max()
			.orElse(0) + 8 - width);
	}

	protected boolean setHorizontalScroll(int horizontalScroll) {
		int previous = this.horizontalScroll;
		this.horizontalScroll = Mth.clamp(horizontalScroll, 0, getMaxHorizontalScroll());
		return this.horizontalScroll != previous;
	}

	protected int getVerticalScroll() {
		return verticalScroll;
	}

	protected int getMaxVerticalScroll() {
		return Math.max(0, getLineCount() * getLineHeight() + 2 - height);
	}

	protected boolean setVerticalScroll(int verticalScroll) {
		int previous = this.verticalScroll;
		this.verticalScroll = Mth.clamp(verticalScroll, 0, getMaxVerticalScroll());
		return this.verticalScroll != previous;
	}

	public boolean isHorizontalScrollEnabled() {
		return horizontalScrollEnabled;
	}

	public void setHorizontalScrollEnabled(boolean enabled) {
		horizontalScrollEnabled = enabled;
		horizontalScroll = 0;
	}

	public boolean isVerticalScrollEnabled() {
		return verticalScrollEnabled;
	}

	public void setVerticalScrollEnabled(boolean enabled) {
		verticalScrollEnabled = enabled;
		verticalScroll = 0;
	}

	protected void scrollToEnsureCursorVisible() {
		int virtualX = getCharacterVirtualX(getCursor());
		int virtualY = getCharacterVirtualY(getCursor());

		setHorizontalScroll(Mth.clamp(horizontalScroll, virtualX - getInnerWidth(), virtualX));
		setVerticalScroll(Mth.clamp(verticalScroll, virtualY - getInnerHeight(), virtualY));
	}

	// ------------------------------------------------------------------
	// Geometry
	// ------------------------------------------------------------------

	public int getCharacterVirtualX(int charIndex) {
		if (charIndex > value.length()) {
			return 0;
		}
		String line = getLine(getLineIndex(charIndex));

		int indexInLine = charIndex - getLineStartBefore(charIndex);
		if (indexInLine > line.length()) {
			indexInLine = line.length();
		}

		return font.width(line.substring(0, indexInLine));
	}

	public int getCharacterRealX(int charIndex) {
		return getInnerX() - horizontalScroll + getCharacterVirtualX(charIndex);
	}

	/**
	 * Gets the desired X position of the command suggestion window.
	 *
	 * <p>This method exists because the suggestor asks the text field where the
	 * cursor is on screen.</p>
	 *
	 * @param charIndex The index of the character to place the suggestion
	 *                  window at.
	 * @return The desired X position of the suggestion window.
	 */
	public int getCharacterX(int charIndex) {
		// Since getInnerX isn't a method in the original text field widget,
		// the suggestor calls getCharacterX(0) instead.
		if (charIndex == 0) {
			return getInnerX();
		}
		// Enforce a lower bound on position. The suggestor will enforce the
		// upper bound using getInnerWidth().
		return Math.max(getCharacterRealX(charIndex), getInnerX());
	}

	public int getCharacterVirtualY(int charIndex) {
		if (charIndex > value.length()) {
			charIndex = value.length();
		}
		int lineIndex = getLineIndex(charIndex);

		return lineIndex * getLineHeight();
	}

	public int getCharacterRealY(int charIndex) {
		return getInnerY() - verticalScroll + getCharacterVirtualY(charIndex);
	}

	public int getInnerX() {
		return this.getX() + (bordered ? 4 : 0);
	}

	public int getInnerY() {
		return this.getY() + (bordered ? 4 : 0);
	}

	public int getInnerWidth() {
		return bordered ? this.width - 8 : this.width;
	}

	public int getInnerHeight() {
		return bordered ? this.height - 6 : this.height;
	}

	// ------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (focused) {
			this.lastSwitchFocusTime = Util.getMillis();
		}
	}

	public boolean isVisible() {
		return this.visible;
	}

	public boolean canConsumeInput() {
		return this.isVisible() && this.isFocused() && this.editable;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.visible
			&& mouseX >= (double)this.getX() && mouseY >= (double)this.getY()
			&& mouseX < (double)(this.getX() + this.width) && mouseY < (double)(this.getY() + this.height);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!this.canConsumeInput()) {
			return false;
		}
		boolean shift = Screen.hasShiftDown();
		boolean control = Screen.hasControlDown();

		if (keyCode == GLFW.GLFW_KEY_TAB) {
			if (hasSelection()) {
				return true;
			}
			int cursorLeft = getCursor() - getLineStartBefore(getCursor());
			String indent = " ".repeat(INDENT_SIZE - cursorLeft % INDENT_SIZE);
			replaceSelection(indent);
			return true;
		}

		if (Screen.isSelectAll(keyCode)) {
			moveCursorToEnd();
			setSelectionStart(0);
			return true;
		}
		if (Screen.isCopy(keyCode)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
			return true;
		}
		if (Screen.isPaste(keyCode)) {
			replaceSelection(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (Screen.isCut(keyCode)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
			replaceSelection("");
			return true;
		}

		switch (keyCode) {
			case GLFW.GLFW_KEY_BACKSPACE:
				if (control) {
					deleteWords(-1);
				} else {
					delete(-1);
				}
				return true;
			case GLFW.GLFW_KEY_DELETE:
				if (control) {
					deleteWords(1);
				} else {
					delete(1);
				}
				return true;
			case GLFW.GLFW_KEY_LEFT:
				if (control) {
					setCursor(getWordPosition(-1), shift);
				} else {
					setCursor(cursorPos - 1, shift);
				}
				return true;
			case GLFW.GLFW_KEY_RIGHT:
				if (control) {
					setCursor(getWordPosition(1), shift);
				} else {
					setCursor(cursorPos + 1, shift);
				}
				return true;
			case GLFW.GLFW_KEY_HOME:
				setCursor(getLineStartBefore(cursorPos), shift);
				return true;
			case GLFW.GLFW_KEY_END:
				setCursor(getLineEndAfter(cursorPos), shift);
				return true;
			case GLFW.GLFW_KEY_UP:
				setCursor(moveCursorVertically(-1), shift);
				return true;
			case GLFW.GLFW_KEY_DOWN:
				setCursor(moveCursorVertically(1), shift);
				return true;
			case GLFW.GLFW_KEY_ENTER:
			case GLFW.GLFW_KEY_KP_ENTER:
				replaceSelection("\n");
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (!this.canConsumeInput()) {
			return false;
		}
		if (StringUtil.isAllowedChatCharacter(chr)) {
			replaceSelection(String.valueOf(chr));
			return true;
		}
		return false;
	}

	private void deleteWords(int wordOffset) {
		if (value.isEmpty()) {
			return;
		}
		if (hasSelection()) {
			replaceSelection("");
			return;
		}
		int target = getWordPosition(wordOffset);
		int start = Math.min(cursorPos, target);
		int end = Math.max(cursorPos, target);
		value = value.substring(0, start) + value.substring(end);
		setCursor(start, false);
		onValueChanged();
	}

	private int getWordPosition(int wordOffset) {
		int position = cursorPos;
		if (wordOffset < 0) {
			while (position > 0 && value.charAt(position - 1) == ' ') {
				position--;
			}
			while (position > 0 && value.charAt(position - 1) != ' ') {
				position--;
			}
		} else {
			int length = value.length();
			while (position < length && value.charAt(position) == ' ') {
				position++;
			}
			while (position < length && value.charAt(position) != ' ') {
				position++;
			}
		}
		return position;
	}

	private int moveCursorVertically(int lines) {
		int currentLine = getLineIndex(cursorPos);
		int targetLine = Mth.clamp(currentLine + lines, 0, getLineCount() - 1);
		if (targetLine == currentLine) {
			return cursorPos;
		}
		int column = cursorPos - getLineStartBefore(cursorPos);
		Line target = getLineRange(targetLine);
		return Mth.clamp(target.beginIndex() + column, target.beginIndex(), target.endIndex());
	}

	@Override
public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.isVisible()) {
			return false;
		}
		if (button == 0 && isMouseOver(mouseX, mouseY)) {
			setFocused(true);
			moveCursor(mouseX, mouseY, Screen.hasShiftDown());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (!this.isVisible()) {
			return false;
		}
		if (isFocused() && isMouseOver(mouseX, mouseY) && button == 0) {
			moveCursor(mouseX, mouseY, true);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
		if (isMouseOver(mouseX, mouseY)) {
			double horizontal = Screen.hasShiftDown() ? delta : scrollX;
			double vertical = Screen.hasShiftDown() ? 0 : delta;
			boolean changed = horizontalScrollEnabled && setHorizontalScroll(getHorizontalScroll() - (int)Math.round(horizontal * SCROLL_SENSITIVITY));
			changed |= verticalScrollEnabled && setVerticalScroll(getVerticalScroll() - (int)Math.round(vertical * SCROLL_SENSITIVITY));

			// This updates the position of the suggestions window.
			onCursorChanged();
			return changed;
		}
		return false;
	}

	private void moveCursor(double mouseX, double mouseY, boolean extendSelection) {
		double virtualX = mouseX - getInnerX() + getHorizontalScroll();
		double virtualY = mouseY - getInnerY() + getVerticalScroll();

		int lineIndex = Mth.floor(virtualY / getLineHeight());

		// Get a rough estimate of where the cursor should be.
		Line line = getLineRange(lineIndex);
		String lineText = value.substring(line.beginIndex(), line.endIndex());
		int charIndexInLine = font.plainSubstrByWidth(lineText, Mth.floor(virtualX)).length();
		int charIndex = line.beginIndex() + charIndexInLine;

		// Refine the estimate by determining the nearest character boundary.
		double leftCharacterXDistance = Math.abs(getCharacterVirtualX(charIndex) - virtualX);
		double rightCharacterXDistance = Math.abs(getCharacterVirtualX(charIndex + 1) - virtualX);
		if (rightCharacterXDistance < leftCharacterXDistance) {
			charIndex++;
		}

		setCursor(charIndex, extendSelection);
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
		if (!isVisible()) {
			return;
		}

		if (bordered) {
			int borderColor = isFocused() ? -1 : -6250336;
			context.fill(getX() - 1, getY() - 1, getX() + this.width + 1, getY() + this.height + 1, borderColor);
			context.fill(getX(), getY(), getX() + this.width, getY() + this.height, -16777216);
		}

		context.enableScissor(
			getX() + 1, getY() + 1,
			getX() + this.width - 1, getY() + this.height - 1
		);

		int renderColor = editable ? this.textColor : this.textColorUneditable;
		int x = getInnerX() - horizontalScroll;
		int y = getInnerY() - verticalScroll;

		long timeSinceLastSwitchFocusMs = Util.getMillis() - lastSwitchFocusTime;
		boolean showCursor = isFocused() && timeSinceLastSwitchFocusMs / CURSOR_BLINK_INTERVAL_MS % 2 == 0;
		boolean lineCursor = getCursor() < value.length() || value.length() >= getMaxLength();

		int cursorLine = getCurrentLineIndex();
		int cursorX = x;
		int cursorY = y + lineHeight * cursorLine;

		// This assumes that the highlighter returns the same characters as the
		// original text, which is not enforced by the API.
		List<FormattedCharSequence> lines = syntaxHighlighter.highlight(value);
		for (int index = 0; index < lines.size(); index++) {
			FormattedCharSequence line = lines.get(index);
			if (index == cursorLine) {
				int indexOfLastNewlineBeforeCursor = getLineStartBefore(getCursor()) - 1;
				int codePointsBeforeCursor;
				if (indexOfLastNewlineBeforeCursor != -1) {
					codePointsBeforeCursor = value.codePointCount(indexOfLastNewlineBeforeCursor, Math.max(getCursor() - 1, 0));
				} else {
					codePointsBeforeCursor = value.codePointCount(0, getCursor());
				}
				int endX = context.drawString(font, OrderedTexts.limit(codePointsBeforeCursor, line), x, y + lineHeight * index, renderColor, true) - 1;
				context.drawString(font, OrderedTexts.skip(codePointsBeforeCursor, line), endX, y + lineHeight * index, renderColor, true);
				cursorX = endX - 1;
			} else {
				context.drawString(font, line, x, y + lineHeight * index, renderColor, true);
			}
		}

		if (showCursor) {
			if (lineCursor) {
				context.fill(RenderType.guiOverlay(), cursorX, cursorY - 1, cursorX + 1, cursorY + 10, 0xFFD0D0D0);
			} else {
				context.drawString(font, "_", cursorX, cursorY, renderColor, true);
			}
		}

		if (isFocused() && hasSelection()) {
			renderSelection(context, x, y);
		}

		context.disableScissor();
	}

	private void renderSelection(GuiGraphics context, int x, int y) {
		int normalizedSelectionStart = getSelectionStart();
		int normalizedSelectionEnd = getSelectionEnd();

		int startX = x + font.width(value.substring(getLineStartBefore(normalizedSelectionStart), normalizedSelectionStart)) - 1;
		int startY = y + lineHeight * getLineIndex(normalizedSelectionStart) - 1;
		int endX = x + font.width(value.substring(getLineStartBefore(normalizedSelectionEnd), normalizedSelectionEnd)) - 1;
		int endY = y + lineHeight * getLineIndex(normalizedSelectionEnd) - 1;

		int leftEdge = getInnerX();
		int rightEdge = leftEdge + getInnerWidth();

		final int color = 0xFF0000FF;

		if (startY == endY) {
			// Selection spans one line
			context.fill(RenderType.guiTextHighlight(), endX, startY, startX, endY + lineHeight - 1, color);
		} else {
			// Selection spans two or more lines
			context.fill(RenderType.guiTextHighlight(), rightEdge, startY, startX, startY + lineHeight, color);

			if (!(startY - lineHeight == endY || endY - lineHeight == startY)) {
				// Selection spans three or more lines
				context.fill(RenderType.guiTextHighlight(), rightEdge, startY + lineHeight, leftEdge, endY, color);
			}

			context.fill(RenderType.guiTextHighlight(), endX, endY, leftEdge, endY + lineHeight - 1, color);
		}
	}

	// ------------------------------------------------------------------
	// Syntax highlighting
	// ------------------------------------------------------------------

	public SyntaxHighlighter getSyntaxHighlighter() {
		return syntaxHighlighter;
	}

	public void setSyntaxHighlighter(SyntaxHighlighter syntaxHighlighter) {
		this.syntaxHighlighter = syntaxHighlighter;
	}

	// ------------------------------------------------------------------
	// Narration
	// ------------------------------------------------------------------

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		return isFocused() ? NarratableEntry.NarrationPriority.FOCUSED : NarratableEntry.NarrationPriority.NONE;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return Component.translatable("gui.narrate.editBox", getMessage(), value);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, createNarrationMessage());
	}

	/**
	 * A range of characters within the text, corresponding to a single line.
	 */
	public record Line(int beginIndex, int endIndex) { }

	@FunctionalInterface
	public interface SyntaxHighlighter {
		/**
		 * A syntax highlighter that performs no highlighting.
		 */
		SyntaxHighlighter NONE = text -> Arrays.stream(text.split("\n"))
			.map(line -> FormattedCharSequence.forward(line, Style.EMPTY))
			.toList();

		List<FormattedCharSequence> highlight(String text);
	}
}
