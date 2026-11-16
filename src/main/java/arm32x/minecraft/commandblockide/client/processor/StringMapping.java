package arm32x.minecraft.commandblockide.client.processor;

import java.util.Map;
import java.util.NavigableMap;

public final class StringMapping {
    private final NavigableMap<Integer, Integer> map;
    public StringMapping(NavigableMap<Integer, Integer> map) { this.map = map; }
    public int mapIndexOrAfter(int index) {
        Map.Entry<Integer, Integer> entry = map.floorEntry(index);
        return entry == null ? index : index + entry.getValue() - entry.getKey();
    }
}
