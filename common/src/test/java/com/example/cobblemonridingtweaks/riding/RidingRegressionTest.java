package com.example.cobblemonridingtweaks.riding;

import net.minecraft.world.phys.Vec3;

/** Numerical regressions for the hooks; full controller/gameplay testing is separate. */
public final class RidingRegressionTest {
    public static void main(String[] args) {
        for (double multiplier : new double[]{0.01, 0.5, 1.0, 1.5, 2.0, 3.0, 10.0}) {
            staminaExhausts(multiplier);
            rocketCoasts(multiplier);
            rocketAccelerates(multiplier);
            birdGlancingContacts(multiplier, true);
            birdGlancingContacts(multiplier, false);
        }
        exhaustionChecksSeeScaledStamina();
        rocketChargesScale();
        recoveryIsUnchanged();
        collisionAndConfigChangesPreserveMomentum();
        birdCollisionStops();
        System.out.println("Riding regressions passed: finite exhaustion, exhaustion state, Rocket charges, "
                + "recovery, coasting, acceleration, repeated Bird wall/ceiling contacts, collisions and config changes.");
    }

    private static void staminaExhausts(double multiplier) {
        float stamina = 1.0F;
        int ticks = 0;
        while (stamina > 0.0F && ticks < 2000) {
            stamina = RidingMath.staminaAfterDrain(stamina, stamina - 0.01F, multiplier);
            ticks++;
        }
        check(stamina == 0.0F, "Stamina must reach exactly zero at x" + multiplier);
        check(Math.abs(ticks - 100 * multiplier) <= 1, "Unexpected exhaustion time at x" + multiplier + ": " + ticks);
        check(RidingMath.staminaAfterDrain(0.0F, -0.01F, multiplier) == 0.0F, "Exhausted stamina must stay zero");
        check(RidingMath.staminaAfterDrain(Float.MIN_VALUE, -0.01F, multiplier) == 0.0F, "Tiny residual must exhaust");
    }

    private static void exhaustionChecksSeeScaledStamina() {
        // Boat checks this value immediately to decide whether to stop sprinting.
        float stamina = RidingMath.staminaAfterDrain(0.0075F, 0.0075F - 0.01F, 2.0);
        check(stamina > 0.0F, "Sprint must continue when scaled stamina remains");
        stamina = RidingMath.staminaAfterDrain(stamina, stamina - 0.01F, 2.0);
        check(stamina == 0.0F, "Sprint must stop on actual exhaustion");
        // Lower endurance must also reach zero before Jet/Bird exhaustion checks.
        check(RidingMath.staminaAfterDrain(0.015F, 0.005F, 0.5) == 0.0F, "Early exhaustion must be visible");
    }

    private static void rocketChargesScale() {
        float stamina = RidingMath.staminaAfterDrain(1.0F, 1.0F - 0.01F, 10.0);
        close(stamina, 0.999, 1e-7, "Rocket regular drain");
        stamina = RidingMath.staminaAfterDrain(stamina, stamina - 0.05F, 10.0);
        close(stamina, 0.994, 1e-7, "Rocket boost charge");
        check(RidingMath.staminaAfterDrain(0.002F, 0.002F - 0.05F, 10.0) == 0.0F, "Boost charge can exhaust");
    }

    private static void recoveryIsUnchanged() {
        for (double multiplier : new double[]{0.5, 1.0, 2.0, 10.0}) {
            check(RidingMath.staminaAfterDrain(0.25F, 0.3F, multiplier) == 0.3F, "Recovery must be unchanged");
            check(RidingMath.staminaAfterDrain(1.0F, 1.0F, multiplier) == 1.0F, "No drain must stay unchanged");
            check(Math.min(1.0F, RidingMath.staminaAfterDrain(0.99F, 1.04F, multiplier)) == 1.0F,
                    "Upstream recovery cap must still apply");
        }
        check(RidingMath.staminaAfterDrain(0.25F, 0.24F, 1.0) == 0.24F, "Neutral drain must match upstream");
    }

    private static void rocketCoasts(double multiplier) {
        Vec3 world = new Vec3(0.1, 0.0, 0.2);
        for (int tick = 0; tick < 1000; tick++) {
            Vec3 controller = RidingMath.controllerMovement(world, multiplier);
            // Horizontal Rocket coasting has no acceleration or damping.
            world = controller.scale(multiplier);
        }
        close(world.x, 0.1, 1e-10, "Rocket coasting X at x" + multiplier);
        close(world.z, 0.2, 1e-10, "Rocket coasting Z at x" + multiplier);
    }

    private static void rocketAccelerates(double multiplier) {
        Vec3 world = Vec3.ZERO;
        double topSpeed = 0.5;
        double acceleration = 0.01;
        for (int tick = 0; tick < 1000; tick++) {
            Vec3 controller = RidingMath.controllerMovement(world, multiplier);
            if (controller.horizontalDistance() <= topSpeed) {
                controller = controller.add(0, 0, acceleration);
            }
            world = controller.scale(multiplier);
        }
        check(world.z >= topSpeed * multiplier - 1e-9, "Rocket should reach scaled top speed");
        check(world.z <= (topSpeed + acceleration) * multiplier + 1e-9, "Rocket acceleration must remain bounded");
    }

    private static void collisionAndConfigChangesPreserveMomentum() {
        Vec3 world = new Vec3(0.0, -0.2, 0.4); // Collision has zeroed X.
        for (double multiplier : new double[]{2.0, 0.5, 10.0, 1.0}) {
            world = RidingMath.controllerMovement(world, multiplier).scale(multiplier);
            check(world.x == 0.0, "Collision-stopped axis must stay stopped");
            close(world.y, -0.2, 1e-12, "Config/mode changes preserve vertical momentum");
            close(world.z, 0.4, 1e-12, "Config/mode changes preserve horizontal momentum");
        }
    }

    private static void birdGlancingContacts(double multiplier, boolean wall) {
        // Compare against the same collision at neutral speed, including a fast dive.
        // A generic top-speed cap would fail to preserve these incoming speeds.
        for (double initialSpeed : new double[]{0.4, 4.0}) {
            Vec3 direction = (wall ? new Vec3(0.01, 0, 1) : new Vec3(0, 0.01, 1)).normalize();
            Vec3 control = direction.scale(initialSpeed);
            Vec3 scaled = control;
            for (int contact = 0; contact < 1000; contact++) {
                Vec3 controlWorld = blockedMovement(control, wall);
                Vec3 scaledWorld = blockedMovement(scaled.scale(multiplier), wall);
                // Bird preserves its controller direction and copies the post-collision magnitude.
                control = control.normalize().scale(controlWorld.length());
                scaled = scaled.normalize().scale(RidingMath.controllerMovement(scaledWorld, multiplier).length());
                close(scaled.length(), control.length(), 1e-9,
                        "Bird " + (wall ? "wall" : "ceiling") + " contact " + contact + " at x" + multiplier);
                check(scaled.length() <= initialSpeed + 1e-9, "Contact must not generate extra speed");
            }
        }
    }

    private static Vec3 blockedMovement(Vec3 world, boolean wall) {
        return wall ? new Vec3(0, world.y, world.z) : new Vec3(world.x, 0, world.z);
    }

    private static void birdCollisionStops() {
        Vec3 direction = new Vec3(0, 0, 1);
        for (double multiplier : new double[]{0.01, 0.5, 1.0, 2.0, 10.0}) {
            Vec3 stopped = direction.scale(RidingMath.controllerMovement(Vec3.ZERO, multiplier).length());
            check(stopped.lengthSqr() == 0.0, "Head-on collision must remain stopped");
        }
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
