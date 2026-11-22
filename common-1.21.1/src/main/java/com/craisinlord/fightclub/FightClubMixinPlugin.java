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

    private List<String> devMixins = List.of();

    @Override
    public void onLoad(String mixinPackage) {
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
        return true;
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
