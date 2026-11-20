package com.craisinlord.fightclub.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Removes the anvil's "Too Expensive!" limit (design doc). createResult uses 40 three times: the
 * cost of a stacked input (ordinal 0), the rename-only cap (1) and the cutoff that empties the
 * result at 40+ levels (2). Only the cutoff changes; XP costs keep rising as in vanilla.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 2))
    private int fightClub$noTooExpensive(int limit) {
        return com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.NO_ANVIL_LIMIT) ? Integer.MAX_VALUE : limit;
    }
}
