package arm32x.minecraft.commandblockide.mixin.server;

import java.io.File;
import net.minecraft.advancements.FunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FunctionManager.class)
public interface FunctionManagerAccessor {
    @Accessor("functionDir") File ide$getFunctionDir();
}
