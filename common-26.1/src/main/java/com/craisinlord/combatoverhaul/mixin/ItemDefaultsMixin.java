package com.craisinlord.combatoverhaul.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
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
import com.craisinlord.combatoverhaul.combat.CombatRecovery;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import com.craisinlord.combatoverhaul.combat.WeaponSpec;
import com.craisinlord.combatoverhaul.combat.WeaponSpecs;
import com.craisinlord.combatoverhaul.CombatOverhaul;
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
    private void combatOverhaul$spearUse(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        CombatOverhaul.LOGGER.info("[TEMP SPEAR MIXIN] use item={} class={} side={} hand={}",
                player.getItemInHand(hand), ((Object) this).getClass().getName(), level.isClientSide() ? "client" : "server", hand);
        if (!combatOverhaul$isSpear((Item) (Object) this)) return;
        if (!CombatRecovery.active(player)) { player.startUsingItem(hand); cir.setReturnValue(InteractionResult.CONSUME); }
        else cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$vanillaSwordUse(Level level, Player player, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        Item item = (Item) (Object) this;
        if (!combatOverhaul$isVanillaSword(item)) return;
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD)) return;
        cir.setReturnValue(CombatRecovery.active(player) ? InteractionResult.FAIL : InteractionResult.CONSUME);
        if (!CombatRecovery.active(player)) player.startUsingItem(hand);
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$spearUseDuration(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (combatOverhaul$isSpear((Item) (Object) this) || combatOverhaul$isVanillaSword((Item) (Object) this)) cir.setReturnValue(72000);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$spearUseAnimation(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (combatOverhaul$isSpear((Item) (Object) this)) cir.setReturnValue(ItemUseAnimation.SPEAR);
        else if (combatOverhaul$isVanillaSword((Item) (Object) this)) cir.setReturnValue(ItemUseAnimation.BLOCK);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$spearRelease(ItemStack stack, Level level, LivingEntity user, int timeLeft, CallbackInfoReturnable<Boolean> cir) {
        Item item = (Item) (Object) this;
        CombatOverhaul.LOGGER.info("[TEMP SPEAR MIXIN] release item={} class={} side={} timeLeft={} isSpear={}",
                stack, item.getClass().getName(), level.isClientSide() ? "client" : "server", timeLeft, combatOverhaul$isSpear(item));
        if (!combatOverhaul$isSpear(item) || level.isClientSide() || !(user instanceof Player player) || CombatRecovery.active(player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(combatOverhaul$spearMaterial(item), "spear");
        if (spec == null) return;
        SpearProjectile projectile = new SpearProjectile(level, player, stack.copy());
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, spec.throwVelocity(), 1.0f);
        level.addFreshEntity(projectile);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        CombatRecovery.start(player, spec.recoveryTicks());
        cir.setReturnValue(true);
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void combatOverhaul$spearTooltip(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        Item item = (Item) (Object) this;
        if (!combatOverhaul$isSpear(item)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(combatOverhaul$spearMaterial(item), "spear");
        if (spec == null) return;
        tooltip.accept(Component.literal("Reach: " + combatOverhaul$format(spec.reach()) + " blocks"));
        tooltip.accept(Component.literal("Attack style: " + spec.attackStyle().name().toLowerCase(java.util.Locale.ROOT)));
        tooltip.accept(Component.literal("Throw: " + combatOverhaul$format(spec.throwVelocity()) + " velocity / " + combatOverhaul$format(spec.throwRange()) + " blocks"));
    }

    private static boolean combatOverhaul$isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.COPPER_SPEAR || item == Items.IRON_SPEAR
                || item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR || item == Items.NETHERITE_SPEAR;
    }

    private static boolean combatOverhaul$isVanillaSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD
                || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD
                || item == Items.NETHERITE_SWORD;
    }
    private static String combatOverhaul$spearMaterial(Item item) {
        if (item == Items.WOODEN_SPEAR) return "wood";
        if (item == Items.STONE_SPEAR) return "stone";
        if (item == Items.COPPER_SPEAR) return "copper";
        if (item == Items.IRON_SPEAR) return "iron";
        if (item == Items.GOLDEN_SPEAR) return "gold";
        if (item == Items.DIAMOND_SPEAR) return "diamond";
        return "netherite";
    }
    private static String combatOverhaul$format(float value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void combatOverhaul$vanillaWeaponTooltip(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        Item item = (Item) (Object) this;
        if (combatOverhaul$isSpear(item)) return;
        String[] key = combatOverhaul$vanillaWeaponKey(item);
        if (key == null) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(key[0], key[1]);
        if (spec == null) return;
        tooltip.accept(Component.literal("Reach: " + combatOverhaul$format(spec.reach()) + " blocks"));
        tooltip.accept(Component.literal("Attack style: " + spec.attackStyle().name().toLowerCase(java.util.Locale.ROOT)));
        tooltip.accept(Component.literal("Thrust width: " + combatOverhaul$format(spec.thrustWidth())));
        tooltip.accept(Component.literal("Sweep: " + combatOverhaul$format(spec.sweepRadius()) + " blocks / " + combatOverhaul$format(spec.sweepAngleDegrees()) + "°"));
        tooltip.accept(Component.literal("Penetration: " + spec.penetrationCount()));
        tooltip.accept(Component.literal("Critical hits: " + (spec.criticalHits() ? "yes" : "no")));
        tooltip.accept(Component.literal("Recovery: " + spec.recoveryTicks() + " ticks"));
        if (spec.throwing()) tooltip.accept(Component.literal("Throw: " + combatOverhaul$format(spec.throwVelocity()) + " velocity / " + combatOverhaul$format(spec.throwRange()) + " blocks"));
    }
    private static String[] combatOverhaul$vanillaWeaponKey(Item item) {
        if (item == Items.MACE) return new String[]{"base", "mace"};
        if (item == Items.TRIDENT) return new String[]{"base", "trident"};
        String[] materials = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
        Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        Item[] axes = {Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
        for (int i = 0; i < materials.length; i++) { if (item == swords[i]) return new String[]{materials[i], "sword"}; if (item == axes[i]) return new String[]{materials[i], "axe"}; }
        return null;
    }
    @Inject(method = "getDefaultMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void combatOverhaul$potionStackSize(CallbackInfoReturnable<Integer> cir) {
        Item item = (Item) (Object) this;
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) cir.setReturnValue(16);
    }

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void combatOverhaul$tridentLoyalty(ItemStack stack, ServerLevel level, Entity entity,
                                               EquipmentSlot slot, CallbackInfo ci) {
        if ((Object) this != Items.TRIDENT) return;
        HolderLookup.Provider registries = level.registryAccess();
        var loyalty = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY);
        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, stack) >= 3) return;
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(stack.getEnchantments());
        enchantments.set(loyalty, 3);
        EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
    }
}
