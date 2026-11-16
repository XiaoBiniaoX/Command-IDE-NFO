package arm32x.minecraft.commandblockide.mixin.server;

import java.nio.file.Path;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathPackResources.class)
public interface PathPackResourcesAccessor {
	@Accessor("root")
	Path getRoot();
}
