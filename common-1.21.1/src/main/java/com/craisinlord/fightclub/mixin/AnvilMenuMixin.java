package com.craisinlord.fightclub.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 2))
    private int fightClub$noTooExpensive(int limit) {
        return com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.NO_ANVIL_LIMIT) ? Integer.MAX_VALUE : limit;
    }
}
