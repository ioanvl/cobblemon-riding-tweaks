package com.example.cobblemonridingtweaks.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.CompositeState;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
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
        var configManager = CobblemonRidingTweaks.configManager();
        Stats staminaStat = cobblemonRidingTweaks$stat(configManager.staminaStatKey());
        Nature naturalNature = pokemon.getNature();
        Nature effectiveNature = pokemon.getEffectiveNature();
        boolean statHasNature = !Stats.HP.equals(staminaStat);
        float originalDrain = staminaBefore - staminaAfter;
        float scaledDrain = configManager.scaleDrain(
                originalDrain,
                pokemon.getLevel(),
                cobblemonRidingTweaks$labels(pokemon),
                cobblemonRidingTweaks$speciesId(pokemon),
                cobblemonRidingTweaks$rideStyle(behaviour, settings, state),
                cobblemonRidingTweaks$behaviourKey(behaviour, state),
                pokemon.getIvs().getOrDefault(staminaStat),
                pokemon.getIvs().getEffectiveBattleIV(staminaStat),
                pokemon.getEvs().getOrDefault(staminaStat),
                statHasNature && naturalNature != null && staminaStat.equals(naturalNature.getIncreasedStat()),
                statHasNature && naturalNature != null && staminaStat.equals(naturalNature.getDecreasedStat()),
                statHasNature && effectiveNature != null && staminaStat.equals(effectiveNature.getIncreasedStat()),
                statHasNature && effectiveNature != null && staminaStat.equals(effectiveNature.getDecreasedStat())
        );
        float scaledStamina = Math.max(0.0F, Math.min(1.0F, staminaBefore - scaledDrain));

        if (scaledStamina != staminaAfter) {
            state.getStamina().set(scaledStamina, false);
        }
    }

    @Redirect(
            method = "handleRelativeFrictionAndCalculateMovement$lambda$0(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/phys/Vec3;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;velocity(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 cobblemonRidingTweaks$scaleRelativeFrictionVelocity(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver,
            Vec3 input
    ) {
        return cobblemonRidingTweaks$scaledVelocity(behaviour, settings, state, vehicle, driver, input);
    }

    @Redirect(
            method = "travel$lambda$0(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;velocity(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 cobblemonRidingTweaks$scaleTravelVelocity(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver,
            Vec3 input
    ) {
        return cobblemonRidingTweaks$scaledVelocity(behaviour, settings, state, vehicle, driver, input);
    }

    @Redirect(
            method = "getRiddenInput$lambda$0(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviour;velocity(Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourSettings;Lcom/cobblemon/mod/common/api/riding/behaviour/RidingBehaviourState;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 cobblemonRidingTweaks$scaleRiddenInputVelocity(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver,
            Vec3 input
    ) {
        return cobblemonRidingTweaks$scaledVelocity(behaviour, settings, state, vehicle, driver, input);
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
        double multiplier = cobblemonRidingTweaks$speedMultiplier(
                pokemon,
                cobblemonRidingTweaks$rideStyle(behaviour, settings, state),
                cobblemonRidingTweaks$behaviourKey(behaviour, state)
        );
        return (float) (speed * multiplier);
    }

    private static Vec3 cobblemonRidingTweaks$scaledVelocity(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle,
            Player driver,
            Vec3 input
    ) {
        Vec3 velocity = behaviour.velocity(settings, state, vehicle, driver, input);
        Pokemon pokemon = vehicle.getPokemon();
        String rideStyle = cobblemonRidingTweaks$rideStyle(behaviour, settings, state);
        double multiplier = cobblemonRidingTweaks$speedMultiplier(
                pokemon,
                rideStyle,
                cobblemonRidingTweaks$behaviourKey(behaviour, state)
        );

        if (multiplier == 1.0D) {
            return velocity;
        }
        if ("land".equals(rideStyle)) {
            return velocity.multiply(multiplier, 1.0D, multiplier);
        }
        return velocity.scale(multiplier);
    }

    private static double cobblemonRidingTweaks$speedMultiplier(Pokemon pokemon, String rideStyle, String behaviour) {
        Nature naturalNature = pokemon.getNature();
        Nature effectiveNature = pokemon.getEffectiveNature();
        return CobblemonRidingTweaks.configManager().speedMultiplier(
                pokemon.getLevel(),
                cobblemonRidingTweaks$labels(pokemon),
                cobblemonRidingTweaks$speciesId(pokemon),
                rideStyle,
                behaviour,
                pokemon.getIvs().getOrDefault(Stats.SPEED),
                pokemon.getIvs().getEffectiveBattleIV(Stats.SPEED),
                pokemon.getEvs().getOrDefault(Stats.SPEED),
                naturalNature != null && Stats.SPEED.equals(naturalNature.getIncreasedStat()),
                naturalNature != null && Stats.SPEED.equals(naturalNature.getDecreasedStat()),
                effectiveNature != null && Stats.SPEED.equals(effectiveNature.getIncreasedStat()),
                effectiveNature != null && Stats.SPEED.equals(effectiveNature.getDecreasedStat())
        );
    }

    private static Stats cobblemonRidingTweaks$stat(String statKey) {
        return switch (RidingTweaksConfig.sanitizeStatKey(statKey)) {
            case RidingTweaksConfig.STAT_ATTACK -> Stats.ATTACK;
            case RidingTweaksConfig.STAT_DEFENCE -> Stats.DEFENCE;
            case RidingTweaksConfig.STAT_SPECIAL_ATTACK -> Stats.SPECIAL_ATTACK;
            case RidingTweaksConfig.STAT_SPECIAL_DEFENCE -> Stats.SPECIAL_DEFENCE;
            case RidingTweaksConfig.STAT_SPEED -> Stats.SPEED;
            default -> Stats.HP;
        };
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
