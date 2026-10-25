package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.reagent.DumpableSolutionPolicy;

/** Supported ChemMaster composition: APC load, cable reach, UI reach, and dispenser-compatible input slot. */
public record ChemMasterData(double powerLoadWatts, int cableReach, float uiRange, InputSlot inputSlot, int pillDosageLimit,
                              OutputSlot outputSlot,
                              ResourceLocation clickSound, ResourceLocation slotInsertSound, ResourceLocation slotEjectSound,
                              DumpableSolutionPolicy dumpableSolution) {
    public record InputSlot(boolean fitsInDispenser) {
        public static final Codec<InputSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("fitsInDispenser", true).forGetter(InputSlot::fitsInDispenser)
        ).apply(i, InputSlot::new));
    }
    /** Supported local profile IDs corresponding to SS14 Bottle/PillCanister tags. */
    public record OutputSlot(List<ResourceLocation> acceptedProfiles) {
        public static final Codec<OutputSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("acceptedProfiles", List.of()).forGetter(OutputSlot::acceptedProfiles)
        ).apply(i, OutputSlot::new));
        public OutputSlot { if (acceptedProfiles == null || acceptedProfiles.stream().anyMatch(java.util.Objects::isNull)
                || acceptedProfiles.size() != acceptedProfiles.stream().distinct().count()) throw new IllegalArgumentException("invalid output profile policy");
            acceptedProfiles = List.copyOf(acceptedProfiles); }
        public boolean accepts(ResourceLocation profile) { return profile != null && acceptedProfiles.contains(profile); }
    }
    private static final Codec<ChemMasterData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("powerLoadWatts", 250d).forGetter(ChemMasterData::powerLoadWatts),
            Codec.INT.optionalFieldOf("cableReach", 3).forGetter(ChemMasterData::cableReach),
            Codec.FLOAT.optionalFieldOf("uiRange", 2f).forGetter(ChemMasterData::uiRange),
            InputSlot.CODEC.optionalFieldOf("inputSlot", new InputSlot(true)).forGetter(ChemMasterData::inputSlot),
            Codec.intRange(0, 10000).optionalFieldOf("pillDosageLimit", 0).forGetter(ChemMasterData::pillDosageLimit),
            OutputSlot.CODEC.optionalFieldOf("outputSlot", new OutputSlot(List.of())).forGetter(ChemMasterData::outputSlot),
            ResourceLocation.CODEC.optionalFieldOf("clickSound").forGetter(d -> java.util.Optional.ofNullable(d.clickSound())),
            ResourceLocation.CODEC.optionalFieldOf("slotInsertSound").forGetter(d -> java.util.Optional.ofNullable(d.slotInsertSound())),
            ResourceLocation.CODEC.optionalFieldOf("slotEjectSound").forGetter(d -> java.util.Optional.ofNullable(d.slotEjectSound())),
            DumpableSolutionPolicy.CODEC.optionalFieldOf("dumpableSolution", DumpableSolutionPolicy.DISABLED).forGetter(ChemMasterData::dumpableSolution)
    ).apply(i, (watts, cable, range, slot, dose, output, click, insert, eject, dump) -> new ChemMasterData(watts, cable, range, slot, dose, output, click.orElse(null), insert.orElse(null), eject.orElse(null), dump)));
    public static final Codec<ChemMasterData> CODEC = STRUCTURAL;
    public ChemMasterData {
        if (!Double.isFinite(powerLoadWatts) || powerLoadWatts <= 0 || cableReach <= 0
                || !Float.isFinite(uiRange) || uiRange <= 0 || inputSlot == null || pillDosageLimit < 0 || outputSlot == null || dumpableSolution == null)
            throw new IllegalArgumentException("invalid ChemMaster composition");
    }
    public ChemMasterData(double watts, int cable, float range, InputSlot slot) { this(watts, cable, range, slot, 0, new OutputSlot(List.of()), null, null, null, DumpableSolutionPolicy.DISABLED); }
    public ChemMasterData(double watts, int cable, float range, InputSlot slot, ResourceLocation click, ResourceLocation insert, ResourceLocation eject) { this(watts, cable, range, slot, 0, new OutputSlot(List.of()), click, insert, eject, DumpableSolutionPolicy.DISABLED); }
}

