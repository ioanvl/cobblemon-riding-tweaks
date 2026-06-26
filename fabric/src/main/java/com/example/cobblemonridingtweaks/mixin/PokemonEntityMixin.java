package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Locale;

@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMixin {
    @Redirect(
            method = "tickRidden(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;tick(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private void cobblemonRidingTweaks$scaleStaminaDrain(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver,
            Vec3 input
    ) {
        float staminaBefore = state.getStamina().get();
        behaviour.tick(settings, state, vehicle, driver, input);
        float staminaAfter = state.getStamina().get();

        if (staminaAfter >= staminaBefore || staminaBefore <= 0.0F) {
            return;
        }

        Pokemon pokemon = vehicle.getPokemon();
        float originalDrain = staminaBefore - staminaAfter;
        float scaledDrain = CobblemonRidingTweaks.configManager().scaleDrain(
                originalDrain,
                pokemon.getLevel(),
                cobblemonRidingTweaks$labels(pokemon),
                cobblemonRidingTweaks$speciesId(pokemon),
                cobblemonRidingTweaks$rideStyle(behaviour, settings, state),
                cobblemonRidingTweaks$behaviourKey(behaviour)
        );
        float scaledStamina = Math.max(0.0F, Math.min(1.0F, staminaBefore - scaledDrain));

        if (scaledStamina != staminaAfter) {
            state.getStamina().set(scaledStamina, false);
        }
    }

    @Redirect(
            method = "getRiddenSpeed(Lnet/minecraft/world/entity/player/Player;)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;speed(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;)F"
            )
    )
    private float cobblemonRidingTweaks$scaleRiddenSpeed(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver
    ) {
        float speed = behaviour.speed(settings, state, vehicle, driver);
        Pokemon pokemon = vehicle.getPokemon();
        double multiplier = CobblemonRidingTweaks.configManager().speedMultiplier(
                cobblemonRidingTweaks$labels(pokemon),
                cobblemonRidingTweaks$speciesId(pokemon),
                cobblemonRidingTweaks$rideStyle(behaviour, settings, state),
                cobblemonRidingTweaks$behaviourKey(behaviour)
        );
        return (float) (speed * multiplier);
    }

    private static Collection<String> cobblemonRidingTweaks$labels(Pokemon pokemon) {
        return pokemon.getForm().getLabels();
    }

    private static String cobblemonRidingTweaks$speciesId(Pokemon pokemon) {
        ResourceLocation speciesId = pokemon.getSpecies().getResourceIdentifier();
        return speciesId == null ? "" : speciesId.toString();
    }

    private static String cobblemonRidingTweaks$rideStyle(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state
    ) {
        return behaviour.getRidingStyle(settings, state).name().toLowerCase(Locale.ROOT);
    }

    private static String cobblemonRidingTweaks$behaviourKey(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour
    ) {
        return behaviour.getKey().toString();
    }
}
