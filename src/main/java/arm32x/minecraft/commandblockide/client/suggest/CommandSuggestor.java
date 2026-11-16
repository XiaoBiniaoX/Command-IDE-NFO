package arm32x.minecraft.commandblockide.client.suggest;

import arm32x.minecraft.commandblockide.client.gui.MultilineTextFieldWidget;
import arm32x.minecraft.commandblockide.client.processor.CommandProcessor;
import arm32x.minecraft.commandblockide.client.processor.StringMapping;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * The command suggestion window.
 *
 * <p>In 1.20.4 the original mod used Minecraft's {@code ChatInputSuggestor}, a
 * class that had been refactored away from the chat screen and therefore could
 * be pointed at an arbitrary text field. Minecraft 1.20.1 has no such class:
 * the closest equivalent is {@code CommandSuggestions}, which is written
 * against the single-line {@code EditBox}.</p>
 *
 * <p>This class is a port of the 1.20.1 {@code CommandSuggestions} that works
 * with the mod's own multi-line text field and understands the mapping between
 * a multiline command and the single-line command Minecraft actually parses.</p>
 */
public final class CommandSuggestor {
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");
	private static final Style UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
	private static final Style LITERAL_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
	private static final List<Style> ARGUMENT_STYLES = Stream.of(
		ChatFormatting.AQUA,
		ChatFormatting.YELLOW,
		ChatFormatting.GREEN,
		ChatFormatting.LIGHT_PURPLE,
		ChatFormatting.GOLD
	).map(Style.EMPTY::withColor).collect(ImmutableList.toImmutableList());

	/** Vertical offset of the suggestion window relative to the cursor. */
	private static final int SUGGESTOR_Y_OFFSET = 9;

	private final Minecraft minecraft;
	private final Screen screen;
	private final MultilineTextFieldWidget input;
	private final Font font;
	private final boolean commandsOnly;
	private final boolean onlyShowIfCursorPastError;
	private final int lineStartOffset;
	private final int suggestionLineLimit;
	private final boolean anchorToBottom;
	private final int fillColor;

	private final List<FormattedCharSequence> commandUsage = Lists.newArrayList();
	private int commandUsagePosition;
	private int commandUsageWidth;

	@Nullable
	private ParseResults<SharedSuggestionProvider> currentParse;
	@Nullable
	private CompletableFuture<Suggestions> pendingSuggestions;
	@Nullable
	private SuggestionsList suggestions;

	private boolean allowSuggestions;
	boolean keepSuggestions;

	/**
	 * Whether this editor is allowed to show a suggestion window at all.
	 *
	 * <p>Only the editor that currently has the keyboard focus may show
	 * suggestions. Without this gate every editor of the screen would render
	 * its own (very long) list of suggestions and they would all overlap
	 * each other.</p>
	 */
	private boolean windowActive = false;

	/**
	 * Whether the suggestion window should pop up as soon as the suggestions
	 * have been computed. Only enabled while the user is actually typing.
	 */
	private boolean autoShow = false;

	// Command Block IDE extensions.
	private boolean ide$allowComments = false;
	private boolean ide$slashForbidden = false;
	@Nullable
	private CommandProcessor ide$commandProcessor = null;
	@Nullable
	private StringMapping ide$mapping = null;
	private String ide$singleLine = "";

	public CommandSuggestor(Minecraft minecraft, Screen screen, MultilineTextFieldWidget input, Font font,
						   boolean commandsOnly, boolean onlyShowIfCursorPastError, int lineStartOffset,
						   int suggestionLineLimit, boolean anchorToBottom, int fillColor) {
		this.minecraft = minecraft;
		this.screen = screen;
		this.input = input;
		this.font = font;
		this.commandsOnly = commandsOnly;
		this.onlyShowIfCursorPastError = onlyShowIfCursorPastError;
		this.lineStartOffset = lineStartOffset;
		this.suggestionLineLimit = suggestionLineLimit;
		this.anchorToBottom = anchorToBottom;
		this.fillColor = fillColor;
	}

	// ------------------------------------------------------------------
	// Command Block IDE extensions
	// ------------------------------------------------------------------

	public void ide$setAllowComments(boolean allowComments) {
		this.ide$allowComments = allowComments;
	}

	public boolean ide$getAllowComments() {
		return ide$allowComments;
	}

	public void ide$setSlashForbidden(boolean slashForbidden) {
		this.ide$slashForbidden = slashForbidden;
	}

	public void ide$setCommandProcessor(@Nullable CommandProcessor processor) {
		this.ide$commandProcessor = processor;
	}

	@Nullable
	public CommandProcessor ide$getCommandProcessor() {
		return ide$commandProcessor;
	}

	@Nullable
	public StringMapping ide$getMapping() {
		return ide$mapping;
	}

	@Nullable
	public ParseResults<SharedSuggestionProvider> getParse() {
		return currentParse;
	}

	/** The processed (single-line) version of the text currently in the field. */
	public String getSingleLineCommand() {
		return ide$singleLine;
	}

	// ------------------------------------------------------------------
	// Suggestion handling
	// ------------------------------------------------------------------

	public void setAllowSuggestions(boolean allowSuggestions) {
		this.allowSuggestions = allowSuggestions;
		if (!allowSuggestions) {
			this.suggestions = null;
		}
	}

	/**
	 * Whether the suggestion window is allowed to be shown.
	 *
	 * <p>This is the 1.20.1 equivalent of the 1.20.4
	 * {@code ChatInputSuggestor#setWindowActive}.</p>
	 */
	public void setWindowActive(boolean active) {
		this.windowActive = active;
		if (!active) {
			this.suggestions = null;
			this.autoShow = false;
		}
	}

	/**
	 * Whether the window should pop up automatically once the suggestions have
	 * been computed (i.e. while the user is typing).
	 */
	public void setAutoShow(boolean autoShow) {
		this.autoShow = autoShow;
	}

	public boolean isWindowActive() {
		return windowActive;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.suggestions != null && this.suggestions.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		} else if (input.isFocused() && keyCode == 258 /* GLFW_KEY_TAB */) {
			this.showSuggestions(true);
			return true;
		} else {
			return false;
		}
	}

	public boolean mouseScrolled(double amount) {
		return this.suggestions != null && this.suggestions.mouseScrolled(Mth.clamp(amount, -1.0D, 1.0D));
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return this.suggestions != null && this.suggestions.mouseClicked((int)mouseX, (int)mouseY, button);
	}

	public void showSuggestions(boolean showTabCompletion) {
		if (!this.windowActive) {
			// Only the focused editor may show a suggestion window.
			return;
		}
		if (this.pendingSuggestions != null && this.pendingSuggestions.isDone()) {
			Suggestions suggestions = this.pendingSuggestions.join();
			if (!suggestions.isEmpty()) {
				int i = 0;

				for (Suggestion suggestion : suggestions.getList()) {
					i = Math.max(i, this.font.width(suggestion.getText()));
				}

				int startIndex = mapToMultiline(suggestions.getRange().getStart());
				int j = Mth.clamp(input.getCharacterX(startIndex), 0, input.getCharacterX(0) + input.getInnerWidth() - i);
				int k = this.anchorToBottom ? this.screen.height - 12 : getSuggestorY();
				this.suggestions = new SuggestionsList(j, k, i, this.sortSuggestions(suggestions), showTabCompletion);
			}
		}
	}

	public void hide() {
		this.suggestions = null;
	}

	/**
	 * The Y coordinate at which the suggestion window should be drawn.
	 */
	private int getSuggestorY() {
		int charIndex = input.getText().length();
		if (this.pendingSuggestions != null) {
			Suggestions suggestions = this.pendingSuggestions.getNow(null);
			if (suggestions != null && !suggestions.isEmpty()) {
				charIndex = mapToMultiline(suggestions.getRange().getStart());
			}
		}
		return input.getCharacterRealY(charIndex) + SUGGESTOR_Y_OFFSET;
	}

	private List<Suggestion> sortSuggestions(Suggestions suggestions) {
		String s = ide$singleLine.substring(0, Math.min(getSingleLineCursor(), ide$singleLine.length()));
		int i = getLastWordIndex(s);
		String s1 = s.substring(i).toLowerCase(Locale.ROOT);
		List<Suggestion> list = Lists.newArrayList();
		List<Suggestion> list1 = Lists.newArrayList();

		for (Suggestion suggestion : suggestions.getList()) {
			if (!suggestion.getText().startsWith(s1) && !suggestion.getText().startsWith("minecraft:" + s1)) {
				list1.add(suggestion);
			} else {
				list.add(suggestion);
			}
		}

		list.addAll(list1);
		return list;
	}

	public void updateCommandInfo() {
		String raw = input.getText();
		if (ide$commandProcessor != null) {
			var processed = ide$commandProcessor.processCommand(raw);
			ide$mapping = processed.getRight();
			ide$singleLine = processed.getLeft();
		} else {
			ide$mapping = null;
			ide$singleLine = raw;
		}

		if (currentParse != null && !currentParse.getReader().getString().equals(ide$singleLine)) {
			currentParse = null;
		}

		if (!keepSuggestions) {
			this.suggestions = null;
		}

		this.commandUsage.clear();

		if (ide$allowComments && ide$singleLine.startsWith("#")) {
			// The command is a comment; there is nothing to suggest.
			this.pendingSuggestions = null;
			return;
		}
		if (ide$mapping != null && ide$mapping.inverted().mapIndex(input.getCursor()).isEmpty()) {
			// The cursor is inside a region that was removed while merging the
			// multiline command into a single line.
			this.pendingSuggestions = null;
			return;
		}

		StringReader stringreader = new StringReader(ide$singleLine);
		boolean flag = !ide$slashForbidden && stringreader.canRead() && stringreader.peek() == '/';
		if (flag) {
			stringreader.skip();
		}

		boolean flag1 = this.commandsOnly || flag;
		int i = getSingleLineCursor();
		if (flag1) {
			if (minecraft.player == null || minecraft.player.connection == null) {
				return;
			}
			CommandDispatcher<SharedSuggestionProvider> commanddispatcher = minecraft.player.connection.getCommands();
			if (this.currentParse == null) {
				this.currentParse = commanddispatcher.parse(stringreader, minecraft.player.connection.getSuggestionsProvider());
			}

			int j = this.onlyShowIfCursorPastError ? stringreader.getCursor() : 1;
			if (i >= j && (this.suggestions == null || !this.keepSuggestions)) {
				var pending = commanddispatcher.getCompletionSuggestions(this.currentParse, i);
				this.pendingSuggestions = pending;
				pending.thenRun(() -> minecraft.execute(() -> {
					if (this.pendingSuggestions == pending && this.currentParse != null
						&& minecraft.screen == screen && minecraft.player != null) {
						this.updateUsageInfo();
					}
				}));
			}
		} else {
			String s1 = ide$singleLine.substring(0, Math.min(i, ide$singleLine.length()));
			int k = getLastWordIndex(s1);
			if (minecraft.player == null || minecraft.player.connection == null) {
				return;
			}
			Collection<String> collection = minecraft.player.connection.getSuggestionsProvider().getCustomTabSugggestions();
			this.pendingSuggestions = SharedSuggestionProvider.suggest(collection, new SuggestionsBuilder(s1, k));
		}
	}

	/**
	 * The cursor position in the processed (single-line) command.
	 */
	private int getSingleLineCursor() {
		return StringMapping.mapIndexOrAfter(ide$mapping, true, input.getCursor());
	}

	/**
	 * Maps an index in the processed (single-line) command back to an index in
	 * the multiline text.
	 */
	private int mapToMultiline(int singleLineIndex) {
		return StringMapping.mapIndexOrAfter(ide$mapping, false, singleLineIndex);
	}

	private static int getLastWordIndex(String text) {
		if (Strings.isNullOrEmpty(text)) {
			return 0;
		} else {
			int i = 0;

			for (Matcher matcher = WHITESPACE_PATTERN.matcher(text); matcher.find(); i = matcher.end()) {
			}

			return i;
		}
	}

	private static FormattedCharSequence getExceptionMessage(CommandSyntaxException exception) {
		Component component = ComponentUtils.fromMessage(exception.getRawMessage());
		String s = exception.getContext();
		return s == null
			? component.getVisualOrderText()
			: Component.translatable("command.context.parse_error", component, exception.getCursor(), s).getVisualOrderText();
	}

	private void updateUsageInfo() {
		boolean flag = false;
		if (getSingleLineCursor() == ide$singleLine.length()) {
			if (this.pendingSuggestions.join().isEmpty() && !this.currentParse.getExceptions().isEmpty()) {
				int i = 0;

				for (Map.Entry<CommandNode<SharedSuggestionProvider>, CommandSyntaxException> entry : this.currentParse.getExceptions().entrySet()) {
					CommandSyntaxException commandsyntaxexception = entry.getValue();
					if (commandsyntaxexception.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()) {
						++i;
					} else {
						this.commandUsage.add(getExceptionMessage(commandsyntaxexception));
					}
				}

				if (i > 0) {
					this.commandUsage.add(getExceptionMessage(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()));
				}
			} else if (this.currentParse.getReader().canRead()) {
				flag = true;
			}
		}

		this.commandUsagePosition = 0;
		this.commandUsageWidth = this.screen.width;
		if (this.commandUsage.isEmpty() && !this.fillNodeUsage(ChatFormatting.GRAY) && flag) {
			this.commandUsage.add(getExceptionMessage(Commands.getParseException(this.currentParse)));
		}

		this.suggestions = null;
		if (this.windowActive && this.autoShow && minecraft.options.autoSuggestions().get()) {
			this.showSuggestions(false);
		}
	}

	private boolean fillNodeUsage(ChatFormatting color) {
		CommandContextBuilder<SharedSuggestionProvider> commandcontextbuilder = this.currentParse.getContext();
		SuggestionContext<SharedSuggestionProvider> suggestioncontext = commandcontextbuilder.findSuggestionContext(getSingleLineCursor());
		Map<CommandNode<SharedSuggestionProvider>, String> map = minecraft.player.connection.getCommands()
			.getSmartUsage(suggestioncontext.parent, minecraft.player.connection.getSuggestionsProvider());
		List<FormattedCharSequence> list = Lists.newArrayList();
		int i = 0;
		Style style = Style.EMPTY.withColor(color);

		for (Map.Entry<CommandNode<SharedSuggestionProvider>, String> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof LiteralCommandNode)) {
				list.add(FormattedCharSequence.forward(entry.getValue(), style));
				i = Math.max(i, this.font.width(entry.getValue()));
			}
		}

		if (!list.isEmpty()) {
			this.commandUsage.addAll(list);
			this.commandUsagePosition = Mth.clamp(
				input.getCharacterX(mapToMultiline(suggestioncontext.startPos)),
				0,
				input.getCharacterX(0) + input.getInnerWidth() - i
			);
			this.commandUsageWidth = i;
			return true;
		} else {
			return false;
		}
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	public void render(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!this.windowActive) {
			// Only the focused editor may show a suggestion window.
			return;
		}
		if (!this.renderSuggestions(graphics, mouseX, mouseY)) {
			this.renderUsage(graphics);
		}
	}

	public boolean renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.suggestions != null) {
			this.suggestions.render(graphics, mouseX, mouseY);
			return true;
		} else {
			return false;
		}
	}

	public void renderUsage(GuiGraphics graphics) {
		int i = 0;

		for (FormattedCharSequence formattedcharsequence : this.commandUsage) {
			int j = this.anchorToBottom ? this.screen.height - 14 - 13 - 12 * i : getSuggestorY() + 12 * i;
			graphics.fill(this.commandUsagePosition - 1, j, this.commandUsagePosition + this.commandUsageWidth + 1, j + 12, this.fillColor);
			graphics.drawString(this.font, formattedcharsequence, this.commandUsagePosition, j + 2, -1);
			++i;
		}
	}

	public Component getNarrationMessage() {
		return this.suggestions != null
			? CommonComponents.NEW_LINE.copy().append(this.suggestions.getNarrationMessage())
			: CommonComponents.EMPTY;
	}

	/**
	 * Applies a suggestion to the multiline text, taking the mapping between
	 * the single-line and multiline commands into account.
	 */
	private String applySuggestion(Suggestion suggestion, String inputText) {
		int start = mapToMultiline(suggestion.getRange().getStart());
		int end = mapToMultiline(suggestion.getRange().getEnd());

		if (start == 0 && end == inputText.length()) {
			return suggestion.getText();
		}
		StringBuilder result = new StringBuilder();
		if (start > 0) {
			result.append(inputText, 0, start);
		}
		result.append(suggestion.getText());
		if (end < inputText.length()) {
			result.append(inputText, end, inputText.length());
		}
		return result.toString();
	}

	private final class SuggestionsList {
		private final Rect2i rect;
		private final String originalContents;
		private final List<Suggestion> suggestionList;
		private int offset;
		private int current;
		private Vec2 lastMouse = Vec2.ZERO;
		private boolean tabCycles;
		private int lastNarratedEntry;

		SuggestionsList(int x, int y, int width, List<Suggestion> suggestions, boolean showTabCompletion) {
			int i = x - 1;
			int j = CommandSuggestor.this.anchorToBottom
				? y - 3 - Math.min(suggestions.size(), CommandSuggestor.this.suggestionLineLimit) * 12
				: y;
			this.rect = new Rect2i(i, j, width + 1, Math.min(suggestions.size(), CommandSuggestor.this.suggestionLineLimit) * 12);
			this.originalContents = CommandSuggestor.this.input.getText();
			this.lastNarratedEntry = showTabCompletion ? -1 : 0;
			this.suggestionList = suggestions;
			this.select(0);
		}

		public void render(GuiGraphics graphics, int mouseX, int mouseY) {
			int i = Math.min(this.suggestionList.size(), CommandSuggestor.this.suggestionLineLimit);
			int j = -5592406;
			boolean flag = this.offset > 0;
			boolean flag1 = this.suggestionList.size() > this.offset + i;
			boolean flag2 = flag || flag1;
			boolean flag3 = this.lastMouse.x != (float)mouseX || this.lastMouse.y != (float)mouseY;
			if (flag3) {
				this.lastMouse = new Vec2((float)mouseX, (float)mouseY);
			}

			if (flag2) {
				graphics.fill(this.rect.getX(), this.rect.getY() - 1, this.rect.getX() + this.rect.getWidth(), this.rect.getY(), CommandSuggestor.this.fillColor);
				graphics.fill(this.rect.getX(), this.rect.getY() + this.rect.getHeight(), this.rect.getX() + this.rect.getWidth(), this.rect.getY() + this.rect.getHeight() + 1, CommandSuggestor.this.fillColor);
				if (flag) {
					for (int k = 0; k < this.rect.getWidth(); ++k) {
						if (k % 2 == 0) {
							graphics.fill(this.rect.getX() + k, this.rect.getY() - 1, this.rect.getX() + k + 1, this.rect.getY(), -1);
						}
					}
				}

				if (flag1) {
					for (int i1 = 0; i1 < this.rect.getWidth(); ++i1) {
						if (i1 % 2 == 0) {
							graphics.fill(this.rect.getX() + i1, this.rect.getY() + this.rect.getHeight(), this.rect.getX() + i1 + 1, this.rect.getY() + this.rect.getHeight() + 1, -1);
						}
					}
				}
			}

			boolean flag4 = false;

			for (int l = 0; l < i; ++l) {
				Suggestion suggestion = this.suggestionList.get(l + this.offset);
				graphics.fill(this.rect.getX(), this.rect.getY() + 12 * l, this.rect.getX() + this.rect.getWidth(), this.rect.getY() + 12 * l + 12, CommandSuggestor.this.fillColor);
				if (mouseX > this.rect.getX() && mouseX < this.rect.getX() + this.rect.getWidth() && mouseY > this.rect.getY() + 12 * l && mouseY < this.rect.getY() + 12 * l + 12) {
					if (flag3) {
						this.select(l + this.offset);
					}

					flag4 = true;
				}

				graphics.drawString(CommandSuggestor.this.font, suggestion.getText(), this.rect.getX() + 1, this.rect.getY() + 2 + 12 * l, l + this.offset == this.current ? -256 : -5592406);
			}

			if (flag4) {
				Message message = this.suggestionList.get(this.current).getTooltip();
				if (message != null) {
					graphics.renderTooltip(CommandSuggestor.this.font, ComponentUtils.fromMessage(message), mouseX, mouseY);
				}
			}
		}

		public boolean mouseClicked(int mouseX, int mouseY, int button) {
			if (!this.rect.contains(mouseX, mouseY)) {
				return false;
			} else {
				int i = (mouseY - this.rect.getY()) / 12 + this.offset;
				if (i >= 0 && i < this.suggestionList.size()) {
					this.select(i);
					this.useSuggestion();
				}

				return true;
			}
		}

		public boolean mouseScrolled(double amount) {
			int i = (int)(CommandSuggestor.this.minecraft.mouseHandler.xpos() * (double)CommandSuggestor.this.minecraft.getWindow().getGuiScaledWidth() / (double)CommandSuggestor.this.minecraft.getWindow().getScreenWidth());
			int j = (int)(CommandSuggestor.this.minecraft.mouseHandler.ypos() * (double)CommandSuggestor.this.minecraft.getWindow().getGuiScaledHeight() / (double)CommandSuggestor.this.minecraft.getWindow().getScreenHeight());
			if (this.rect.contains(i, j)) {
				this.offset = Mth.clamp((int)((double)this.offset - amount), 0, Math.max(this.suggestionList.size() - CommandSuggestor.this.suggestionLineLimit, 0));
				return true;
			} else {
				return false;
			}
		}

		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == 265 /* GLFW_KEY_UP */) {
				this.cycle(-1);
				this.tabCycles = false;
				return true;
			} else if (keyCode == 264 /* GLFW_KEY_DOWN */) {
				this.cycle(1);
				this.tabCycles = false;
				return true;
			} else if (keyCode == 258 /* GLFW_KEY_TAB */) {
				if (this.tabCycles) {
					this.cycle(Screen.hasShiftDown() ? -1 : 1);
				}

				this.useSuggestion();
				return true;
			} else if (keyCode == 256 /* GLFW_KEY_ESCAPE */) {
				CommandSuggestor.this.hide();
				return true;
			} else {
				return false;
			}
		}

		public void cycle(int offset) {
			this.select(this.current + offset);
			int i = this.offset;
			int j = this.offset + CommandSuggestor.this.suggestionLineLimit - 1;
			if (this.current < i) {
				this.offset = Mth.clamp(this.current, 0, Math.max(this.suggestionList.size() - CommandSuggestor.this.suggestionLineLimit, 0));
			} else if (this.current > j) {
				this.offset = Mth.clamp(this.current + CommandSuggestor.this.lineStartOffset - CommandSuggestor.this.suggestionLineLimit, 0, Math.max(this.suggestionList.size() - CommandSuggestor.this.suggestionLineLimit, 0));
			}
		}

		public void select(int current) {
			this.current = current;
			if (this.current < 0) {
				this.current += this.suggestionList.size();
			}

			if (this.current >= this.suggestionList.size()) {
				this.current -= this.suggestionList.size();
			}

			if (this.lastNarratedEntry != this.current) {
				CommandSuggestor.this.minecraft.getNarrator().sayNow(this.getNarrationMessage());
			}
		}

		public void useSuggestion() {
			Suggestion suggestion = this.suggestionList.get(this.current);
			CommandSuggestor.this.keepSuggestions = true;
			CommandSuggestor.this.input.setText(CommandSuggestor.this.applySuggestion(suggestion, this.originalContents));
			int singleLineCursor = suggestion.getRange().getStart() + suggestion.getText().length();
			CommandSuggestor.this.input.setCursor(CommandSuggestor.this.mapToMultiline(singleLineCursor), false);
			this.select(this.current);
			CommandSuggestor.this.keepSuggestions = false;
			this.tabCycles = true;
		}

		Component getNarrationMessage() {
			this.lastNarratedEntry = this.current;
			Suggestion suggestion = this.suggestionList.get(this.current);
			Message message = suggestion.getTooltip();
			return message != null
				? Component.translatable("narration.suggestion.tooltip", this.current + 1, this.suggestionList.size(), suggestion.getText(), message)
				: Component.translatable("narration.suggestion", this.current + 1, this.suggestionList.size(), suggestion.getText());
		}
	}
}
