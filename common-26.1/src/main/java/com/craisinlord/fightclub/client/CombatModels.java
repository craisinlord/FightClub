package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CombatModels {
    private static float modelSpin;

    private CombatModels() {}

    public static void setModelSpin(float degrees) {
        modelSpin = degrees;
    }

    public static float modelSpin() {
        return modelSpin;
    }

    public static final float TRIDENT_POLE_X = -0.5F, TRIDENT_POLE_Z = -0.5F;

    private static float modelRoll, rollPivotX, rollPivotZ;

    public static void setModelRoll(float degrees, float pivotX, float pivotZ) {
        modelRoll = degrees;
        rollPivotX = pivotX;
        rollPivotZ = pivotZ;
    }

    public static float modelRoll() {
        return modelRoll;
    }

    public static float rollPivotX() {
        return rollPivotX;
    }

    public static float rollPivotZ() {
        return rollPivotZ;
    }

    public static ItemStack inHandReference(ItemStack stack, ItemDisplayContext context) {
        if (stack.isEmpty() || !inHandContext(context)) return null;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external == null) return null;
        Identifier id = switch (external.weapon()) {
            case "dagger" -> Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "iron_dagger");
            case "longsword" -> Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "iron_longsword");
            case "spear" -> Identifier.withDefaultNamespace("iron_spear");
            default -> null;
        };
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR || item == stack.getItem() ? null : new ItemStack(item);
    }

    public static boolean matchesTip(ItemStack stack) {
        return "spear".equals(CombatWeapons.weapon(stack.getItem()));
    }

    private static final java.util.Map<String, Float> TIP_ROLLS = new java.util.concurrent.ConcurrentHashMap<>();

    public static float tipRoll(ItemStack stack, java.util.List<SpriteContents> sprites, java.util.List<SpriteContents> reference) {
        if (sprites.isEmpty() || reference.isEmpty()) return 0.0F;
        StringBuilder key = new StringBuilder();
        for (SpriteContents sprite : sprites) key.append(sprite.name()).append(';');
        key.append('>');
        for (SpriteContents sprite : reference) key.append(sprite.name()).append(';');
        return TIP_ROLLS.computeIfAbsent(key.toString(), k -> {
            float own = tipCorner(sprites), target = tipCorner(reference);
            return Float.isNaN(own) || Float.isNaN(target) ? 0.0F : own - target;
        });
    }

    private static float tipCorner(java.util.List<SpriteContents> sprites) {
        float mainDiagonal = 0, antiDiagonal = 0, topLeft = 0, topRight = 0, bottomLeft = 0, bottomRight = 0;
        for (SpriteContents sprite : sprites) {
            int width = sprite.width(), height = sprite.height();
            float pixel = 1.0F / (width * height);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (sprite.isTransparent(0, x, y)) continue;
                float u = (x + 0.5F) / width, v = (y + 0.5F) / height;
                if (Math.abs(u - v) <= 0.14F) mainDiagonal += pixel;
                if (Math.abs(u + v - 1.0F) <= 0.14F) antiDiagonal += pixel;
                boolean left = u < 0.35F, right = u >= 0.65F, top = v < 0.35F, bottom = v >= 0.65F;
                if (top && left) topLeft += pixel;
                if (top && right) topRight += pixel;
                if (bottom && left) bottomLeft += pixel;
                if (bottom && right) bottomRight += pixel;
            }
        }
        if (mainDiagonal > antiDiagonal * 1.3F) {
            if (Math.abs(topLeft - bottomRight) < 1.0E-6F) return Float.NaN;
            return topLeft > bottomRight ? 0.0F : 180.0F;
        }
        if (antiDiagonal > mainDiagonal * 1.3F) {
            if (Math.abs(topRight - bottomLeft) < 1.0E-6F) return Float.NaN;
            return topRight > bottomLeft ? 90.0F : -90.0F;
        }
        return Float.NaN;
    }

    private static final java.util.Map<ItemTransform, Float> ROLLS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static final java.util.Map<ItemTransform, java.util.Map<Float, ItemTransform>> ROLLED = new java.util.concurrent.ConcurrentHashMap<>();

    public static ItemTransform rolled(ItemTransform transform, float degrees) {
        if (degrees == 0.0F) return transform;
        return ROLLED.computeIfAbsent(transform, key -> new java.util.concurrent.ConcurrentHashMap<>()).computeIfAbsent(degrees, roll -> {
            ItemTransform copy = new ItemTransform(transform.rotation(), transform.translation(), transform.scale());
            ROLLS.put(copy, roll);
            return copy;
        });
    }

    public static float transformRoll(ItemTransform transform) {
        Float roll = ROLLS.get(transform);
        return roll == null ? 0.0F : roll;
    }

    private static boolean inHandContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.NONE || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
