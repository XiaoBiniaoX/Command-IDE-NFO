package arm32x.minecraft.commandblockide;

import arm32x.minecraft.commandblockide.network.Packets;
import net.minecraft.network.INetHandler;

public class CommonProxy {
    public void init() { }
    public void receiveFunction(Packets.FunctionChunk packet, INetHandler handler) { }
    public void receiveResult(Packets.SaveResult packet, INetHandler handler) { }
    public void receiveSuggestions(Packets.Suggestions packet, INetHandler handler) { }
}
