package arm32x.minecraft.commandblockide.client.processor;

import arm32x.minecraft.commandblockide.util.Pair;

public interface CommandProcessor {
    Pair<String, StringMapping> processCommand(String command);
}
