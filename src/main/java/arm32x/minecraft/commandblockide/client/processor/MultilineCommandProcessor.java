package arm32x.minecraft.commandblockide.client.processor;

import arm32x.minecraft.commandblockide.util.Pair;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class MultilineCommandProcessor implements CommandProcessor {
    private static final MultilineCommandProcessor INSTANCE = new MultilineCommandProcessor();
    private MultilineCommandProcessor() { }
    public static MultilineCommandProcessor getInstance() { return INSTANCE; }

    @Override
    public Pair<String, StringMapping> processCommand(String command) {
        StringBuilder result = new StringBuilder();
        NavigableMap<Integer, Integer> map = new TreeMap<>();
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\n') {
                result.append(' ');
                while (i + 1 < command.length() && command.charAt(i + 1) == ' ') i++;
                map.put(result.length(), i + 1);
            } else result.append(ch);
        }
        return new Pair<>(result.toString(), new StringMapping(map));
    }
}
