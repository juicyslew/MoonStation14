package com.juicyslew.moonstation14.ms14.disposal;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalTubeRecordPersistenceTest {
    @Test void frameAndSealedHostIdentitySurviveSavedDataRoundTrip() {
        var id=UUID.randomUUID(); var generation=UUID.randomUUID();
        var record=new DisposalTubeRecord(id,new BlockPos(4,70,-3),"moonstation14:disposal_pipe",
                new DisposalOrientation(Direction.DOWN,Direction.EAST),true,"tag","filter",7,
                DisposalTubeRecord.HostKind.SEALED_WALL,"moonstation14:steel_wall_block",generation);
        var raw=new CompoundTag(); raw.putUUID("id",id); raw.putLong("pos",record.pos().asLong());
        raw.putString("prototype",record.prototypeId()); raw.putString("direction",record.frame().forward().getName());
        raw.putString("up",record.frame().up().getName()); raw.putBoolean("anchored",true); raw.putString("tag","tag");
        raw.putString("filter","filter"); raw.putLong("revision",7); raw.putString("hostKind","SEALED_WALL");
        raw.putString("sealedHostId",record.sealedHostId()); raw.putUUID("hostGeneration",generation);
        CompoundTag tag=new CompoundTag(); tag.putInt("schema",1); ListTag list=new ListTag(); list.add(raw); tag.put("records",list);
        var restored=DisposalTubeWorldData.load(tag,null).save(new CompoundTag(),null);
        var loaded=DisposalTubeWorldData.load(restored,null).inChunk(0,-1).get(0);
        assertEquals(id,loaded.id()); assertEquals(record.frame(),loaded.frame());
        assertEquals(record.hostKind(),loaded.hostKind()); assertEquals(record.sealedHostId(),loaded.sealedHostId());
        assertEquals(generation,loaded.hostGeneration()); assertEquals(7,loaded.revision());
    }
}
