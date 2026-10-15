package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import net.minecraft.resources.ResourceKey;

/** Pure admission rule for the one buffer operation that does not require an input beaker. */
public final class ChemMasterProcessingPolicy {
    private ChemMasterProcessingPolicy() { }

    public static boolean discardBufferWithoutInput(boolean inputPresent, ChemMasterControlRequest.Operation operation,
            ChemMasterControlRequest.Mode mode, ChemMasterBuffer buffer,
            ResourceKey<ReagentData> reagent, ChemMasterBuffer.Amount amount) {
        if (inputPresent || operation != ChemMasterControlRequest.Operation.BUFFER_REAGENT
                || mode != ChemMasterControlRequest.Mode.DISCARD || buffer == null || reagent == null || amount == null)
            return false;
        buffer.discard(reagent, amount);
        return true;
    }

    public static boolean shouldReactOnReturn(long admittedCents) { return admittedCents > 0; }
}

