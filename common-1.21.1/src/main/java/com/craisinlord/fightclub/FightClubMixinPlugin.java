package com.craisinlord.fightclub;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public final class FightClubMixinPlugin implements IMixinConfigPlugin {
    private static final String DEV_MIXINS = "/fight_club.dev_mixins.txt";

    private static final List<String> ANVIL_LIMIT_MODS = List.of("ante");
    private static final Set<String> ANVIL_MIXINS = Set.of("AnvilMenuMixin", "AnvilScreenMixin");

    private List<String> devMixins = List.of();
    private String anvilLimitMod;

    @Override
    public void onLoad(String mixinPackage) {
        anvilLimitMod = ANVIL_LIMIT_MODS.stream().filter(FightClubMixinPlugin::modLoaded).findFirst().orElse(null);
        if (anvilLimitMod != null) {
            org.slf4j.LoggerFactory.getLogger("Fight Club").warn("Fight Club: {} is installed, so remove_anvil_limit is handled by it instead", anvilLimitMod);
        }
        if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) return;
        try (InputStream in = FightClubMixinPlugin.class.getResourceAsStream(DEV_MIXINS)) {
            if (in == null) return;
            devMixins = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read " + DEV_MIXINS, e);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return anvilLimitMod == null || !ANVIL_MIXINS.contains(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
    }

    private static boolean modLoaded(String modId) {
        try {
            Class<?> fabric = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabric.getMethod("getInstance").invoke(null);
            return (Boolean) fabric.getMethod("isModLoaded", String.class).invoke(loader, modId);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
        try {
            Object mods = Class.forName("net.neoforged.fml.loading.FMLLoader").getMethod("getLoadingModList").invoke(null);
            return mods.getClass().getMethod("getModFileById", String.class).invoke(mods, modId) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return devMixins.isEmpty() ? null : devMixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
