package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.FightClub;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Set;

public final class MobWeapons {
    private static final String[] LONGSWORD_MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond"};
    private static final int[] LONGSWORD_WEIGHTS = {25, 25, 20, 18, 8, 4};
    private static final String[] DAGGER_MATERIALS = {"stone", "copper", "iron", "gold", "diamond"};
    private static final int[] DAGGER_WEIGHTS = {30, 25, 30, 10, 5};
    private static final float SKELETON_KNIFE_CHANCE = 0.03F;
    private static final Set<String> ZOMBIES = Set.of("zombie", "husk", "zombie_villager");
    private static final Set<String> SKELETONS = Set.of("skeleton", "stray", "bogged");
    private static boolean warned;

    private MobWeapons() {}

    public static boolean zombieLongswords() {
        return CombatConfig.enabled(CombatConfig.ZOMBIE_LONGSWORDS);
    }

    public static boolean skeletonKnives() {
        return CombatConfig.enabled(CombatConfig.SKELETON_KNIVES);
    }

    public static boolean isKnifeSkeleton(Mob mob) {
        return isVanilla(mob, SKELETONS);
    }

    private static boolean isVanilla(Mob mob, Set<String> paths) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id.getNamespace().equals("minecraft") && paths.contains(id.getPath());
    }

    public static boolean isDagger(ItemStack stack) {
        return CombatWeapons.is(stack.getItem(), "dagger");
    }

    public static void equipZombie(Mob mob, RandomSource random, DifficultyInstance difficulty) {
        try {
            if (!zombieLongswords() || !isVanilla(mob, ZOMBIES) || !mob.getMainHandItem().isEmpty()) return;
            float chance = difficulty.getDifficulty() == Difficulty.HARD ? 0.08F : 0.04F;
            if (random.nextFloat() >= chance) return;
            Item item = pick(random, LONGSWORD_MATERIALS, LONGSWORD_WEIGHTS, "longsword");
            if (item != Items.AIR) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    public static void equipSkeleton(Mob mob, RandomSource random) {
        try {
            if (!skeletonKnives() || !isKnifeSkeleton(mob) || random.nextFloat() >= SKELETON_KNIFE_CHANCE) return;
            Item item = pick(random, DAGGER_MATERIALS, DAGGER_WEIGHTS, "dagger");
            if (item == Items.AIR) return;
            ItemStack stack = new ItemStack(item);
            mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.LOYALTY)
                    .ifPresent(loyalty -> stack.enchant(loyalty, 1 + random.nextInt(3)));
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    public static void warn(RuntimeException e) {
        if (warned) return;
        warned = true;
        FightClub.LOGGER.error("Fight Club mob weapons hit an error and skipped it; further errors are not logged", e);
    }

    private static Item pick(RandomSource random, String[] materials, int[] weights, String weapon) {
        int total = 0;
        for (int weight : weights) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < materials.length; i++) {
            roll -= weights[i];
            if (roll < 0) return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(FightClub.MOD_ID, materials[i] + "_" + weapon));
        }
        return Items.AIR;
    }
}
