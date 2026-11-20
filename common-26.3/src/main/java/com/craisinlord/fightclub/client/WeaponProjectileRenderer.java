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

/**
 * Thrown spears and daggers, shared by both loaders (ported from 1.21.1). Renders the weapon's
 * in-hand model (the long spear, the dagger) pointed along its flight like a vanilla arrow or
 * trident. Daggers tumble end over end in the plane of the throw; both wobble briefly when they
 * stick. Lighting comes from {@link CombatProjectile#getLightProbePosition}, which vanilla's
 * extractRenderState already samples.
 */
public class WeaponProjectileRenderer<T extends CombatProjectile> extends EntityRenderer<T, WeaponProjectileRenderer.State> {
    /** Degrees per tick a thrown dagger tumbles. */
    private static final float DAGGER_SPIN = 40.0F;
    /**
     * The spear's in-hand sprite is 32px, so at NONE (no display transform) a texel is 1/32 block.
     * Doubled in the sprite plane it matches the dagger and the held spear (1/16-block texels);
     * item/generated already extrudes 1/16 block, so depth stays 1.
     */
    private static final float SPEAR_SCALE = 2.0F;
    /** Distance from the spear model's centre to its tip along the flight, after SPEAR_SCALE. */
    private static final float SPEAR_TIP = 1.38F;
    /** The 3D trident's point is 4px (0.25 block) ahead of its model origin at the head. */
    private static final float TRIDENT_TIP = 0.25F;
    /** How far a stuck spear's or trident's point sinks into the surface. */
    private static final float EMBED_DEPTH = 0.12F;
    private final ItemModelResolver itemModelResolver;

    public WeaponProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /** The model reaches well behind the entity position (the tip); don't cull it early. */
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
        // Stuck projectiles are frozen; use the exact rotation instead of interpolating.
        state.yaw = state.embedded ? entity.getYRot() : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = state.embedded ? entity.getXRot() : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.wobble = state.embedded ? entity.wobble(partialTick) : 0.0F;
        state.age = entity.tickCount + partialTick;
        // NONE picks the in-hand model (the items/ definitions only use the icon for gui/ground/fixed).
        itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Same convention as vanilla's arrow renderer: afterwards +X points along the flight.
        poseStack.rotateDegrees(Axis.YP, state.yaw - 90.0F);
        poseStack.rotateDegrees(Axis.ZP, state.pitch);
        if (state.wobble > 0.0F) poseStack.rotateDegrees(Axis.ZP, -Mth.sin(state.wobble * 3.0F) * state.wobble);
        if (state.dagger && !state.embedded) {
            // Tumble end over end around the side axis, so the spin follows the throw direction.
            poseStack.rotateDegrees(Axis.ZP, -state.age * DAGGER_SPIN);
        }
        // Point the model's tip down +X, with the tip (not the middle) on the entity position, so the
        // hit point is where the point is and stuck weapons embed by it.
        if (state.trident) {
            // Vanilla's 3D trident model: tip straight up (+Y) with its origin at the head. Turn it
            // onto +X and undo the item transform's -0.5 centring shift, so the head sits on the
            // entity position; then its point sits TRIDENT_TIP ahead, so pull it back by that.
            poseStack.translate(-TRIDENT_TIP + (state.embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.rotateDegrees(Axis.ZP, -90.0F);
            poseStack.translate(0.5F, 0.5F, 0.5F);
        } else if (state.dagger) {
            // The dagger's item model has its tip at the top-right (+X, +Y).
            poseStack.rotateDegrees(Axis.ZP, -45.0F);
            // Push the tip into the surface a little so it reads as stuck, not balanced on its point.
            if (state.embedded) poseStack.translate(0.03F, 0.03F, 0.0F);
        } else {
            // The long spear's in-hand texture runs the other diagonal, tip at (-X, +Y).
            poseStack.translate(-SPEAR_TIP + (state.embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.rotateDegrees(Axis.ZP, -135.0F);
            poseStack.scale(SPEAR_SCALE, SPEAR_SCALE, 1.0F);
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
