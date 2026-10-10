package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.reagent.DumpableSolutionPolicy;

/** Supported ChemMaster composition: APC load, cable reach, UI reach, and dispenser-compatible input slot. */
public record ChemMasterData(double powerLoadWatts, int cableReach, float uiRange, InputSlot inputSlot,
                              ResourceLocation clickSound, ResourceLocation slotInsertSound, ResourceLocation slotEjectSound,
                              DumpableSolutionPolicy dumpableSolution) {
    public record InputSlot(boolean fitsInDispenser) {
        public static final Codec<InputSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("fitsInDispenser", true).forGetter(InputSlot::fitsInDispenser)
        ).apply(i, InputSlot::new));
    }
    private static final Codec<ChemMasterData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("powerLoadWatts", 250d).forGetter(ChemMasterData::powerLoadWatts),
            Codec.INT.optionalFieldOf("cableReach", 3).forGetter(ChemMasterData::cableReach),
            Codec.FLOAT.optionalFieldOf("uiRange", 2f).forGetter(ChemMasterData::uiRange),
            InputSlot.CODEC.optionalFieldOf("inputSlot", new InputSlot(true)).forGetter(ChemMasterData::inputSlot),
            ResourceLocation.CODEC.optionalFieldOf("clickSound").forGetter(d -> java.util.Optional.ofNullable(d.clickSound())),
            ResourceLocation.CODEC.optionalFieldOf("slotInsertSound").forGetter(d -> java.util.Optional.ofNullable(d.slotInsertSound())),
            ResourceLocation.CODEC.optionalFieldOf("slotEjectSound").forGetter(d -> java.util.Optional.ofNullable(d.slotEjectSound())),
            DumpableSolutionPolicy.CODEC.optionalFieldOf("dumpableSolution", DumpableSolutionPolicy.DISABLED).forGetter(ChemMasterData::dumpableSolution)
    ).apply(i, (watts, cable, range, slot, click, insert, eject, dump) -> new ChemMasterData(watts, cable, range, slot, click.orElse(null), insert.orElse(null), eject.orElse(null), dump)));
    public static final Codec<ChemMasterData> CODEC = STRUCTURAL;
    public ChemMasterData {
        if (!Double.isFinite(powerLoadWatts) || powerLoadWatts <= 0 || cableReach <= 0
                || !Float.isFinite(uiRange) || uiRange <= 0 || inputSlot == null || dumpableSolution == null)
            throw new IllegalArgumentException("invalid ChemMaster composition");
    }
    public ChemMasterData(double watts, int cable, float range, InputSlot slot) { this(watts, cable, range, slot, null, null, null, DumpableSolutionPolicy.DISABLED); }
    public ChemMasterData(double watts, int cable, float range, InputSlot slot, ResourceLocation click, ResourceLocation insert, ResourceLocation eject) { this(watts, cable, range, slot, click, insert, eject, DumpableSolutionPolicy.DISABLED); }
}
