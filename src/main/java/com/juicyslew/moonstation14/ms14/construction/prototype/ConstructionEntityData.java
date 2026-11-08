package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponData;
import java.util.Map;

/** Locally supported runtime policy for a resolved graph entity; not a generic SS14 component mirror. */
public record ConstructionEntityData(String block, boolean defaultAnchored, boolean anchorable,
                                     boolean unanchorable, double anchoringSeconds, boolean airtight,
                                      java.util.Optional<RadiationBlockerProfile> radiationBlocker,
                                      java.util.Optional<DamageModifierData> structuralContact) {
    public static final Codec<ConstructionEntityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("block").forGetter(ConstructionEntityData::block),
            Codec.BOOL.optionalFieldOf("defaultAnchored", true).forGetter(ConstructionEntityData::defaultAnchored),
            Codec.BOOL.optionalFieldOf("anchorable", false).forGetter(ConstructionEntityData::anchorable),
            Codec.BOOL.optionalFieldOf("unanchorable", false).forGetter(ConstructionEntityData::unanchorable),
            Codec.DOUBLE.optionalFieldOf("anchoringSeconds", 1.0).forGetter(ConstructionEntityData::anchoringSeconds),
            Codec.BOOL.optionalFieldOf("airtight", true).forGetter(ConstructionEntityData::airtight),
            Codec.DOUBLE.optionalFieldOf("radiationBlockerResistance").xmap(
                    value -> value.map(RadiationBlockerProfile::new),
                    value -> value.map(RadiationBlockerProfile::resistance))
                    .forGetter(ConstructionEntityData::radiationBlocker),
            DamageModifierData.CODEC.optionalFieldOf("structuralContact").forGetter(ConstructionEntityData::structuralContact)
    ).apply(i, (block, anchored, anchorable, unanchorable, seconds, airtight, radiation, contact) ->
            new ConstructionEntityData(block, anchored, anchorable, unanchorable, seconds, airtight, radiation, contact)));
    public ConstructionEntityData {
        if (block == null || block.isBlank() || unanchorable && !anchorable
                || !Double.isFinite(anchoringSeconds) || anchoringSeconds < 0 || radiationBlocker == null || structuralContact == null)
            throw new IllegalArgumentException("invalid construction entity mapping");
    }
    public ConstructionEntityData(String block, boolean defaultAnchored, boolean anchorable, boolean unanchorable,
                                  double anchoringSeconds, boolean airtight) {
        this(block, defaultAnchored, anchorable, unanchorable, anchoringSeconds, airtight, java.util.Optional.empty(), java.util.Optional.empty());
    }

    public ConstructionEntityData(String block, boolean defaultAnchored, boolean anchorable, boolean unanchorable,
                                  double anchoringSeconds, boolean airtight, java.util.Optional<RadiationBlockerProfile> radiationBlocker) {
        this(block, defaultAnchored, anchorable, unanchorable, anchoringSeconds, airtight, radiationBlocker, java.util.Optional.empty());
    }

    public StructuralContactResult evaluateStructuralContact(MeleeWeaponData attack) {
        if (structuralContact.isEmpty()) return StructuralContactResult.NOT_OPTED_IN;
        Map<String, Float> adjusted = DamageModifierData.apply(attack.damage(), structuralContact.get());
        return evaluateStructuralContact(adjusted);
    }

    /** Evaluates the already-composed attack damage after this structure's typed modifiers. */
    public StructuralContactResult evaluateStructuralContact(Map<String, Float> adjusted) {
        if (structuralContact.isEmpty()) return StructuralContactResult.NOT_OPTED_IN;
        return evaluateAdjustedContact(adjusted);
    }

    public static StructuralContactResult evaluateAdjustedContact(Map<String, Float> adjusted) {
        if (adjusted != null && adjusted.values().stream().anyMatch(value -> value != null && value > 0f))
            return StructuralContactResult.UNSUPPORTED_POSITIVE_DAMAGE;
        return StructuralContactResult.ZERO_ABSORBED_CONTACT;
    }

    public enum StructuralContactResult { NOT_OPTED_IN, ZERO_ABSORBED_CONTACT, UNSUPPORTED_POSITIVE_DAMAGE }
}
