package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModGasReactions {
    public static final PrototypeType<GasReactionData> GAS_REACTION_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "gas_reaction"),
            "moonstation14/gas_reaction", GasReactionData.CODEC, ModGasReactions::validateBundledReactions);

    private record RequiredReaction(String path, GasReactionData.EffectType effect, int priority) { }

    private static final List<RequiredReaction> REQUIRED = List.of(
            new RequiredReaction("frezon_production", GasReactionData.EffectType.FREZON_PRODUCTION, 2),
            new RequiredReaction("ammonia_oxygen", GasReactionData.EffectType.AMMONIA_OXYGEN, 2),
            new RequiredReaction("frezon_coolant", GasReactionData.EffectType.FREZON_COOLANT, 1),
            new RequiredReaction("n2o_decomposition", GasReactionData.EffectType.N2O_DECOMPOSITION, 0),
            new RequiredReaction("tritium_fire", GasReactionData.EffectType.TRITIUM_FIRE, -1),
            new RequiredReaction("plasma_fire", GasReactionData.EffectType.PLASMA_FIRE, -2));

    private ModGasReactions() { }

    private static void validateBundledReactions(PrototypeCatalog<GasReactionData> catalog) {
        for (RequiredReaction required : REQUIRED) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, required.path());
            GasReactionData reaction = catalog.get(id);
            if (reaction == null) {
                throw new IllegalArgumentException("Missing required gas reaction '" + id + "' (expected priority "
                        + required.priority() + " and sole effect " + required.effect().id() + ")");
            }
            if (reaction.priority() != required.priority() || reaction.effects().size() != 1
                    || reaction.effects().getFirst().type() != required.effect()) {
                throw new IllegalArgumentException("Gas reaction '" + id + "' must have priority "
                        + required.priority() + " and sole effect " + required.effect().id()
                        + "; found priority " + reaction.priority() + " and effects "
                        + reaction.effects().stream().map(effect -> effect.type().id()).toList());
            }
        }
    }

    public static PrototypeCatalog<GasReactionData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientGasReactions() : PrototypeRuntime.serverGasReactions();
    }
}
