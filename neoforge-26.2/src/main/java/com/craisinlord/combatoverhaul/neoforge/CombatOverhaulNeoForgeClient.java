package com.craisinlord.combatoverhaul.neoforge;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CombatOverhaulNeoForgeClient {
    private CombatOverhaulNeoForgeClient() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CombatOverhaulNeoForge.DAGGER_PROJECTILE.get(), WeaponProjectileRenderer::new);
        event.registerEntityRenderer(CombatOverhaulNeoForge.SPEAR_PROJECTILE.get(), WeaponProjectileRenderer::new);
    }

    private static final class WeaponProjectileRenderer<T extends net.minecraft.world.entity.Entity & net.minecraft.world.entity.projectile.ItemSupplier>
            extends EntityRenderer<T, WeaponProjectileRenderer.State> {
        private final ItemModelResolver itemModelResolver;
        private WeaponProjectileRenderer(EntityRendererProvider.Context context) { super(context); this.itemModelResolver = context.getItemModelResolver(); }
        @Override public State createRenderState() { return new State(); }
        @Override public void extractRenderState(T entity, State state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            state.embedded = entity instanceof DaggerProjectile dagger ? dagger.isEmbedded() : ((SpearProjectile) entity).isEmbedded();
            state.dagger = entity instanceof DaggerProjectile;
            state.spin = (entity.tickCount + partialTick) * 55.0F;
            state.yaw = entity.getYRot(partialTick);
            state.pitch = entity.getXRot(partialTick);
            itemModelResolver.updateForNonLiving(state.item, entity.getItem(), net.minecraft.world.item.ItemDisplayContext.GROUND, entity);
        }
        @Override public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.mulPose(camera.orientation);
            if (state.dagger) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.embedded ? 90.0F : state.spin));
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw - 90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.pitch + 90.0F));
            }
            state.item.submit(poseStack, collector, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        private static final class State extends ThrownItemRenderState { private boolean embedded; private boolean dagger; private float spin; private float yaw; private float pitch; }
    }
}
