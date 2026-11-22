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

public class WeaponProjectileRenderer<T extends CombatProjectile> extends EntityRenderer<T> {
    private static final float DAGGER_SPIN = 40.0F;
    private static final float SPEAR_SCALE = 2.0F;
    private static final float SPEAR_SIZE = 0.85F;
    private static final float SPEAR_TIP = 1.38F * SPEAR_SIZE;
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

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z)
                || entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        boolean embedded = entity.isEmbedded();
        float yaw = embedded ? entity.getYRot() : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = embedded ? entity.getXRot() : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));

        float wobble = embedded ? entity.wobble(partialTick) : 0.0F;
        if (wobble > 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(wobble * 3.0F) * wobble));

        boolean dagger = entity instanceof DaggerProjectile;
        if (dagger && !embedded) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTick) * DAGGER_SPIN));
        }
        if (dagger) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            if (embedded) poseStack.translate(0.03F, 0.03F, 0.0F);
        } else {
            poseStack.translate(-SPEAR_TIP + (embedded ? EMBED_DEPTH : 0.0F), 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-135.0F));
            poseStack.scale(SPEAR_SCALE * SPEAR_SIZE, SPEAR_SCALE * SPEAR_SIZE, SPEAR_SIZE);
        }
        if (embedded) {
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
