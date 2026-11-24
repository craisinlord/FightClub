package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.craisinlord.fightclub.combat.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class OffhandPlacing {
    public static final TagKey<Item> PRIORITY = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "offhand_priority"));
    public static final int COMBAT_TICKS = 60;

    private static boolean placedThisPress;
    private static int combatTicks;

    private OffhandPlacing() {}

    public static void tick(Minecraft client) {
        if (!client.options.keyUse.isDown()) placedThisPress = false;
        if (combatTicks > 0) combatTicks--;
    }

    public static void reset() {
        placedThisPress = false;
        combatTicks = 0;
    }

    public static void onDamage(LivingEntity victim, DamageSource source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || source.getEntity() == null || (victim != player && source.getEntity() != player)) return;
        combatTicks = COMBAT_TICKS;
    }

    public static boolean tryPlace(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || placedThisPress || combatTicks > 0
                || !CombatConfig.enabled(CombatConfig.OFFHAND_PLACING)
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !placeable(player.getOffhandItem()) || !takesRightClick(player, player.getMainHandItem())) return false;
        ItemStack stack = player.getOffhandItem();
        if (!stack.isItemEnabled(player.level().enabledFeatures())) return false;
        int count = stack.getCount();
        InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hit);
        if (result.consumesAction()) {
            if (result.shouldSwing()) {
                player.swing(InteractionHand.OFF_HAND);
                if (!stack.isEmpty() && (stack.getCount() != count || client.gameMode.hasInfiniteItems())) {
                    client.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.OFF_HAND);
                }
            }
            placedThisPress = true;
            return true;
        }
        return false;
    }

    private static boolean placeable(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(PRIORITY)
                || stack.getItem() instanceof BlockItem && !stack.has(DataComponents.FOOD));
    }

    private static boolean takesRightClick(LocalPlayer player, ItemStack stack) {
        if (stack.isEmpty() || stack.getUseDuration(player) <= 0) return false;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external != null && !external.rightClick()) return false;
        return switch (WeaponKind.of(stack)) {
            case SWORD -> stack.getUseAnimation() == UseAnim.BLOCK;
            case LONGSWORD, SPEAR, DAGGER -> true;
            default -> false;
        };
    }
}
