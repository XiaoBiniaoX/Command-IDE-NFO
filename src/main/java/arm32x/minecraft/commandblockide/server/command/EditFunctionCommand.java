package arm32x.minecraft.commandblockide.server.command;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import arm32x.minecraft.commandblockide.network.Packets;
import arm32x.minecraft.commandblockide.server.function.FunctionIO;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

public final class EditFunctionCommand extends CommandBase {
    @Override public String getName() { return "editfunction"; }
    @Override public String getUsage(ICommandSender sender) { return "/editfunction <namespace:path>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        NetworkDispatcher dispatcher = NetworkDispatcher.get(player.connection.netManager);
        if (dispatcher == null || !dispatcher.getModList().containsKey(CommandBlockIDE.MOD_ID)) {
            throw new CommandException("commands.editfunction.failed.modNotInstalled");
        }
        if (args[0].startsWith("#")) throw new CommandException("arguments.editfunction.tag.unsupported");
        ResourceLocation id = new ResourceLocation(args[0]);
        if (server.getFunctionManager().getFunction(id) == null) {
            throw new CommandException("commands.editfunction.failed.functionNotFound", id);
        }
        try {
            List<String> lines = FunctionIO.loadFunction(server, id);
            Packets.sendFunction(id, arm32x.minecraft.commandblockide.util.ContentHash.of(String.join("\n", lines)), lines, player, Packets.nextRequest());
        }
        catch (IOException | IllegalArgumentException ex) { throw new CommandException(ex.getMessage()); }
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getFunctionManager().getFunctions().keySet())
                : Collections.emptyList();
    }
}
