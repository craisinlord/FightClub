package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatAttacks;
import com.craisinlord.fightclub.combat.CombatPlatform;
import com.craisinlord.fightclub.combat.CombatProjectile;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.LevelAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CompatAbilities {
    public enum Hit { SWING, PIERCE, ARC, SPIN, THROWN }

    private interface Ability {
        void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) throws ReflectiveOperationException;
        void tooltip(List<Component> lines) throws ReflectiveOperationException;

        default float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
            return amount;
        }

        default void mainHandModifiers(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {}

        default float thrownDamage(ItemStack stack, float damage) {
            return damage;
        }
    }

    private static boolean resolved;
    private static final Map<CompatMaterial, List<Ability>> ABILITIES = new EnumMap<>(CompatMaterial.class);

    private CompatAbilities() {}

    public static CompatMaterial materialOf(ItemStack stack) {
        if (!(stack.getItem() instanceof CombatWeaponItem item)) return null;
        String name = item.combatMaterial();
        for (CompatMaterial material : CompatMaterial.values()) {
            if (material.material.equals(name)) return material;
        }
        return null;
    }

    public static void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {
        if (attacker == null || target == null || attacker.level().isClientSide) return;
        CompatMaterial material = materialOf(stack);
        if (material == null) return;
        List<Ability> abilities = abilities(material);
        for (Ability ability : abilities.toArray(new Ability[0])) {
            try {
                ability.hit(stack, target, attacker, hit);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                FightClub.LOGGER.warn("Fight Club: {} ability failed, disabling it", material.material, e);
                abilities.remove(ability);
            }
        }
    }

    public static boolean hurtThrown(LivingEntity thrower, ItemStack stack, BooleanSupplier hurt) {
        if (thrower == null || thrower.level().isClientSide || materialOf(stack) == null) return hurt.getAsBoolean();
        List<Runnable> undo = new ArrayList<>();
        try {
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                boolean vanilla = attribute.unwrapKey().map(key -> key.location().getNamespace().equals("minecraft")).orElse(true);
                if (vanilla) return;
                AttributeInstance instance = thrower.getAttribute(attribute);
                if (instance == null || instance.hasModifier(modifier.id())) return;
                instance.addTransientModifier(modifier);
                undo.add(() -> instance.removeModifier(modifier.id()));
            });
            return hurt.getAsBoolean();
        } finally {
            undo.forEach(Runnable::run);
        }
    }

    public static void appendTooltip(ItemStack stack, Consumer<Component> tooltip) {
        CompatMaterial material = materialOf(stack);
        if (material == null) return;
        List<Component> lines = new ArrayList<>();
        for (Ability ability : abilities(material).toArray(new Ability[0])) {
            try {
                ability.tooltip(lines);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        lines.forEach(tooltip);
    }

    public static float incomingDamage(LivingEntity target, DamageSource source, float amount) {
        if (target.level().isClientSide || target.isInvulnerableTo(source) || target.isDeadOrDying()) return amount;
        Entity direct = source.getDirectEntity();
        ItemStack weapon = direct instanceof LivingEntity living ? living.getMainHandItem()
                : direct instanceof CombatProjectile projectile ? projectile.getItem() : ItemStack.EMPTY;
        amount = incomingDamage(weapon, target, source, amount, true);
        if (source.getEntity() instanceof LivingEntity owner && owner != direct) {
            amount = incomingDamage(owner.getMainHandItem(), target, source, amount, false);
        }
        return amount;
    }

    private static float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
        CompatMaterial material = materialOf(stack);
        if (material == null) return amount;
        for (Ability ability : abilities(material).toArray(new Ability[0])) {
            try {
                amount = ability.incomingDamage(stack, target, source, amount, direct);
            } catch (RuntimeException | LinkageError e) {
                FightClub.LOGGER.warn("Fight Club: {} damage ability failed", material.material, e);
            }
        }
        return amount;
    }

    public static void forEachMainHand(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        CompatMaterial material = materialOf(stack);
        if (material == null) return;
        for (Ability ability : abilities(material).toArray(new Ability[0])) {
            try {
                ability.mainHandModifiers(stack, action);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    public static float thrownDamage(ItemStack stack, float damage) {
        CompatMaterial material = materialOf(stack);
        if (material == null) return damage;
        for (Ability ability : abilities(material).toArray(new Ability[0])) {
            try {
                damage = ability.thrownDamage(stack, damage);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return damage;
    }

    public static Item.Properties properties(CompatMaterial material) {
        Item.Properties properties = new Item.Properties();
        switch (material) {
            case FIERY -> properties.fireResistant().rarity(Rarity.UNCOMMON);
            case REFINED_OBSIDIAN -> properties.fireResistant();
            case WARDEN -> properties.rarity(Rarity.RARE).fireResistant();
            case UTHERIUM -> properties.rarity(extendedRarity("UNDERGARDEN_UTHERIUM"));
            case FORGOTTEN -> properties.rarity(extendedRarity("UNDERGARDEN_FORGOTTEN"));
            default -> {}
        }
        return properties;
    }

    private static Rarity extendedRarity(String name) {
        try {
            return Rarity.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    private static List<Ability> abilities(CompatMaterial material) {
        if (!resolved) resolve();
        return ABILITIES.getOrDefault(material, List.of());
    }

    private static synchronized void resolve() {
        if (resolved) return;
        if (CombatPlatform.isModLoaded("iceandfire")) IceAndFire.register();
        if (CombatPlatform.isModLoaded("born_in_chaos_v1")) BornInChaos.register();
        if (CombatPlatform.isModLoaded("aether")) Aether.register();
        if (CombatPlatform.isModLoaded("twilightforest")) TwilightForest.register();
        if (CombatPlatform.isModLoaded("undergarden")) Undergarden.register();
        resolved = true;
    }

    private static void add(CompatMaterial material, Ability ability) {
        ABILITIES.computeIfAbsent(material, key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(ability);
    }

    private static final class IceAndFire {
        private static final String PACKAGE = "com.iafenvoy.iceandfire.item.ability.";
        private static Method bonusDamage;

        private record Bonus(float amount, TagKey<EntityType<?>> targetType) {}

        private static String field(CompatMaterial material) {
            return switch (material) {
                case DRAGONSTEEL_FIRE -> "DRAGONSTEEL_FIRE_TOOL";
                case DRAGONSTEEL_ICE -> "DRAGONSTEEL_ICE_TOOL";
                case DRAGONSTEEL_LIGHTNING -> "DRAGONSTEEL_LIGHTNING_TOOL";
                case DRAGONBONE_FIRE -> "FIRE_DRAGON_BLOOD_TOOL";
                case DRAGONBONE_ICE -> "ICE_DRAGON_BLOOD_TOOL";
                case DRAGONBONE_LIGHTNING -> "LIGHTNING_DRAGON_BLOOD_TOOL";
                case SILVER -> "UNDEAD_DAMAGE_BONUS";
                default -> null;
            };
        }

        static void register() {
            try {
                Class<?> builtins = Class.forName(PACKAGE + "BuiltinAbilities");
                Class<?> damageBonus = Class.forName(PACKAGE + "DamageBonusAbility");
                Method active = Class.forName(PACKAGE + "PostHitAbility").getMethod("active", ItemStack.class, LivingEntity.class, LivingEntity.class);
                Method addDescription = Class.forName(PACKAGE + "Ability").getMethod("addDescription", List.class);
                try {
                    bonusDamage = Class.forName("com.iafenvoy.iceandfire.registry.IafDamageTypes").getMethod("bonusDamage", Entity.class);
                } catch (ReflectiveOperationException ignored) {
                }
                for (CompatMaterial material : CompatMaterial.values()) {
                    String field = field(material);
                    if (field == null) continue;
                    try {
                        Object ability = builtins.getField(field).get(null);
                        add(material, ability(ability, active, addDescription, bonuses(ability, damageBonus)));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        FightClub.LOGGER.warn("Fight Club: no Ice and Fire ability {} for {} weapons", field, material.material);
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                FightClub.LOGGER.warn("Fight Club: Ice and Fire weapon abilities unavailable ({})", e.toString());
            }
        }

        private static Ability ability(Object ability, Method active, Method addDescription, List<Bonus> bonuses) {
            return new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) throws ReflectiveOperationException {
                    float attackAnim = attacker.attackAnim;
                    attacker.attackAnim = 0.0f;
                    try {
                        active.invoke(ability, stack, target, attacker);
                    } finally {
                        attacker.attackAnim = attackAnim;
                    }
                    if (bonuses.isEmpty() || !(attacker instanceof Player player) || player.getAttackStrengthScale(0.0f) == 1.0f
                            || !fullStrength(player, hit)) return;
                    for (Bonus bonus : bonuses) {
                        if (target.getType().is(bonus.targetType())) target.hurt(bonusSource(attacker), bonus.amount());
                    }
                }

                @Override
                public void tooltip(List<Component> lines) throws ReflectiveOperationException {
                    addDescription.invoke(ability, lines);
                }
            };
        }

        private static boolean fullStrength(Player player, Hit hit) {
            return switch (hit) {
                case SPIN, THROWN -> true;
                default -> CombatAttacks.capturedStrength(player) >= 0.9f;
            };
        }

        private static DamageSource bonusSource(LivingEntity attacker) {
            if (bonusDamage != null) {
                try {
                    return (DamageSource) bonusDamage.invoke(null, attacker);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    bonusDamage = null;
                }
            }
            return attacker instanceof Player player ? attacker.damageSources().playerAttack(player) : attacker.damageSources().mobAttack(attacker);
        }

        private static List<Bonus> bonuses(Object ability, Class<?> damageBonus) {
            List<Bonus> bonuses = new ArrayList<>();
            if (damageBonus.isInstance(ability)) {
                Bonus bonus = bonus(ability, damageBonus);
                if (bonus != null) bonuses.add(bonus);
                return bonuses;
            }
            for (Field field : ability.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !damageBonus.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Bonus bonus = bonus(field.get(ability), damageBonus);
                    if (bonus != null) bonuses.add(bonus);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            return bonuses;
        }

        @SuppressWarnings("unchecked")
        private static Bonus bonus(Object ability, Class<?> damageBonus) {
            if (ability == null) return null;
            try {
                float amount = (Float) damageBonus.getMethod("bonus").invoke(ability);
                TagKey<EntityType<?>> type = (TagKey<EntityType<?>>) damageBonus.getMethod("targetType").invoke(ability);
                return new Bonus(amount, type);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
    }

    private static boolean fullStrength(LivingEntity attacker, Hit hit) {
        if (!(attacker instanceof Player player) || hit == Hit.SPIN || hit == Hit.THROWN) return true;
        return CombatAttacks.capturedStrength(player) >= 1.0f - 0.5f / player.getCurrentItemAttackStrengthDelay();
    }

    private static final class Aether {
        private static final ResourceLocation ZANITE_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "zanite_weapon_attack_damage");
        private static final TagKey<EntityType<?>> UNLAUNCHABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("aether", "unlaunchable"));

        static void register() {
            add(CompatMaterial.ZANITE, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {}

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("tooltip.fight_club.ability.zanite").withStyle(ChatFormatting.GRAY));
                }

                @Override
                public void mainHandModifiers(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
                    int increase = zaniteIncrease(stack, baseDamage(stack));
                    if (increase > 0) {
                        action.accept(Attributes.ATTACK_DAMAGE, new AttributeModifier(ZANITE_DAMAGE_ID, increase, AttributeModifier.Operation.ADD_VALUE));
                    }
                }

                @Override
                public float thrownDamage(ItemStack stack, float damage) {
                    return damage + zaniteIncrease(stack, damage);
                }
            });
            add(CompatMaterial.GRAVITITE, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {
                    if (!fullStrength(attacker, hit) || target.getType().is(UNLAUNCHABLE)) return;
                    if (!target.onGround() && !target.isInWater() && !target.isInLava()) return;
                    target.push(0.0, 1.0, 0.0);
                    if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("tooltip.fight_club.ability.gravitite").withStyle(ChatFormatting.GRAY));
                }
            });
        }

        private static int zaniteIncrease(ItemStack stack, double base) {
            if (stack.getMaxDamage() <= 0) return 0;
            double buffed = base * (2.0 * stack.getDamageValue() / stack.getMaxDamage() + 0.5);
            return (int) Math.round(Math.max(0.0, buffed - base));
        }

        @SuppressWarnings("deprecation")
        private static double baseDamage(ItemStack stack) {
            ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            if (modifiers.modifiers().isEmpty()) modifiers = stack.getItem().getDefaultAttributeModifiers();
            double total = 0.0;
            for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                if (entry.slot() == EquipmentSlotGroup.MAINHAND && entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()
                        && !entry.modifier().id().equals(ZANITE_DAMAGE_ID)) {
                    total += entry.modifier().amount();
                }
            }
            return total;
        }
    }

    private static final class TwilightForest {
        private static final int KNIGHTMETAL_BONUS_DAMAGE = 2;

        static void register() {
            add(CompatMaterial.KNIGHTMETAL, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {}

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("item.twilightforest.knightmetal_sword.desc").withStyle(ChatFormatting.GRAY));
                }

                @Override
                public float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
                    if (!direct || target.getArmorValue() <= 0) return amount;
                    float cover = target.getArmorCoverPercentage();
                    amount += cover > 0.0f ? (int) (KNIGHTMETAL_BONUS_DAMAGE * cover) : KNIGHTMETAL_BONUS_DAMAGE;
                    if (target.level() instanceof ServerLevel level) {
                        level.getChunkSource().broadcastAndSend(target, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.MAGIC_CRITICAL_HIT));
                    }
                    return amount;
                }
            });
            add(CompatMaterial.FIERY, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {
                    if (!target.fireImmune()) target.igniteForSeconds(15);
                }

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("item.twilightforest.fiery_sword.desc").withStyle(ChatFormatting.GRAY));
                }

                @Override
                public float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
                    if (source.getEntity() instanceof LivingEntity owner && owner.getMainHandItem() == stack && !target.fireImmune()) {
                        target.igniteForSeconds(1);
                    }
                    return amount;
                }
            });
        }
    }

    private static final class Undergarden {
        private static final float BONUS_MULTIPLIER = 1.5f;
        private static final ResourceKey<MobEffect> CHILLY = ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath("undergarden", "chilly"));
        private static final ResourceLocation UTHERIUM_CRIT = ResourceLocation.fromNamespaceAndPath("undergarden", "utherium_crit");
        private static final TagKey<EntityType<?>> ROTSPAWN = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("undergarden", "rotspawn"));
        private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

        static void register() {
            add(CompatMaterial.FROSTSTEEL, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {
                    BuiltInRegistries.MOB_EFFECT.getHolder(CHILLY)
                            .ifPresent(chilly -> target.addEffect(new MobEffectInstance(chilly, 600, 2, false, false)));
                }

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("tooltip.undergarden.froststeel_weapon").withStyle(ChatFormatting.AQUA));
                }
            });
            add(CompatMaterial.UTHERIUM, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {}

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("tooltip.undergarden.utherium_weapon").withStyle(ChatFormatting.RED));
                }

                @Override
                public float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
                    if (!direct || !target.getType().is(ROTSPAWN)) return amount;
                    if (target.level() instanceof ServerLevel level && BuiltInRegistries.PARTICLE_TYPE.get(UTHERIUM_CRIT) instanceof SimpleParticleType crit) {
                        level.sendParticles(crit, target.getX(), target.getY(0.5), target.getZ(), 32,
                                target.getBbWidth() / 4.0, target.getBbHeight() / 4.0, target.getBbWidth() / 4.0, 0.5);
                    }
                    return amount * BONUS_MULTIPLIER;
                }
            });
            add(CompatMaterial.FORGOTTEN, new Ability() {
                @Override
                public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) {}

                @Override
                public void tooltip(List<Component> lines) {
                    lines.add(Component.translatable("tooltip.undergarden.forgotten_weapon").withStyle(ChatFormatting.GREEN));
                }

                @Override
                public float incomingDamage(ItemStack stack, LivingEntity target, DamageSource source, float amount, boolean direct) {
                    if (!direct || target.getType().is(BOSSES)) return amount;
                    if (!BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace().equals("undergarden")) return amount;
                    return amount * BONUS_MULTIPLIER;
                }
            });
        }
    }

    private static final class BornInChaos {
        private static final String PROCEDURE = "net.mcreator.borninchaosv.procedures."
                + "SharpenedDarkMetalSwordKoghdaZhivaiaSushchnostPopadaietSPomoshchiuInstrumientaProcedure";

        static void register() {
            try {
                Method execute = Class.forName(PROCEDURE).getMethod("execute", LevelAccessor.class,
                        double.class, double.class, double.class, Entity.class, Entity.class, ItemStack.class);
                add(CompatMaterial.DARK_METAL, new Ability() {
                    @Override
                    public void hit(ItemStack stack, LivingEntity target, LivingEntity attacker, Hit hit) throws ReflectiveOperationException {
                        execute.invoke(null, target.level(), target.getX(), target.getY(), target.getZ(), target, attacker, stack);
                    }

                    @Override
                    public void tooltip(List<Component> lines) {
                        lines.add(Component.translatable("item.born_in_chaos_v1.sharpened_dark_metal_sword.description_0"));
                    }
                });
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                FightClub.LOGGER.warn("Fight Club: Born in Chaos dark metal ability unavailable ({})", e.toString());
            }
        }
    }
}
