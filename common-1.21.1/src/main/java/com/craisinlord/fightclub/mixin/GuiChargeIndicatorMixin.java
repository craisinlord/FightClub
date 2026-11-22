package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class GuiChargeIndicatorMixin {
    @Redirect(method = {"renderCrosshair", "renderItemHotbar"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 2)
    private float fightClub$chargeIndicator(LocalPlayer player, float adjustTicks) {
        if (CombatInputClient.isCharging()
                && com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.CHARGE_INDICATOR)) {
            return CombatInputClient.chargeProgress(Minecraft.getInstance(), 0.0F);
        }
        if (com.craisinlord.fightclub.client.LongswordSpinClient.isCharging(player)
                && com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.CHARGE_INDICATOR)) {
            return com.craisinlord.fightclub.client.LongswordSpinClient.progress(Minecraft.getInstance(), 0.0F);
        }
        if (com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.CHARGE_INDICATOR)) {
            float bash = CombatInputClient.bashReadiness(Minecraft.getInstance());
            if (bash < 1.0F) return bash;
        }
        return player.getAttackStrengthScale(adjustTicks);
    }
}
