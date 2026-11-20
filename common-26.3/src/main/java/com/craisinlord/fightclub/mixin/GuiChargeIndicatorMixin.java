package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a hold-left-click charge is active, vanilla's attack indicator (crosshair or hotbar,
 * whichever the player chose in Options) shows charge progress instead of attack cooldown.
 * No new UI: it fills the same way, and at full charge it reads as "ready" like a full attack.
 * (26.2 moved the HUD from Gui to Hud; 26.1 still uses Gui.)
 */
@Mixin(Hud.class)
public abstract class GuiChargeIndicatorMixin {
    @Redirect(method = {"extractCrosshair", "extractItemHotbar"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 2)
    private float fightClub$chargeIndicator(LocalPlayer player, float adjustTicks) {
        if (CombatInputClient.isCharging() && CombatConfig.enabled(CombatConfig.CHARGE_INDICATOR)) {
            return CombatInputClient.chargeProgress(Minecraft.getInstance(), 0.0F);
        }
        // Longsword spin charge (hold right-click).
        if (com.craisinlord.fightclub.client.LongswordSpinClient.isCharging(player) && CombatConfig.enabled(CombatConfig.CHARGE_INDICATOR)) {
            return com.craisinlord.fightclub.client.LongswordSpinClient.progress(Minecraft.getInstance(), 0.0F);
        }
        // Shield raised after a bash: the indicator refills as the bash comes off cooldown.
        if (CombatConfig.enabled(CombatConfig.CHARGE_INDICATOR)) {
            float bash = CombatInputClient.bashReadiness(Minecraft.getInstance());
            if (bash < 1.0F) return bash;
        }
        return player.getAttackStrengthScale(adjustTicks);
    }
}
