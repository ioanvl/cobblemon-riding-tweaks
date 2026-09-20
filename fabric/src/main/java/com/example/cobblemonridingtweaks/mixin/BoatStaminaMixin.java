package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.liquid.BoatBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.liquid.BoatSettings;
import com.cobblemon.mod.common.api.riding.behaviour.types.liquid.BoatState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.example.cobblemonridingtweaks.riding.RidingModifiers;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BoatBehaviour.class)
public abstract class BoatStaminaMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = "consumeStamina", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float cobblemonRidingTweaks$scaleSprintDrain(
            float minimum, float proposed,
            PokemonEntity vehicle, Player driver, BoatSettings settings, BoatState state
    ) {
        return Math.max(minimum, RidingModifiers.staminaAfterDrain(proposed,
                (RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>) (Object) this,
                settings, state, vehicle));
    }
}
