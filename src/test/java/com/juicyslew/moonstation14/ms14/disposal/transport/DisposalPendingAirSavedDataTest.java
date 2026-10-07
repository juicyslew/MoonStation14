package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalPendingAirSavedDataTest {
    @Test void roundTripsOneIdempotentOwnedGasRecord() {
        UUID owner=UUID.randomUUID(); BlockPos floor=new BlockPos(4,5,6);
        GasMixture gas=new GasMixture(Map.of(GasType.OXYGEN,2.0,GasType.PLASMA,.25),311);
        var data=new DisposalPendingAirSavedData();
        var record=new DisposalPendingAirSavedData.Record(owner,floor,gas);
        assertTrue(data.put(record)); assertTrue(data.put(record)); assertEquals(1,data.records().size());
        var restored=DisposalPendingAirSavedData.load(data.save(new CompoundTag(),null),null);
        assertEquals(1,restored.records().size());
        var loaded=restored.records().getFirst();
        assertEquals(owner,loaded.owner()); assertEquals(floor,loaded.floor());
        assertEquals(gas.gasMoles(),loaded.gas().gasMoles()); assertEquals(gas.thermalEnergy(),loaded.gas().thermalEnergy(),1e-9);
        assertTrue(restored.remove(owner,loaded)); assertFalse(restored.remove(owner,loaded));
    }
}
