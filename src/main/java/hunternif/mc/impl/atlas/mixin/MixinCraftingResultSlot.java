package hunternif.mc.impl.atlas.mixin;

import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Kept as a no-op mixin placeholder for 1.20.1.
 *
 * The previous injection targeted an obsolete Yarn-named method descriptor
 * (net/minecraft/item/ItemStack) and its body was already empty. ForgeGradle's
 * Mixin annotation processor therefore failed the build while the mixin had
 * no runtime effect. Removing the dead injection preserves behavior and lets
 * the 1.20.1 Forge sources compile with Mojmap/Parchment mappings.
 */
@Mixin(ResultSlot.class)
public abstract class MixinCraftingResultSlot {
}
