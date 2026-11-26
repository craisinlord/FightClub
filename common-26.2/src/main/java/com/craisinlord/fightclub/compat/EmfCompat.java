package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatPlatform;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class EmfCompat {
    public static final String MOD_ID = "entity_model_features";
    private static final String API = "traben.entity_model_features.EMFAnimationApi";
    private static final Set<UUID> PAUSED = new HashSet<>();
    private static Boolean available;
    private static Method emfEntityOf;
    private static Method pauseParts;
    private static Method pauseEntity;
    private static Method resume;

    private EmfCompat() {}

    public static boolean isLoaded() {
        if (available == null) available = CombatPlatform.isModLoaded(MOD_ID) && bind();
        return available;
    }

    private static boolean bind() {
        try {
            Class<?> api = Class.forName(API);
            emfEntityOf = api.getMethod("emfEntityOf", Entity.class);
            Class<?> emfEntity = emfEntityOf.getReturnType();
            resume = api.getMethod("resumeAllCustomAnimationsForEntity", emfEntity);
            try {
                pauseParts = api.getMethod("pauseCustomAnimationsForThesePartsOfEntity", emfEntity, ModelPart[].class);
            } catch (NoSuchMethodException exception) {
                pauseEntity = api.getMethod("pauseAllCustomAnimationsForEntity", emfEntity);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            FightClub.LOGGER.warn("Fight Club: couldn't hook Entity Model Features animations: {}", exception.toString());
            return false;
        }
    }

    public static float[] snapshot(HumanoidModel<?> model) {
        float[] values = new float[24];
        int index = 0;
        for (ModelPart part : parts(model)) {
            values[index++] = part.xRot;
            values[index++] = part.yRot;
            values[index++] = part.zRot;
            values[index++] = part.x;
            values[index++] = part.y;
            values[index++] = part.z;
        }
        return values;
    }

    public static boolean changed(float[] before, HumanoidModel<?> model) {
        float[] after = snapshot(model);
        for (int i = 0; i < before.length; i++) {
            if (Float.compare(before[i], after[i]) != 0) return true;
        }
        return false;
    }

    public static void update(Player player, HumanoidModel<?> model, boolean posed) {
        if (!isLoaded()) return;
        UUID id = player.getUUID();
        if (posed == PAUSED.contains(id)) return;
        try {
            Object entity = emfEntityOf.invoke(null, player);
            if (posed) {
                Object result = pauseParts != null
                        ? pauseParts.invoke(null, entity, (Object) parts(model))
                        : pauseEntity.invoke(null, entity);
                if (Boolean.TRUE.equals(result)) PAUSED.add(id);
            } else {
                resume.invoke(null, entity);
                PAUSED.remove(id);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FightClub.LOGGER.warn("Fight Club: Entity Model Features animation pause failed, disabling it: {}", exception.toString());
            available = false;
            PAUSED.clear();
        }
    }

    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.rightArm, model.leftArm, model.body, model.head};
    }
}
