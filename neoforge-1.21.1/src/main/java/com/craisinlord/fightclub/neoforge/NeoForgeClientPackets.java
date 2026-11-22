package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.combat.CombatPosePayload;

final class NeoForgeClientPackets {
    private NeoForgeClientPackets() {}

    static void handlePose(CombatPosePayload payload) {
        CombatClientPoses.handlePacket(payload);
    }
}
