package com.example.cobblemonridingtweaks.riding;

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

import java.util.Collection;
import java.util.Locale;

public final class RidingModifiers {
    private RidingModifiers() {
    }

    public static float staminaAfterDrain(
            float proposed,
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state,
            PokemonEntity vehicle
    ) {
        float before = state.getStamina().get();
        if (proposed >= before) {
            return proposed;
        }
        Pokemon pokemon = vehicle.getPokemon();
        var configManager = CobblemonRidingTweaks.configManager();
        Stats staminaStat = stat(configManager.staminaStatKey());
        Nature naturalNature = pokemon.getNature();
        Nature effectiveNature = pokemon.getEffectiveNature();
        boolean statHasNature = !Stats.HP.equals(staminaStat);
        double endurance = configManager.enduranceMultiplier(
                pokemon.getLevel(),
                labels(pokemon),
                speciesId(pokemon),
                rideStyle(behaviour, settings, state),
                behaviourKey(behaviour, state),
                pokemon.getIvs().getOrDefault(staminaStat),
                pokemon.getIvs().getEffectiveBattleIV(staminaStat),
                pokemon.getEvs().getOrDefault(staminaStat),
                statHasNature && naturalNature != null && staminaStat.equals(naturalNature.getIncreasedStat()),
                statHasNature && naturalNature != null && staminaStat.equals(naturalNature.getDecreasedStat()),
                statHasNature && effectiveNature != null && staminaStat.equals(effectiveNature.getIncreasedStat()),
                statHasNature && effectiveNature != null && staminaStat.equals(effectiveNature.getDecreasedStat())
        );
        return RidingMath.staminaAfterDrain(before, proposed, endurance);
    }

    public static double speedMultiplier(Pokemon pokemon, String rideStyle, String behaviour) {
        Nature naturalNature = pokemon.getNature();
        Nature effectiveNature = pokemon.getEffectiveNature();
        return CobblemonRidingTweaks.configManager().speedMultiplier(
                pokemon.getLevel(),
                labels(pokemon),
                speciesId(pokemon),
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

    private static Stats stat(String statKey) {
        return switch (RidingTweaksConfig.sanitizeStatKey(statKey)) {
            case RidingTweaksConfig.STAT_ATTACK -> Stats.ATTACK;
            case RidingTweaksConfig.STAT_DEFENCE -> Stats.DEFENCE;
            case RidingTweaksConfig.STAT_SPECIAL_ATTACK -> Stats.SPECIAL_ATTACK;
            case RidingTweaksConfig.STAT_SPECIAL_DEFENCE -> Stats.SPECIAL_DEFENCE;
            case RidingTweaksConfig.STAT_SPEED -> Stats.SPEED;
            default -> Stats.HP;
        };
    }

    private static Collection<String> labels(Pokemon pokemon) {
        return pokemon.getForm().getLabels();
    }

    private static String speciesId(Pokemon pokemon) {
        ResourceLocation speciesId = pokemon.getSpecies().getResourceIdentifier();
        return speciesId == null ? "" : speciesId.toString();
    }

    public static String rideStyle(
            RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> behaviour,
            RidingBehaviourSettings settings,
            RidingBehaviourState state
    ) {
        return behaviour.getRidingStyle(settings, state).name().toLowerCase(Locale.ROOT);
    }

    public static String behaviourKey(
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
