package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.combat.CombatProjectile;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

public class WeaponProjectileRenderer<T extends CombatProjectile> extends EntityRenderer<T, WeaponProjectileRenderer.State> {
    private static final float DAGGER_SPIN = 40.0F;
    private static final float SPEAR_SCALE = 2.0F;
    private static final float SPEAR_SIZE = 0.85F;
    private static final float SPEAR_TIP = 1.38F * SPEAR_SIZE;
    private static final float TRIDENT_TIP = 0.25F;
    private static final float EMBED_DEPTH = 0.12F;
    private final ItemModelResolver itemModelResolver;

    public WeaponProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(T entity, float partialTick) {
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(3.0);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.embedded = entity.isEmbedded();
        state.dagger = entity instanceof DaggerProjectile;
        state.trident = entity.getItem().is(net.minecraft.world.item.Items.TRIDENT);
        state.yaw = state.embedded ? entity.getYRot() : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = state.embedded ? entity.getXRot() : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.wobble = state.embedded ? entity.wobble(partialTick) : 0.0F;
        state.age = entity.tickCount + partialTick;
        itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, state.yaw - 90.0F);
        poseStack.rotateDegrees(Axis.ZP, state.pitch);
        if (state.wobble > 0.0F) poseStack.rotateDegrees(Axis.ZP, -Mth.sin(state.wobble * 3.0F) * state.wobble);
        if (state.dagger && !state.embedded) {
            poseStack.rotateDegrees(Axis.ZP, -state.age * DAGGER_SPIN);
        }
        if (state.trident) {
            poseStack.translate(-TRIDENT_TIP + (state.embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.rotateDegrees(Axis.ZP, -90.0F);
            poseStack.translate(0.5F, 0.5F, 0.5F);
        } else if (state.dagger) {
            poseStack.rotateDegrees(Axis.ZP, -45.0F);
            if (state.embedded) poseStack.translate(0.03F, 0.03F, 0.0F);
        } else {
            poseStack.translate(-SPEAR_TIP + (state.embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.rotateDegrees(Axis.ZP, -135.0F);
            poseStack.scale(SPEAR_SCALE * SPEAR_SIZE, SPEAR_SCALE * SPEAR_SIZE, SPEAR_SIZE);
        }
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static class State extends ThrownItemRenderState {
        boolean embedded;
        boolean dagger;
        boolean trident;
        float yaw;
        float pitch;
        float wobble;
        float age;
    }
}
