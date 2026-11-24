package com.craisinlord.fightclub.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.SpearProjectile;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemDefaultsMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$spearUse(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (!fightClub$isSpear((Item) (Object) this) && !fightClub$isTrident((Item) (Object) this)) return;
        if (!CombatRecovery.active(player)) {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResult.CONSUME);
        } else {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$vanillaSwordUse(Level level, Player player, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        Item item = (Item) (Object) this;
        if (!fightClub$isVanillaSword(item)) return;
        if (hand == InteractionHand.MAIN_HAND && com.craisinlord.fightclub.combat.CombatWeapons.isShield(player.getOffhandItem())) return;
        cir.setReturnValue(CombatRecovery.active(player) ? InteractionResult.FAIL : InteractionResult.CONSUME);
        if (!CombatRecovery.active(player)) player.startUsingItem(hand);
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$spearUseDuration(ItemStack stack, LivingEntity user,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (fightClub$isSpear((Item) (Object) this) || fightClub$isVanillaSword((Item) (Object) this)) cir.setReturnValue(72000);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$spearUseAnimation(ItemStack stack,
                                                   CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (fightClub$isSpear((Item) (Object) this) || fightClub$isTrident((Item) (Object) this)) cir.setReturnValue(ItemUseAnimation.TRIDENT);
        else if (fightClub$isVanillaSword((Item) (Object) this)) cir.setReturnValue(ItemUseAnimation.BLOCK);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$spearRelease(ItemStack stack, Level level, LivingEntity user, int timeLeft,
                                             CallbackInfoReturnable<Boolean> cir) {
        Item item = (Item) (Object) this;
        if ((!fightClub$isSpear(item) && !fightClub$isTrident(item)) || level.isClientSide() || !(user instanceof Player player)) return;
        if (CombatChargeState.isLeftCharging(player)) {
            CombatChargeState.releaseLeft(player);
            cir.setReturnValue(true);
            return;
        }
        if (CombatRecovery.active(player)) return;
        String material = fightClub$isTrident(item) ? "base" : fightClub$spearMaterial(item);
        String weapon = fightClub$isTrident(item) ? "trident" : "spear";
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        int heldTicks = 72000 - timeLeft;
        if (heldTicks < (spec == null ? 0 : spec.throwChargeTicks())) return;
        if (spec == null) return;
        SpearProjectile projectile = new SpearProjectile(level, player, stack.copy());
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f,
                spec.throwVelocity(), 1.0f);
        level.addFreshEntity(projectile);
        if (fightClub$isTrident(item)) level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        else level.playSound(null, player, com.craisinlord.fightclub.combat.CombatSounds.SPEAR_THROW, SoundSource.PLAYERS, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        CombatRecovery.start(player, spec.recoveryTicks());
        cir.setReturnValue(true);
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void fightClub$vanillaWeaponTooltip(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                                Consumer<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        if ((Object) this instanceof com.craisinlord.fightclub.combat.CombatWeaponItem) return;
        com.craisinlord.fightclub.combat.CombatTooltips.append(stack, tooltip);
    }

    private static boolean fightClub$isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.COPPER_SPEAR
                || item == Items.IRON_SPEAR || item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR
                || item == Items.NETHERITE_SPEAR;
    }

    private static boolean fightClub$isVanillaSword(Item item) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SWORD_BLOCKING)) return false;
        if (com.craisinlord.fightclub.combat.CombatWeapons.external(item) != null) return false;
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD
                || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD
                || item == Items.NETHERITE_SWORD;
    }

    private static boolean fightClub$isTrident(Item item) { return item == Items.TRIDENT; }


    private static String fightClub$spearMaterial(Item item) {
        if (item == Items.WOODEN_SPEAR) return "wood";
        if (item == Items.STONE_SPEAR) return "stone";
        if (item == Items.COPPER_SPEAR) return "copper";
        if (item == Items.IRON_SPEAR) return "iron";
        if (item == Items.GOLDEN_SPEAR) return "gold";
        if (item == Items.DIAMOND_SPEAR) return "diamond";
        return "netherite";
    }

    @Inject(method = "getDefaultMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void fightClub$potionStackSize(CallbackInfoReturnable<Integer> cir) {
        Item item = (Item) (Object) this;
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) cir.setReturnValue(16);
    }
}
