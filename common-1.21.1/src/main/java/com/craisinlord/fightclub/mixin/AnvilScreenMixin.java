package com.craisinlord.fightclub.mixin;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The anvil screen shows the real level cost instead of "Too Expensive!" at 40+ (matches AnvilMenuMixin). */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
    private int fightClub$noTooExpensiveLabel(int limit) {
        return com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.NO_ANVIL_LIMIT) ? Integer.MAX_VALUE : limit;
    }
}
