package com.example.cobblemonridingtweaks.riding;

import net.minecraft.world.phys.Vec3;

public final class RidingMath {
    private RidingMath() {
    }

    public static Vec3 controllerMovement(Vec3 movement, double multiplier) {
        return multiplier == 1.0D ? movement : movement.scale(1.0D / multiplier);
    }

    public static float staminaAfterDrain(float before, float proposed, double endurance) {
        if (proposed >= before) {
            return proposed;
        }
        // Keep the full drain, including the part below zero, before scaling it.
        // Scaling an already-clamped value makes exhaustion approach zero forever.
        if (endurance == 1.0D) {
            return Math.max(0.0F, proposed);
        }
        double drain = ((double) before - proposed) / endurance;
        return (float) Math.max(0.0D, before - drain);
    }
}
