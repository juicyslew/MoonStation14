package com.juicyslew.moonstation14.ms14.devicenet.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.ms14.devicenet.PrototypeResourceFixture;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@GameTestTags({"device-network", "signal-control", "prototype"})
class SignalControlPrototypeTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14",path); }
    private static PrototypeManager manager() throws Exception {
        PrototypeManager manager=new PrototypeManager();
        manager.register(ModSignalControls.TYPE);
        manager.register(ModDeviceNetworks.NETWORK_TYPE); manager.register(ModDeviceNetworks.FREQUENCY_TYPE); manager.register(ModDeviceNetworks.PORT_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.NETWORK_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.FREQUENCY_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.PORT_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModSignalControls.TYPE);
        return manager;
    }

    @Test void inheritanceDefaultsAndResolvedButtonSwitchPoliciesRouteThroughSharedLinks() throws Exception {
        PrototypeManager manager=manager();
        SignalControlData button=manager.snapshot(ModSignalControls.TYPE).get(ModSignalControls.SIGNAL_BUTTON);
        SignalControlData signalSwitch=manager.snapshot(ModSignalControls.TYPE).get(ModSignalControls.SIGNAL_SWITCH);
        assertTrue(button.wallMount().anchored()); assertTrue(button.wallMount().nonColliding());
        DeviceComposition buttonComposition=button.deviceComponents().resolve(manager).withAddress("button-stable-id");
        DeviceComposition switchComposition=signalSwitch.deviceComponents().resolve(manager).withAddress("switch-stable-id");
        assertEquals(2,buttonComposition.network().orElseThrow().networkId());
        assertEquals(1280,buttonComposition.network().orElseThrow().transmitFrequency());
        assertEquals(200,buttonComposition.connection().orElseThrow().wirelessRange());
        assertTrue(buttonComposition.sinks().isEmpty());
        assertEquals(DeviceSignal.LOW,signalSwitch.deviceComponents().source().orElseThrow().lastSignals().get(id("status")));
        assertEquals(List.of(new SignalControlData.Output("Pressed",DeviceSignal.MOMENTARY)),button.outputs(true));
        assertEquals(button.outputs(true),button.outputs(false));
        assertEquals(List.of(new SignalControlData.Output("On",DeviceSignal.MOMENTARY),new SignalControlData.Output("Status",DeviceSignal.HIGH)),signalSwitch.outputs(true));
        assertEquals(List.of(new SignalControlData.Output("Off",DeviceSignal.MOMENTARY),new SignalControlData.Output("Status",DeviceSignal.LOW)),signalSwitch.outputs(false));

        var transport=new DeviceNetworkTransport(); var links=new DeviceLinkService(transport); var graph=new DeviceLinkGraph();
        var source=new TestEndpoint(DeviceIdentity.random(),buttonComposition,BlockPos.ZERO);
        var sinkPort=new DevicePort("Toggle","Toggle","",List.of());
        var sink=new TestEndpoint(DeviceIdentity.random(),new DeviceComposition(List.of(),List.of(sinkPort),Optional.empty(),Optional.empty()),new BlockPos(1,0,0));
        transport.register(source); transport.register(sink);
        assertTrue(links.link(graph,source,"Pressed",sink,"Toggle"));
        for(SignalControlData.Output output:button.outputs(true)) assertTrue(links.invoke(graph,source,output.port(),output.signal()));
        assertEquals(new SignalPayload("Toggle",DeviceSignal.MOMENTARY),sink.received.getFirst());
    }

    private static final class TestEndpoint implements DeviceEndpoint {
        private final DeviceIdentity identity; private final DeviceComposition composition; private final BlockPos position;
        private final List<DevicePayload> received=new ArrayList<>();
        private TestEndpoint(DeviceIdentity identity,DeviceComposition composition,BlockPos position) { this.identity=identity;this.composition=composition;this.position=position; }
        @Override public DeviceIdentity identity(){return identity;}
        @Override public ResourceKey<Level> dimension(){return Level.OVERWORLD;}
        @Override public BlockPos position(){return position;}
        @Override public DeviceComposition composition(){return composition;}
        @Override public boolean available(){return true;}
        @Override public void receive(DevicePayload payload,String sourceAddress){received.add(payload);}
    }
}
