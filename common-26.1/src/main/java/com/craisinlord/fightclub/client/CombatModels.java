package com.craisinlord.fightclub.client;

/**
 * Model tweaks shared by the item renderers. On 26.x the perspective-specific in-hand models come
 * from the {@code items/} model definitions (select on display context), so only the model spin
 * from 1.21.1 is left here.
 */
public final class CombatModels {
    private static float modelSpin;

    private CombatModels() {}

    /**
     * Turns the next item submits' model by this many degrees around its own face normal, pivoting
     * on the model's center, after its display transform (see ItemTransformSpinMixin). Used to
     * re-point a weapon for a different hold without separate model files. Callers reset it to 0
     * right after submitting. Item layers are transformed at submit time, so this is safe even
     * though drawing is deferred.
     */
    public static void setModelSpin(float degrees) {
        modelSpin = degrees;
    }

    public static float modelSpin() {
        return modelSpin;
    }
}
