package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.CompositeState;
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
            method = "tickRidden$lambda$0(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;)Lkotlin/Unit;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;tick(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private static void cobblemonRidingTweaks$scaleStaminaDrain(
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
                cobblemonRidingTweaks$behaviourKey(behaviour, state)
        );
        float scaledStamina = Math.max(0.0F, Math.min(1.0F, staminaBefore - scaledDrain));

        if (scaledStamina != staminaAfter) {
            state.getStamina().set(scaledStamina, false);
        }
    }

    @Redirect(
            method = "getRiddenSpeed$lambda$0(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;speed(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;)F"
            )
    )
    private static float cobblemonRidingTweaks$scaleRiddenSpeed(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver
    ) {
        float speed = behaviour.speed(settings, state, vehicle, driver);
        Pokemon pokemon = vehicle.getPokemon();
        double multiplier = CobblemonRidingTweaks.configManager().speedMultiplier(
                pokemon.getLevel(),
                cobblemonRidingTweaks$labels(pokemon),
                cobblemonRidingTweaks$speciesId(pokemon),
                cobblemonRidingTweaks$rideStyle(behaviour, settings, state),
                cobblemonRidingTweaks$behaviourKey(behaviour, state)
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
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourState state
    ) {
        if (state instanceof CompositeState compositeState) {
            ResourceLocation activeBehaviour = compositeState.getActiveBehaviour().get();
            if (activeBehaviour != null) {
                return activeBehaviour.toString();
            }
        }
        return behaviour.getKey().toString();
    }
}
