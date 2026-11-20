package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.combat.CombatProjectile;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Thrown spears and daggers, shared by both loaders. Renders the weapon's in-hand model (the
 * long spear, the 3D dagger) pointed along its flight like a vanilla arrow or trident. Daggers
 * tumble end over end in the plane of the throw; both wobble briefly when they stick.
 */
public class WeaponProjectileRenderer<T extends CombatProjectile> extends EntityRenderer<T> {
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
    /** How far a stuck spear's point sinks into the surface. */
    private static final float EMBED_DEPTH = 0.12F;
    private final ItemRenderer itemRenderer;

    public WeaponProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    /** The model reaches well behind the entity position (the tip); don't cull it early. */
    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z)
                || entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        boolean embedded = entity.isEmbedded();
        // Stuck projectiles are frozen; use the exact rotation instead of interpolating.
        float yaw = embedded ? entity.getYRot() : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = embedded ? entity.getXRot() : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        // Same convention as vanilla's arrow renderer: afterwards +X points along the flight.
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));

        float wobble = embedded ? entity.wobble(partialTick) : 0.0F;
        if (wobble > 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(wobble * 3.0F) * wobble));

        boolean dagger = entity instanceof DaggerProjectile;
        if (dagger && !embedded) {
            // Tumble end over end around the side axis, so the spin follows the throw direction.
            poseStack.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTick) * DAGGER_SPIN));
        }
        // Point the model's tip down +X, with the tip (not the middle) on the entity position, so the
        // hit point is where the point is and stuck weapons embed by it.
        if (dagger) {
            // The dagger uses its normal item model, tip at the top-right (+X, +Y).
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            // Push the tip into the surface a little so it reads as stuck, not balanced on its point.
            if (embedded) poseStack.translate(0.03F, 0.03F, 0.0F);
        } else {
            // The long spear's in-hand texture runs the other diagonal, tip at (-X, +Y).
            poseStack.translate(-SPEAR_TIP + (embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-135.0F));
            poseStack.scale(SPEAR_SCALE, SPEAR_SCALE, 1.0F);
        }
        if (embedded) {
            // Sitting exactly on the block face, the passed-in light can come from inside the
            // block (black). Light it from a point backed off along the flight instead.
            net.minecraft.core.BlockPos probe = net.minecraft.core.BlockPos.containing(entity.getLightProbePosition(partialTick));
            light = net.minecraft.client.renderer.LightTexture.pack(
                    entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, probe),
                    entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, probe));
        }
        itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }
}
