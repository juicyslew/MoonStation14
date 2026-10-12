package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Pure ownership/order predicate shared by the authoritative gesture service and its unit tests. */
public final class ChemMasterDumpCorrelation {
    private ChemMasterDumpCorrelation() { }
    public record Owner(UUID gesture, UUID source, UUID body, long epoch, ResourceLocation dimension, long sequence) { }
    public static boolean newer(long incoming, long previous) { return incoming > previous; }
    public static boolean owns(Owner owner, UUID gesture, UUID source, UUID body, long epoch,
                               ResourceLocation dimension, long sequence) {
        return owner != null && owner.gesture().equals(gesture) && owner.source().equals(source)
                && owner.body().equals(body) && owner.epoch() == epoch && owner.dimension().equals(dimension)
                && newer(sequence, owner.sequence());
    }
}
