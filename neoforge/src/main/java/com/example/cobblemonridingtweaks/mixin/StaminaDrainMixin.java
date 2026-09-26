package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.HoverBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.JetBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.RocketBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.land.HorseBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.liquid.DolphinBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.liquid.SubmarineBehaviour;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.example.cobblemonridingtweaks.riding.RidingModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

// All these stamina ticks are client-side; keep this mixin in the client config list.
// Transforming Hover on a dedicated server resolves its client-only frame types.
@Mixin({HorseBehaviour.class, DolphinBehaviour.class, SubmarineBehaviour.class,
        BirdBehaviour.class, HoverBehaviour.class, JetBehaviour.class, RocketBehaviour.class})
public abstract class StaminaDrainMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = "tickStamina", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float cobblemonRidingTweaks$scaleDrainBeforeClamp(
            float minimum, float proposed,
            @Coerce RidingBehaviourSettings settings,
            @Coerce RidingBehaviourState state,
            PokemonEntity vehicle
    ) {
        return Math.max(minimum, RidingModifiers.staminaAfterDrain(proposed,
                (RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>) (Object) this,
                settings, state, vehicle));
    }
}
