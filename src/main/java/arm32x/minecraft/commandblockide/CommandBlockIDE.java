package arm32x.minecraft.commandblockide;

import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.server.command.EditFunctionCommand;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = CommandBlockIDE.MOD_ID, name = "Command Block IDE", version = "1.0.0",
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public final class CommandBlockIDE {
    public static final String MOD_ID = "commandblockide";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    @SidedProxy(clientSide = "arm32x.minecraft.commandblockide.client.CommandBlockIDEClient",
            serverSide = "arm32x.minecraft.commandblockide.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) { Packets.register(); }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) { proxy.init(); }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new EditFunctionCommand());
    }
}
