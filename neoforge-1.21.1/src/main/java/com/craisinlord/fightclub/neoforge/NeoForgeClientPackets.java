package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.combat.CombatPosePayload;

/** Client-side packet handlers, kept in their own class so the server never loads client code. */
final class NeoForgeClientPackets {
    private NeoForgeClientPackets() {}

    static void handlePose(CombatPosePayload payload) {
        CombatClientPoses.handlePacket(payload);
    }
}
