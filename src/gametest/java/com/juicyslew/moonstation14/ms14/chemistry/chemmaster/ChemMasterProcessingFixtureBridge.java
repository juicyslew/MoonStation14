package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Test-source bridge to the existing trusted processor; it adds no processing behavior. */
public final class ChemMasterProcessingFixtureBridge {
    private ChemMasterProcessingFixtureBridge() { }

    public static boolean process(ChemMasterBlockEntity machine, ChemMasterControlRequest.Operation operation,
                                  ResourceKey<ReagentData> reagent, long amountCents) {
        return ChemMasterSessionService.process(machine, new ChemMasterControlRequest(UUID.randomUUID(),
                machine.placementIdentity(), ((ServerLevel) machine.getLevel()).dimension().location(), machine.getBlockPos(),
                0, machine.stateRevision(), machine.inputSlot().snapshot().revision(), operation, machine.mode(), machine.sort(),
                Optional.of(reagent), amountCents));
    }
}
