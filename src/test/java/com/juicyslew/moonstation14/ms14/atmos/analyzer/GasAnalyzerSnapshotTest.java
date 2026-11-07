package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GasAnalyzerSnapshotTest {
    private static GasAnalyzerSample sample(double volume) {
        return GasAnalyzerSample.fromMixture(new GasMixture(Map.of(GasType.OXYGEN, 1d), 293.15), volume,
                new GasAnalyzerProfile(6, 1, 20, .01));
    }

    @Test void boundedNamedThreePortReadoutRoundTripsWithIdentity() {
        var pos = new BlockPos(3, 4, 5); var entries = new ArrayList<GasAnalyzerPipeSamples.Entry>();
        var ports=List.of("inlet","filter","outlet");
        for (int i=0;i<ports.size();i++) {
            String port=ports.get(i);double volume=i==0?.2:i==1?.1:.05;
            var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY, port);
            var label=GasAnalyzerPipeSamples.deviceLabel(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.MIXER,
                    port,PipeTopology.Layer.PRIMARY,net.minecraft.core.Direction.NORTH);
            entries.add(new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(key, UUID.randomUUID(), 1, volume),label,sample(volume)));
        }
        var value = new GasAnalyzerSnapshot(UUID.randomUUID(), 1, 2, 0, 10, 1, UUID.randomUUID(), "token",
                ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, pos, 1,
                GasAnalyzerSnapshot.Status.READY, null, null, false, GasAnalyzerPipeSamples.Source.DEVICE, entries);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            GasAnalyzerSnapshot.STREAM_CODEC.encode(buffer, value);
            var decoded=GasAnalyzerSnapshot.STREAM_CODEC.decode(buffer);
            assertEquals(value, decoded);
            assertEquals(List.of("gui.moonstation14.gas_analyzer.port.first_inlet",
                            "gui.moonstation14.gas_analyzer.port.second_inlet","gui.moonstation14.gas_analyzer.port.outlet"),
                    decoded.nodeEntries().stream().map(GasAnalyzerPipeSamples.Entry::labelKey).toList());
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new GasAnalyzerSnapshot(UUID.randomUUID(), 1, 0, 0, 0, 1,
                UUID.randomUUID(), "token", ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, pos, 1,
                GasAnalyzerSnapshot.Status.READY, sample(.2), null, false, GasAnalyzerPipeSamples.Source.DEVICE, entries));
    }

    @Test void mutableGasVersionDoesNotChangeTargetIdentityButReplacementIncarnationDoes() {
        var node = new PipeTopology.NodeKey(new BlockPos(1, 2, 3), PipeTopology.Layer.PRIMARY, "inlet");
        var id = UUID.randomUUID();
        var a = new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.DEVICE, List.of(new GasAnalyzerPipeSamples.Entry(
                new GasAnalyzerPipeSamples.Stamp(node, id, 1, .1), sample(.1))));
        var flowing = new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.DEVICE, List.of(new GasAnalyzerPipeSamples.Entry(
                new GasAnalyzerPipeSamples.Stamp(node, id, 2, .1), sample(.1))));
        var replaced = new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.DEVICE, List.of(new GasAnalyzerPipeSamples.Entry(
                new GasAnalyzerPipeSamples.Stamp(node, UUID.randomUUID(), 1, .1), sample(.1))));
        assertTrue(GasAnalyzerSessionService.sameTargetIdentity(a, flowing));
        assertFalse(GasAnalyzerSessionService.sameTargetIdentity(a, replaced));
    }

    @Test void mixerFilterAndDirectionalPortNamesUseTypedDeviceSemantics() {
        assertEquals("gui.moonstation14.gas_analyzer.port.first_inlet",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.MIXER,"inlet",
                PipeTopology.Layer.PRIMARY,net.minecraft.core.Direction.NORTH));
        assertEquals("gui.moonstation14.gas_analyzer.port.second_inlet",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.MIXER,"filter",
                PipeTopology.Layer.PRIMARY,net.minecraft.core.Direction.WEST));
        assertEquals("gui.moonstation14.gas_analyzer.port.filtered_outlet",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,"filter",
                PipeTopology.Layer.PRIMARY,net.minecraft.core.Direction.WEST));
        assertEquals("gui.moonstation14.gas_analyzer.port.primary_north",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD,"north0",
                PipeTopology.Layer.PRIMARY,net.minecraft.core.Direction.NORTH));
        assertEquals("gui.moonstation14.gas_analyzer.port.secondary_south",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD,"south1",
                PipeTopology.Layer.SECONDARY,net.minecraft.core.Direction.SOUTH));
        assertEquals("gui.moonstation14.gas_analyzer.port.tertiary_south",GasAnalyzerPipeSamples.deviceLabel(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.HEATER,"pipe",
                PipeTopology.Layer.TERTIARY,net.minecraft.core.Direction.SOUTH));
    }

    @Test void unaryDeviceMayPublishOnlyItsExactAdjacentPipeStamp() {
        var host=new BlockPos(4,5,6);var pipe=host.west();
        var stamp=new GasAnalyzerPipeSamples.Stamp(new PipeTopology.NodeKey(pipe,PipeTopology.Layer.PRIMARY),UUID.randomUUID(),2,.2);
        var entry=new GasAnalyzerPipeSamples.Entry(stamp,GasAnalyzerPipeSamples.visiblePipeLabel(PipeTopology.Layer.PRIMARY),sample(.2));
        var target=new GasAnalyzerSnapshot(UUID.randomUUID(),1,1,0,10,1,UUID.randomUUID(),"source",
                ResourceLocation.withDefaultNamespace("overworld"),BlockPos.ZERO,host,1,GasAnalyzerSnapshot.Status.READY,
                null,null,false,GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(entry));
        assertEquals(host,target.targetCell());assertEquals(pipe,target.nodeEntries().getFirst().stamp().node().position());
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{GasAnalyzerSnapshot.STREAM_CODEC.encode(buffer,target);assertEquals(target,GasAnalyzerSnapshot.STREAM_CODEC.decode(buffer));}
        finally{buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new GasAnalyzerSnapshot(UUID.randomUUID(),1,1,0,10,1,
                UUID.randomUUID(),"source",ResourceLocation.withDefaultNamespace("overworld"),BlockPos.ZERO,host,1,
                GasAnalyzerSnapshot.Status.READY,null,null,false,GasAnalyzerPipeSamples.Source.UNARY_DEVICE,
                List.of(new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(
                        new PipeTopology.NodeKey(host,PipeTopology.Layer.PRIMARY),UUID.randomUUID(),1,.2),
                        GasAnalyzerPipeSamples.visiblePipeLabel(PipeTopology.Layer.PRIMARY),sample(.2)))));
    }

    @Test void unaryFacingAndLayerChangesInvalidateButSettingsRevisionAndGasFlowDoNot(){
        var node=new PipeTopology.NodeKey(new BlockPos(2,3,4),PipeTopology.Layer.PRIMARY);
        var entry=new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(node,UUID.randomUUID(),1,.2),
                GasAnalyzerPipeSamples.visiblePipeLabel(PipeTopology.Layer.PRIMARY),sample(.2));
        var stable=new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(entry),"VENT:WEST:PRIMARY");
        var changedFacing=new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(entry),"VENT:EAST:PRIMARY");
        var changedLayer=new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(entry),"VENT:WEST:SECONDARY");
        assertFalse(GasAnalyzerSessionService.sameTargetIdentity(stable,changedFacing));
        assertFalse(GasAnalyzerSessionService.sameTargetIdentity(stable,changedLayer));
        var flowing=new GasAnalyzerPipeSamples.Readout(GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(new GasAnalyzerPipeSamples.Entry(
                new GasAnalyzerPipeSamples.Stamp(node,entry.stamp().incarnation(),2,.2),entry.sample())),"VENT:WEST:PRIMARY");
        assertTrue(GasAnalyzerSessionService.sameTargetIdentity(stable,flowing),"gas version and non-structural settings revisions retain source binding");
    }
}
