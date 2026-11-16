package arm32x.minecraft.commandblockide.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.ArrayList;
import java.util.List;

/** Parse-only legacy grammar. Never dispatches or runs a command. */
public final class LegacyCommandParser {
    public static final CommandDispatcher<Object> DISPATCHER = new CommandDispatcher<>();
    private static final Object SOURCE = new Object();
    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(new LiteralMessage("Invalid argument"));
    static {
        // Alternatives are real Brigadier branches; optional tails are separate paths.
        String[] patterns = {
            "say message...", "me action...", "tell target message...", "msg target message...", "w target message...",
            "tellraw target json...", "title target clear", "title target reset", "title target title json...",
            "title target subtitle json...", "title target actionbar json...", "title target times int int int",
            "give target item", "give target item int", "give target item int int", "give target item int int nbt...",
            "clear", "clear target", "clear target item", "clear target item int", "clear target item int int", "clear target item int int nbt...",
            "kill", "kill target", "gamemode mode", "gamemode mode target", "defaultgamemode mode",
            "difficulty difficulty", "time set int", "time set day", "time set night", "time add int",
            "time query daytime", "time query gametime", "time query day", "weather clear", "weather rain", "weather thunder",
            "weather clear int", "weather rain int", "weather thunder int", "toggledownfall",
            "gamerule rule", "gamerule rule value", "seed", "list", "list uuids", "help", "help topic", "?", "? topic",
            "setblock coord coord coord block", "setblock coord coord coord block data", "setblock coord coord coord block data placement",
            "setblock coord coord coord block data placement nbt...",
            "fill coord coord coord coord coord coord block", "fill coord coord coord coord coord coord block data",
            "fill coord coord coord coord coord coord block data fillmode", "fill coord coord coord coord coord coord block data replace block",
            "fill coord coord coord coord coord coord block data replace block data", "fill coord coord coord coord coord coord block data fillmode nbt...",
            "clone coord coord coord coord coord coord coord coord coord", "clone coord coord coord coord coord coord coord coord coord clonemode",
            "clone coord coord coord coord coord coord coord coord coord clonemode movemode",
            "clone coord coord coord coord coord coord coord coord coord filtered movemode block",
            "clone coord coord coord coord coord coord coord coord coord filtered movemode block data",
            "summon entity", "summon entity coord coord coord", "summon entity coord coord coord nbt...",
            "tp target", "tp coord coord coord", "tp coord coord coord coord coord", "tp target target",
            "tp target coord coord coord", "tp target coord coord coord coord coord",
            "teleport target target", "teleport target coord coord coord", "teleport target coord coord coord coord coord",
            "spawnpoint", "spawnpoint target", "spawnpoint target coord coord coord", "setworldspawn", "setworldspawn coord coord coord",
            "testfor target", "testfor target nbt...", "testforblock coord coord coord block", "testforblock coord coord coord block data",
            "testforblock coord coord coord block data nbt...", "testforblocks coord coord coord coord coord coord coord coord coord",
            "testforblocks coord coord coord coord coord coord coord coord coord all", "testforblocks coord coord coord coord coord coord coord coord coord masked",
            "blockdata coord coord coord nbt...", "entitydata target nbt...", "enchant target enchantment", "enchant target enchantment int",
            "effect target clear", "effect target effect", "effect target effect int", "effect target effect int int", "effect target effect int int bool",
            "xp xpamount", "xp xpamount target", "function function", "function function if target", "function function unless target", "editfunction function",
            "playsound sound category target", "playsound sound category target coord coord coord",
            "playsound sound category target coord coord coord number", "playsound sound category target coord coord coord number number",
            "playsound sound category target coord coord coord number number number", "stopsound target", "stopsound target category", "stopsound target category sound",
            "particle particle coord coord coord number number number number", "particle particle coord coord coord number number number number int",
            "particle particle coord coord coord number number number number int normal", "particle particle coord coord coord number number number number int force",
            "particle particle coord coord coord number number number number int normal target", "particle particle coord coord coord number number number number int force target",
            "worldborder get", "worldborder set number", "worldborder set number int", "worldborder add number", "worldborder add number int",
            "worldborder center coord coord", "worldborder damage amount number", "worldborder damage buffer number",
            "worldborder warning time int", "worldborder warning distance int", "reload", "save-all", "save-all flush", "save-on", "save-off",
            "scoreboard objectives list", "scoreboard objectives add objective criteria", "scoreboard objectives add objective criteria display...",
            "scoreboard objectives remove objective", "scoreboard objectives setdisplay slot", "scoreboard objectives setdisplay slot objective",
            "scoreboard players list", "scoreboard players list target", "scoreboard players set target objective int",
            "scoreboard players add target objective int", "scoreboard players remove target objective int", "scoreboard players reset target",
            "scoreboard players reset target objective", "scoreboard players enable target objective", "scoreboard players test target objective score",
            "scoreboard players test target objective score score", "scoreboard players operation target objective operation target objective",
            "scoreboard players tag target list", "scoreboard players tag target add tag", "scoreboard players tag target remove tag",
            "scoreboard players tag target add tag nbt...", "scoreboard players tag target remove tag nbt...",
            "scoreboard teams list", "scoreboard teams list team", "scoreboard teams add team", "scoreboard teams add team display...",
            "scoreboard teams remove team", "scoreboard teams empty team", "scoreboard teams join team", "scoreboard teams join team targets...",
            "scoreboard teams leave targets...", "scoreboard teams option team option value",
            "trigger objective set int", "trigger objective add int", "recipe give target recipe", "recipe take target recipe",
            "advancement grant target everything", "advancement revoke target everything", "advancement grant target only advancement",
            "advancement grant target only advancement criterion", "advancement revoke target only advancement", "advancement revoke target only advancement criterion",
            "advancement grant target from advancement", "advancement grant target until advancement", "advancement grant target through advancement",
            "advancement revoke target from advancement", "advancement revoke target until advancement", "advancement revoke target through advancement",
            "replaceitem entity target slot item", "replaceitem entity target slot item int", "replaceitem entity target slot item int int",
            "replaceitem entity target slot item int int nbt...", "replaceitem block coord coord coord slot item",
            "replaceitem block coord coord coord slot item int", "replaceitem block coord coord coord slot item int int",
            "replaceitem block coord coord coord slot item int int nbt...", "locate structure", "spreadplayers coord coord number number bool targets..."
        };
        for (String pattern : patterns) register(pattern);
        register("execute target coord coord coord command...");
        register("execute target coord coord coord detect coord coord coord block data command...");
    }
    private LegacyCommandParser() { }

    private static void register(String pattern) {
        String[] parts = pattern.split(" ");
        ArgumentBuilder<Object, ?> node = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            ArgumentBuilder<Object, ?> next;
            boolean literal = i == 0 || !isArgument(part) || parts[0].equals("replaceitem") && i == 1
                || parts[0].equals("scoreboard") && i == 2 && (part.equals("operation") || part.equals("option"));
            if (literal) next = LiteralArgumentBuilder.literal(part);
            else next = RequiredArgumentBuilder.argument(part.replace("...", "") + i,
                token(parts[0].equals("tp") && part.equals("target") ? "tpTarget" : part));
            if (node == null) next.executes(context -> 0); else next.then(node);
            node = next;
        }
        DISPATCHER.register((LiteralArgumentBuilder<Object>) node);
    }
    private static boolean isArgument(String part) {
        return part.endsWith("...") || (" target item int mode difficulty rule value coord block data placement fillmode clonemode movemode entity "
            + "enchantment effect bool xpamount function sound category particle number objective criteria slot score operation tag team targets "
            + "option recipe advancement criterion structure topic ").contains(" " + part + " ");
    }
    private static ArgumentType<?> token(String type) {
        if (type.endsWith("...")) return StringArgumentType.greedyString();
        if (type.equals("int")) return IntegerArgumentType.integer();
        if (type.equals("number")) return DoubleArgumentType.doubleArg();
        return new ArgumentType<String>() {
        @Override public String parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') reader.skip();
            String value = reader.getString().substring(start, reader.getCursor());
            boolean valid = !value.isEmpty();
            if (type.equals("coord")) valid = value.matches("~?(?:-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))?") && !value.isEmpty();
            else if (type.equals("data")) valid = value.matches("-?[0-9]+|\\*|[a-zA-Z_]+=[^ ]+");
            else if (type.equals("bool")) valid = value.equals("true") || value.equals("false");
            else if (type.equals("mode")) valid = value.matches("[0-3]|s|c|a|sp|survival|creative|adventure|spectator");
            else if (type.equals("difficulty")) valid = value.matches("[0-3]|p|e|n|h|peaceful|easy|normal|hard");
            else if (type.equals("target") && value.startsWith("@")) valid = value.matches("@[pares](?:\\[[^\\[\\]]*\\])?");
            else if (type.equals("tpTarget")) valid = !value.matches("~.*|-?[0-9]+(?:\\.[0-9]*)?") && (!value.startsWith("@") || value.matches("@[pares](?:\\[[^\\[\\]]*\\])?"));
            else if (type.equals("xpamount")) valid = value.matches("-?[0-9]+[lL]?");
            else if (type.equals("score")) valid = value.matches("-?[0-9]+|\\*");
            else if (type.equals("operation")) valid = value.matches("\\+=|-=|\\*=|/=|%=|=|<|>|><");
            if (!valid) { reader.setCursor(start); throw INVALID.createWithContext(reader); }
            return value;
        }
        @Override public <S> java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> listSuggestions(
                com.mojang.brigadier.context.CommandContext<S> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
            String options = type.equals("bool") ? "true false" : type.equals("mode") ? "survival creative adventure spectator"
                : type.equals("difficulty") ? "peaceful easy normal hard" : type.equals("coord") ? "~"
                : type.equals("placement") ? "replace destroy keep" : type.equals("fillmode") ? "replace destroy keep hollow outline"
                : type.equals("clonemode") ? "replace masked filtered" : type.equals("movemode") ? "normal force move" : "";
            for (String option : options.split(" ")) if (!option.isEmpty() && option.startsWith(builder.getRemainingLowerCase())) builder.suggest(option);
            return builder.buildFuture();
        }
        };
    }

    public static final class View {
        public final List<int[]> ranges = new ArrayList<>();
        public int error = -1;
        public String message = "", usage = "";
        public boolean supported;
    }
    public static View parse(String text) {
        View view = new View();
        if (text.trim().isEmpty() || text.startsWith("#")) return view;
        int offset = text.startsWith("/") ? 1 : 0;
        String input = text.substring(offset);
        String name = input.split(" ", 2)[0];
        view.supported = DISPATCHER.getRoot().getChild(name) != null;
        if (!view.supported) return view;
        ParseResults<Object> result = DISPATCHER.parse(input, SOURCE);
        CommandContextBuilder<Object> context = result.getContext();
        int color = 0;
        while (context != null) {
            for (ParsedCommandNode<Object> node : context.getNodes()) {
                view.ranges.add(new int[]{offset + node.getRange().getStart(), offset + node.getRange().getEnd(),
                    node.getNode() instanceof ArgumentCommandNode ? 1 + color++ % 4 : 0});
            }
            context = context.getChild();
        }
        if (result.getReader().canRead()) {
            view.error = offset + result.getReader().getCursor();
            view.message = result.getExceptions().isEmpty() ? "Unexpected argument" : result.getExceptions().values().iterator().next().getRawMessage().getString();
        } else if (result.getContext().getLastChild().getCommand() == null) {
            view.error = text.length(); view.message = "Incomplete command";
        }
        CommandContextBuilder<Object> last = result.getContext().getLastChild();
        if (!last.getNodes().isEmpty()) {
            view.usage = String.join(" | ", DISPATCHER.getSmartUsage(last.getNodes().get(last.getNodes().size() - 1).getNode(), SOURCE).values());
        }
        int nested = nestedStart(input);
        if (nested > 0 && nested < input.length() && view.error < 0) {
            View child = parse(input.substring(nested));
            int shift = nested + offset;
            view.ranges.removeIf(range -> range[0] >= shift);
            for (int[] range : child.ranges) view.ranges.add(new int[]{range[0] + shift, range[1] + shift, range[2]});
            view.supported = child.supported;
            if (child.error >= 0) { view.error = child.error + shift; view.message = child.message; }
            view.usage = child.usage;
        }
        return view;
    }

    /** Legacy execute's provider stops at its own coordinates; complete the nested command explicitly. */
    public static int nestedStart(String input) {
        int start = input.startsWith("/") ? 1 : 0;
        while (input.startsWith("execute ", start)) {
            java.util.regex.Matcher words = java.util.regex.Pattern.compile("[^ ]+").matcher(input);
            words.region(start, input.length());
            List<Integer> positions = new ArrayList<>();
            List<String> values = new ArrayList<>();
            while (words.find() && positions.size() < 12) { positions.add(words.start()); values.add(words.group()); }
            int nested = values.size() > 5 && values.get(5).equals("detect") ? 11 : 5;
            if (positions.size() <= nested) {
                if (values.size() == nested && input.endsWith(" ")) return input.length();
                return start;
            }
            start = positions.get(nested);
        }
        return start;
    }
}
