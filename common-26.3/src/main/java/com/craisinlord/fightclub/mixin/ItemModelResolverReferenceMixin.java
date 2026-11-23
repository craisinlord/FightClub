package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatModels;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverReferenceMixin {
    @Unique
    private static final ItemStackRenderState fightClub$reference = new ItemStackRenderState();
    @Unique
    private static boolean fightClub$resolving;

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void fightClub$referenceTransform(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                              Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        if (fightClub$resolving) return;
        ItemStack reference = CombatModels.inHandReference(item, displayContext);
        if (reference == null) return;
        ItemTransform transform;
        List<SpriteContents> referenceSprites;
        fightClub$resolving = true;
        try {
            fightClub$reference.clear();
            ((ItemModelResolver) (Object) this).appendItemLayers(fightClub$reference, reference, displayContext, level, owner, seed);
            ItemStackRenderStateAccessor referenceLayers = (ItemStackRenderStateAccessor) fightClub$reference;
            if (referenceLayers.fightClub$activeLayerCount() == 0) return;
            transform = ((LayerRenderStateAccessor) referenceLayers.fightClub$layers()[0]).fightClub$itemTransform();
            referenceSprites = fightClub$sprites(fightClub$reference);
        } finally {
            fightClub$resolving = false;
        }
        if (transform == null) return;
        if (CombatModels.matchesTip(item)) {
            transform = CombatModels.rolled(transform, CombatModels.tipRoll(item, fightClub$sprites(output), referenceSprites));
        }
        ItemStackRenderStateAccessor layers = (ItemStackRenderStateAccessor) output;
        for (int i = 0; i < layers.fightClub$activeLayerCount(); i++) layers.fightClub$layers()[i].setItemTransform(transform);
    }

    @Unique
    private static List<SpriteContents> fightClub$sprites(ItemStackRenderState state) {
        ItemStackRenderStateAccessor layers = (ItemStackRenderStateAccessor) state;
        List<SpriteContents> sprites = new ArrayList<>();
        for (int i = 0; i < layers.fightClub$activeLayerCount(); i++) {
            for (BakedQuad quad : ((LayerRenderStateAccessor) layers.fightClub$layers()[i]).fightClub$quads().all()) {
                SpriteContents sprite = quad.materialInfo().sprite().contents();
                if (!sprites.contains(sprite)) sprites.add(sprite);
            }
        }
        return sprites;
    }
}
