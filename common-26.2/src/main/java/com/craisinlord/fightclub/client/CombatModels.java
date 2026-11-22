package com.craisinlord.fightclub.client;

public final class CombatModels {
    private static float modelSpin;

    private CombatModels() {}

    public static void setModelSpin(float degrees) {
        modelSpin = degrees;
    }

    public static float modelSpin() {
        return modelSpin;
    }

    public static final float TRIDENT_POLE_X = -0.5F, TRIDENT_POLE_Z = -0.5F;

    private static float modelRoll, rollPivotX, rollPivotZ;

    public static void setModelRoll(float degrees, float pivotX, float pivotZ) {
        modelRoll = degrees;
        rollPivotX = pivotX;
        rollPivotZ = pivotZ;
    }

    public static float modelRoll() {
        return modelRoll;
    }

    public static float rollPivotX() {
        return rollPivotX;
    }

    public static float rollPivotZ() {
        return rollPivotZ;
    }
}
