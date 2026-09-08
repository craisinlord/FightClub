package com.craisinlord.combatoverhaul.fabric;

import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

public final class CombatOverhaulFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(CombatOverhaulFabric.DAGGER_PROJECTILE, WeaponProjectileRenderer::new);
        EntityRendererRegistry.register(CombatOverhaulFabric.SPEAR_PROJECTILE, WeaponProjectileRenderer::new);
    }

    private static final class WeaponProjectileRenderer<T extends Entity & net.minecraft.world.entity.projectile.ItemSupplier>
            extends EntityRenderer<T> {
        private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

        private WeaponProjectileRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.itemRenderer = context.getItemRenderer();
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        }

        @Override
        public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            if (entity instanceof DaggerProjectile dagger) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(dagger.isEmbedded() ? 90.0F
                        : (entity.tickCount + partialTick) * 55.0F));
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot()) + 90.0F));
            }
            itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
            poseStack.popPose();
        }
    }
}
