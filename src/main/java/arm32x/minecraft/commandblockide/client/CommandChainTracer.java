package arm32x.minecraft.commandblockide.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CommandChainTracer {
    private final World world;
    public CommandChainTracer(World world) { this.world = world; }
    public List<BlockPos> trace(BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        BlockPos position = start;
        visited.add(position);
        while (world.getBlockState(position).getBlock() instanceof BlockCommandBlock) {
            BlockPos previous = null;
            int matches = 0;
            EnumFacing facing = world.getBlockState(position).getValue(BlockCommandBlock.FACING);
            for (EnumFacing direction : EnumFacing.values()) {
                BlockPos candidate = position.offset(direction);
                IBlockState state = world.getBlockState(candidate);
                if (direction != facing && state.getBlock() instanceof BlockCommandBlock && !visited.contains(candidate)
                        && candidate.offset(state.getValue(BlockCommandBlock.FACING)).equals(position)) {
                    previous = candidate; matches++;
                }
            }
            if (matches != 1) break;
            position = previous; visited.add(position);
        }
        visited.clear();
        List<BlockPos> chain = new ArrayList<>();
        while (world.getBlockState(position).getBlock() instanceof BlockCommandBlock && visited.add(position)) {
            chain.add(position);
            position = position.offset(world.getBlockState(position).getValue(BlockCommandBlock.FACING));
        }
        return chain;
    }
}
