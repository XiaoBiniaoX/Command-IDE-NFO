package arm32x.minecraft.commandblockide.mixin.client;

import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.tileentity.TileEntityCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiCommandBlock.class)
public interface GuiCommandBlockAccessor {
    @Accessor("commandBlock") TileEntityCommandBlock ide$getCommandBlock();
}
