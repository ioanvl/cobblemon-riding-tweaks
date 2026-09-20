package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.RocketBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.RocketSettings;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.RocketState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.example.cobblemonridingtweaks.riding.RidingMath;
import com.example.cobblemonridingtweaks.riding.RidingModifiers;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RocketBehaviour.class)
public abstract class RocketBehaviourMixin {
    @Redirect(method = "calculateRideSpaceVel", remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 cobblemonRidingTweaks$readUnscaledMovement(PokemonEntity vehicle) {
        double multiplier = RidingModifiers.speedMultiplier(
                vehicle.getPokemon(), "air", RocketBehaviour.Companion.getKEY().toString());
        Vec3 movement = vehicle.getDeltaMovement();
        // Rocket starts from actual world movement instead of its stored ride velocity.
        // Convert that input back to controller units before the output multiplier runs.
        // Reading the current movement preserves collisions and riding-mode transitions.
        return RidingMath.controllerMovement(movement, multiplier);
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "calculateRideSpaceVel", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float cobblemonRidingTweaks$scaleBoostCharge(
            float minimum, float proposed, RocketSettings settings, RocketState state, PokemonEntity vehicle
    ) {
        return Math.max(minimum, RidingModifiers.staminaAfterDrain(proposed,
                (RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>) (Object) this,
                settings, state, vehicle));
    }
}
