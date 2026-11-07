package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TegFixtureLoadedObservationTest {
    @Test void productionDeliveryObserverNeverLoadsAndOnlyCreditsFreshLoadedApcSamples() {
        var level=mock(ServerLevel.class);var source=mock(ServerChunkCache.class);when(level.getChunkSource()).thenReturn(source);
        var origin=BlockPos.ZERO;var token=UUID.randomUUID();var entry=new TegFixtureSavedData.Entry(origin,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),token,true);
        var pos=TegFixturePlan.layout(origin.offset(8,1,8)).devices().outputApc();int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        Map<BlockPos,TegFixtureDeliveryMeter.State> states=new HashMap<>();
        when(source.getChunk(cx,cz,ChunkStatus.FULL,false)).thenReturn(null);
        for(int i=0;i<100;i++)TegFixtureService.observeDeliveryEntry(level,entry,states);
        verify(source,times(100)).getChunk(cx,cz,ChunkStatus.FULL,false);
        verify(source,never()).getChunk(eq(cx),eq(cz),eq(ChunkStatus.FULL),eq(true));
        verify(level,never()).getBlockEntity(any());verify(level,never()).getBlockState(any());
        assertEquals(0,states.get(origin).totalJoules());assertEquals(-1,states.get(origin).sampleTick());

        var chunk=mock(LevelChunk.class);var apc=mock(PowerDeviceBlockEntity.class);
        var state=mock(net.minecraft.world.level.block.state.BlockState.class);var block=mock(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.class);
        when(source.getChunk(cx,cz,ChunkStatus.FULL,false)).thenReturn(chunk);
        when(chunk.getBlockEntities()).thenReturn(new HashMap<>());
        // A loaded chunk can still have pending/unmaterialized block-entity NBT. It must
        // remain UNKNOWN without invoking LevelChunk#getBlockEntity (which may create it).
        TegFixtureService.observeDeliveryEntry(level,entry,states);
        verify(chunk,never()).getBlockEntity(any());verify(chunk,never()).getBlockState(any());
        assertEquals(-1,states.get(origin).sampleTick());
        chunk.getBlockEntities().put(pos,apc);
        when(chunk.getBlockState(pos)).thenReturn(state);
        when(state.getBlock()).thenReturn(block);when(block.kind()).thenReturn(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC);
        try(var ignored=mockStatic(PowerRuntime.class)) {
            var observation=new PowerRuntime.ApcOutputObservation(true,100,100,0,0,100,4);
            ignored.when(()->PowerRuntime.apcOutputObservation(level,apc)).thenReturn(observation);
            TegFixtureService.observeDeliveryEntry(level,entry,states);
            ignored.when(()->PowerRuntime.apcOutputObservation(level,apc)).thenReturn(new PowerRuntime.ApcOutputObservation(true,100,100,0,0,120,4));
            TegFixtureService.observeDeliveryEntry(level,entry,states);
            assertEquals(100,states.get(origin).totalJoules());
            when(source.getChunk(cx,cz,ChunkStatus.FULL,false)).thenReturn(null);
            TegFixtureService.observeDeliveryEntry(level,entry,states);
            assertEquals(100,states.get(origin).totalJoules());assertEquals(-1,states.get(origin).sampleTick());
            ignored.verify(()->PowerRuntime.apcOutputObservation(level,apc),times(2));
        }
        verify(level,never()).getBlockEntity(any());verify(level,never()).getBlockState(any());
        verify(chunk,never()).getBlockEntity(any());
    }
}
