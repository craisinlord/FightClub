package com.craisinlord.combatoverhaul.neoforge;
import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
@EventBusSubscriber(modid = com.craisinlord.combatoverhaul.CombatOverhaul.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CombatOverhaulNeoForgeClient {
    private CombatOverhaulNeoForgeClient() {}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CombatOverhaulNeoForge.DAGGER_PROJECTILE.get(), WeaponProjectileRenderer::new);
        event.registerEntityRenderer(CombatOverhaulNeoForge.SPEAR_PROJECTILE.get(), WeaponProjectileRenderer::new);
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
