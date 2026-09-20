package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdSettings;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.example.cobblemonridingtweaks.riding.RidingMath;
import com.example.cobblemonridingtweaks.riding.RidingModifiers;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BirdBehaviour.class)
public abstract class BirdBehaviourMixin {
    @Redirect(method = "calculateRideSpaceVel", remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 cobblemonRidingTweaks$readUnscaledCollisionMovement(PokemonEntity vehicle) {
        double multiplier = RidingModifiers.speedMultiplier(
                vehicle.getPokemon(), "air", BirdBehaviour.Companion.getKEY().toString());
        // Collision recovery copies world speed back into the controller's velocity.
        // Undo our output scale here so each glancing contact cannot multiply it again.
        return RidingMath.controllerMovement(vehicle.getDeltaMovement(), multiplier);
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "tickStamina", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"))
    private float cobblemonRidingTweaks$scaleGlidingDrain(
            float maximum, float proposed, BirdSettings settings, BirdState state, PokemonEntity vehicle
    ) {
        // Gliding can drain or replenish stamina. Only scale the drain.
        return Math.min(maximum, RidingModifiers.staminaAfterDrain(proposed,
                (RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>) (Object) this,
                settings, state, vehicle));
    }
}
