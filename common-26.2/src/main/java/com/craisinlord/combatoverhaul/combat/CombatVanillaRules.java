package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class CombatVanillaRules {
    private static final Identifier LONGSWORD_OFFHAND = Identifier.fromNamespaceAndPath("combat_overhaul", "longsword_offhand");
    private CombatVanillaRules() {}

    public static boolean isSword(ItemStack stack) { return stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("sword") || isVanillaSword(stack.getItem()); }
    public static boolean isVanillaSword(Item item) { return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD; }
    public static WeaponSpec swordSpec(ItemStack stack) {
        if (stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("sword")) return WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "sword");
        String[] materials = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
        Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        for (int i = 0; i < swords.length; i++) if (stack.getItem() == swords[i]) return WeaponSpecs.getOrLoadBuiltin(materials[i], "sword");
        return null;
    }
    public static boolean isShield(ItemStack stack) { return stack.is(Items.SHIELD); }
    public static boolean isMace(ItemStack stack) { return stack.is(Items.MACE); }

    public static void applyLongswordRules(Player player) {
        if (player.getMainHandItem().getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("longsword")) {
            player.resetFallDistance();
            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.removeModifier(LONGSWORD_OFFHAND);
                WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "longsword");
                if (spec != null && !player.getOffhandItem().isEmpty()) {
                    attackSpeed.addTransientModifier(new AttributeModifier(LONGSWORD_OFFHAND,
                            spec.offhandSpeedMultiplier() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    public static boolean swordBlocks(Player defender, Entity attacker) {
        if (!defender.isUsingItem() || !isSword(defender.getUseItem()) || !(attacker instanceof LivingEntity)) return false;
        WeaponSpec spec = swordSpec(defender.getUseItem());
        return spec != null && spec.blocking() && frontal(defender, attacker, spec.blockingAngleDegrees());
    }
    public static float swordBlockDamageMultiplier(ItemStack stack) {
        WeaponSpec spec = swordSpec(stack);
        return spec == null ? 0.45f : spec.blockingDamageMultiplier();
    }

    public static boolean frontal(LivingEntity defender, Entity attacker, float angle) {
        Vec3 toAttacker = attacker.position().subtract(defender.position());
        if (toAttacker.lengthSqr() < 0.0001) return true;
        return defender.getLookAngle().dot(toAttacker.normalize()) >= Math.cos(Math.toRadians(angle * 0.5));
    }

    public static boolean shieldBash(Player player, Entity target) {
        if (!player.isUsingItem() || !isShield(player.getUseItem()) || player.getCooldowns().isOnCooldown(player.getUseItem())) return false;
        if (!frontal(player, target, 90.0f)) return false;
        CombatPlatform.knockback((LivingEntity) target, 1.6, player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        player.getCooldowns().addCooldown(player.getUseItem(), 25);
        return true;
    }

    public static boolean maceSmash(Player player, Entity target) {
        if (player.level().isClientSide()) return false;
        if (!isMace(player.getMainHandItem()) || !player.isFallFlying() && player.getDeltaMovement().y >= -0.35) return false;
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) return false;
        if (!(target instanceof LivingEntity living) || !frontal(player, target, 100.0f)) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "mace");
        float damage = spec == null ? 12.0f : Math.max(spec.attackDamage(), spec.chargeDamage());
        living.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().playerAttack(player), damage);
        CombatPlatform.knockback(living, 1.1, player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        player.getCooldowns().addCooldown(player.getMainHandItem(), 30);
        return true;
    }
}
