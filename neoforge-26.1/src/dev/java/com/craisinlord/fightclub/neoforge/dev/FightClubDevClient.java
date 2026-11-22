package com.craisinlord.fightclub.neoforge.dev;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FightClub.MOD_ID, dist = Dist.CLIENT)
public final class FightClubDevClient {
    public FightClubDevClient() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> DevCompatTest26.tick(Minecraft.getInstance()));
    }
}
