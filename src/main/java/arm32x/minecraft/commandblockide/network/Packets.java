package arm32x.minecraft.commandblockide.network;

import arm32x.minecraft.commandblockide.CommandBlockIDE;
import arm32x.minecraft.commandblockide.server.function.FunctionIO;
import arm32x.minecraft.commandblockide.util.PacketMerger;
import arm32x.minecraft.commandblockide.util.PacketSplitter;
import arm32x.minecraft.commandblockide.command.LegacyCommandParser;
import arm32x.minecraft.commandblockide.util.ContentHash;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.tileentity.TileEntityCommandBlock.Mode;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class Packets {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CommandBlockIDE.MOD_ID);
    private static final Map<NetHandlerPlayServer, PacketMerger> MERGERS = new WeakHashMap<>();
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    public static int nextRequest() { return REQUESTS.incrementAndGet(); }
    private Packets() { }

    public static void register() {
        CHANNEL.registerMessage((FunctionChunk packet, MessageContext ctx) -> {
            CommandBlockIDE.proxy.receiveFunction(packet, ctx.netHandler);
            return null;
        }, FunctionChunk.class, 0, Side.CLIENT);
        CHANNEL.registerMessage((ApplyFunctionChunk packet, MessageContext ctx) -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.connection.netManager.isChannelOpen()) return;
                if (!player.canUseCommand(2, "editfunction")) {
                    MERGERS.remove(player.connection);
                    if (packet.offset == 0) CHANNEL.sendTo(new SaveResult(packet.request, "", false, "No permission to save functions."), player);
                    return;
                }
                try {
                    PacketMerger merger = MERGERS.computeIfAbsent(player.connection, key -> new PacketMerger());
                    byte[] bytes = merger.append(packet.request, packet.offset, packet.total, packet.bytes);
                    if (bytes == null) return;
                    MERGERS.remove(player.connection);
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    int separator = text.indexOf('\n');
                    if (separator < 1) throw new IllegalArgumentException("Missing function name");
                    ResourceLocation id = new ResourceLocation(text.substring(0, separator));
                    int revisionEnd = text.indexOf('\n', separator + 1);
                    if (revisionEnd < 0) throw new IllegalArgumentException("Missing function revision");
                    String revision = text.substring(separator + 1, revisionEnd);
                    List<String> lines = Arrays.asList(text.substring(revisionEnd + 1).split("\n", -1));
                    FunctionIO.saveFunction(player.getServer(), id, revision, lines);
                    CHANNEL.sendTo(new SaveResult(packet.request, id.toString(), true, "Saved function '" + id + "'."), player);
                } catch (IOException | IllegalArgumentException ex) {
                    MERGERS.remove(player.connection);
                    CHANNEL.sendTo(new SaveResult(packet.request, "", false, ex.getMessage()), player);
                    CommandBlockIDE.LOGGER.warn("Cannot save function for {}", player.getName(), ex);
                }
            });
            return null;
        }, ApplyFunctionChunk.class, 1, Side.SERVER);
        CHANNEL.registerMessage((RefreshBlocks packet, MessageContext ctx) -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.connection.netManager.isChannelOpen() || !player.canUseCommandBlock() || !player.getServer().isCommandBlockEnabled()) return;
                for (BlockPos pos : packet.positions) {
                    if (!player.world.isBlockLoaded(pos)) continue;
                    TileEntity tile = player.world.getTileEntity(pos);
                    if (tile instanceof TileEntityCommandBlock) {
                        player.connection.sendPacket(new SPacketUpdateTileEntity(pos, 2, tile.writeToNBT(new NBTTagCompound())));
                    }
                }
            });
            return null;
        }, RefreshBlocks.class, 2, Side.SERVER);
        CHANNEL.registerMessage((SaveResult packet, MessageContext ctx) -> {
            CommandBlockIDE.proxy.receiveResult(packet, ctx.netHandler);
            return null;
        }, SaveResult.class, 3, Side.CLIENT);
        CHANNEL.registerMessage((Suggest packet, MessageContext ctx) -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.connection.netManager.isChannelOpen()) return;
                List<String> matches = Collections.emptyList();
                if (player.canUseCommand(2, "editfunction")) {
                    int nested = LegacyCommandParser.nestedStart(packet.prefix);
                    try {
                        matches = player.getServer().getCommandManager().getTabCompletions(player, packet.prefix.substring(nested), packet.pos);
                    } catch (RuntimeException ex) {
                        // A third-party ICommand may throw from its completion provider. Do not crash the server task.
                        CommandBlockIDE.LOGGER.warn("Command completion provider failed", ex);
                    }
                }
                CHANNEL.sendTo(new Suggestions(packet.request, matches), player);
            });
            return null;
        }, Suggest.class, 4, Side.SERVER);
        CHANNEL.registerMessage((Suggestions packet, MessageContext ctx) -> {
            CommandBlockIDE.proxy.receiveSuggestions(packet, ctx.netHandler); return null;
        }, Suggestions.class, 5, Side.CLIENT);
        CHANNEL.registerMessage((ApplyBlock packet, MessageContext ctx) -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.connection.netManager.isChannelOpen()) return;
                String failure = null;
                TileEntity tile = player.world.isBlockLoaded(packet.pos) ? player.world.getTileEntity(packet.pos) : null;
                if (!player.canUseCommandBlock() || !player.getServer().isCommandBlockEnabled()) failure = "Command blocks disabled or permission denied.";
                else if (player.dimension != packet.dimension || !(tile instanceof TileEntityCommandBlock)
                        || !(player.world.getBlockState(packet.pos).getBlock() instanceof BlockCommandBlock)) failure = "Command block is no longer available.";
                else if (!packet.revision.equals(ApplyBlock.revision((TileEntityCommandBlock) tile))) failure = "Command block changed since opening. Reopen before saving.";
                if (failure != null) {
                    CHANNEL.sendTo(new SaveResult(packet.request, "block:" + packet.pos.toLong(), false, failure), player); return;
                }
                TileEntityCommandBlock block = (TileEntityCommandBlock) tile;
                IBlockState state = player.world.getBlockState(packet.pos);
                IBlockState replacement = (packet.mode == Mode.AUTO ? Blocks.REPEATING_COMMAND_BLOCK : packet.mode == Mode.SEQUENCE ? Blocks.CHAIN_COMMAND_BLOCK
                    : Blocks.COMMAND_BLOCK).getDefaultState().withProperty(BlockCommandBlock.FACING, state.getValue(BlockCommandBlock.FACING))
                    .withProperty(BlockCommandBlock.CONDITIONAL, packet.conditional);
                if (!state.equals(replacement) && !player.world.setBlockState(packet.pos, replacement, 2)) {
                    CHANNEL.sendTo(new SaveResult(packet.request, "block:" + packet.pos.toLong(), false, "Cannot change command block state."), player); return;
                }
                block.validate(); player.world.setTileEntity(packet.pos, block);
                CommandBlockBaseLogic logic = block.getCommandBlockLogic();
                logic.setCommand(packet.command); logic.setTrackOutput(packet.trackOutput);
                if (!packet.trackOutput) logic.setLastOutput(null);
                block.setAuto(packet.automatic); logic.updateCommand(); block.markDirty();
                player.connection.sendPacket(new SPacketUpdateTileEntity(packet.pos, 2, block.writeToNBT(new NBTTagCompound())));
                CHANNEL.sendTo(new SaveResult(packet.request, "block:" + packet.pos.toLong(), true, "Command block saved."), player);
            });
            return null;
        }, ApplyBlock.class, 6, Side.SERVER);
    }

    public static void sendFunction(ResourceLocation id, String revision, List<String> lines, EntityPlayerMP player, int request) {
        byte[] bytes = (id + "\n" + revision + "\n" + String.join("\n", lines)).getBytes(StandardCharsets.UTF_8);
        PacketSplitter.split(bytes, (offset, chunk) -> {
            if (player == null) CHANNEL.sendToServer(new ApplyFunctionChunk(request, offset, bytes.length, chunk));
            else CHANNEL.sendTo(new FunctionChunk(request, offset, bytes.length, chunk), player);
        });
    }

    public static class FunctionChunk implements IMessage {
        public int request, offset, total;
        public byte[] bytes;
        public FunctionChunk() { }
        public FunctionChunk(int request, int offset, int total, byte[] bytes) {
            this.request = request; this.offset = offset; this.total = total; this.bytes = bytes;
        }
        @Override public void fromBytes(ByteBuf buf) {
            request = buf.readInt(); offset = buf.readInt(); total = buf.readInt();
            int length = buf.readableBytes();
            if (total < 1 || total > PacketSplitter.MAX_SIZE || offset < 0 || offset >= total
                    || length < 1 || length > PacketSplitter.CHUNK_SIZE || offset + length > total) {
                throw new IllegalArgumentException("Invalid function chunk");
            }
            bytes = new byte[length]; buf.readBytes(bytes);
        }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(request); buf.writeInt(offset); buf.writeInt(total); buf.writeBytes(bytes); }
    }

    public static final class ApplyFunctionChunk extends FunctionChunk {
        public ApplyFunctionChunk() { }
        public ApplyFunctionChunk(int request, int offset, int total, byte[] bytes) { super(request, offset, total, bytes); }
    }

    public static final class RefreshBlocks implements IMessage {
        public List<BlockPos> positions;
        public RefreshBlocks() { }
        public RefreshBlocks(List<BlockPos> positions) { this.positions = positions; }
        @Override public void fromBytes(ByteBuf buf) {
            int count = buf.readInt();
            if (count < 1 || count > 1024 || buf.readableBytes() != count * 8) {
                throw new IllegalArgumentException("Invalid block refresh request");
            }
            positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) positions.add(BlockPos.fromLong(buf.readLong()));
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(positions.size());
            for (BlockPos pos : positions) buf.writeLong(pos.toLong());
        }
    }

    public static final class SaveResult implements IMessage {
        public int request;
        public String function, message;
        public boolean success;
        public SaveResult() { }
        public SaveResult(int request, String function, boolean success, String message) {
            this.request = request; this.function = function; this.success = success; this.message = message;
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer packet = new PacketBuffer(buf);
            request = packet.readInt(); function = packet.readString(256); success = packet.readBoolean(); message = packet.readString(4096);
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer packet = new PacketBuffer(buf);
            packet.writeInt(request); packet.writeString(function); packet.writeBoolean(success);
            packet.writeString(message == null ? "Cannot save function" : message.substring(0, Math.min(message.length(), 4096)));
        }
    }

    public static final class Suggest implements IMessage {
        public int request;
        public String prefix;
        public BlockPos pos;
        public Suggest() { }
        public Suggest(int request, String prefix, BlockPos pos) { this.request = request; this.prefix = prefix; this.pos = pos; }
        @Override public void fromBytes(ByteBuf buf) { PacketBuffer b = new PacketBuffer(buf); request = b.readInt(); prefix = b.readString(32500); pos = b.readBlockPos(); }
        @Override public void toBytes(ByteBuf buf) { PacketBuffer b = new PacketBuffer(buf); b.writeInt(request); b.writeString(prefix); b.writeBlockPos(pos); }
    }
    public static final class Suggestions implements IMessage {
        public int request;
        public List<String> matches;
        public Suggestions() { }
        public Suggestions(int request, List<String> matches) {
            this.request = request;
            this.matches = new ArrayList<>();
            for (String match : matches) {
                if (this.matches.size() == 256) break;
                if (match != null && match.length() <= 256) this.matches.add(match);
            }
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer b = new PacketBuffer(buf); request = b.readInt(); int count = b.readInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid suggestions count");
            matches = new ArrayList<>(count); for (int i = 0; i < count; i++) matches.add(b.readString(256));
        }
        @Override public void toBytes(ByteBuf buf) { PacketBuffer b = new PacketBuffer(buf); b.writeInt(request); b.writeInt(matches.size()); for (String match : matches) b.writeString(match); }
    }
    public static final class ApplyBlock implements IMessage {
        public int request, dimension;
        public BlockPos pos;
        public String command, revision;
        public Mode mode;
        public boolean conditional, automatic, trackOutput;
        public ApplyBlock() { }
        public ApplyBlock(int request, int dimension, BlockPos pos, String revision, String command, Mode mode, boolean conditional, boolean automatic, boolean trackOutput) {
            this.request = request; this.dimension = dimension; this.pos = pos; this.revision = revision; this.command = command;
            this.mode = mode; this.conditional = conditional; this.automatic = automatic; this.trackOutput = trackOutput;
        }
        public static String revision(TileEntityCommandBlock tile) {
            return ContentHash.of(tile.getCommandBlockLogic().getCommand() + "\n" + tile.getMode() + ":" + tile.isConditional() + ":" + tile.isAuto()
                + ":" + tile.getCommandBlockLogic().shouldTrackOutput());
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer b = new PacketBuffer(buf); request = b.readInt(); dimension = b.readInt(); pos = b.readBlockPos(); revision = b.readString(64);
            command = b.readString(32500);
            if (command.getBytes(StandardCharsets.UTF_8).length > 32500 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid block command");
            mode = Mode.valueOf(b.readString(16)); conditional = b.readBoolean(); automatic = b.readBoolean(); trackOutput = b.readBoolean();
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer b = new PacketBuffer(buf); b.writeInt(request); b.writeInt(dimension); b.writeBlockPos(pos); b.writeString(revision); b.writeString(command);
            b.writeString(mode.name()); b.writeBoolean(conditional); b.writeBoolean(automatic); b.writeBoolean(trackOutput);
        }
    }
}
