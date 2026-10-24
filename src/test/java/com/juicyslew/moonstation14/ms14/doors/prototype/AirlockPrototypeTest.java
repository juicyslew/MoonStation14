package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class AirlockPrototypeTest {
    private static JsonObject resource(String name) throws Exception {
        try (var in = new InputStreamReader(AirlockPrototypeTest.class.getResourceAsStream(
                "/data/moonstation14/moonstation14/airlock/" + name + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    @Test void inheritedCompositionResolvesAndValidatesUpstreamDefaults() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base_airlock"), resource("base_airlock"));
        raw.put(ModAirlocks.STANDARD, resource("standard_airlock"));
        var manager = new PrototypeManager(); manager.register(ModAirlocks.TYPE);
        manager.reload(ModAirlocks.TYPE, raw);
        var catalog = manager.snapshot(ModAirlocks.TYPE);
        assertNull(catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "base_airlock")));
        var resolved = catalog.get(ModAirlocks.STANDARD);
        assertEquals(20, resolved.apcPowerReceiver().requestedWatts());
        assertEquals(3, resolved.extensionCableReceiver().localReachBlocks());
        assertEquals(5, resolved.airlock().autoCloseSeconds());
        assertEquals(15, resolved.door().crushDamageBlunt());
        assertFalse(resolved.electrified().enabled());
        assertTrue(resolved.electrified().usesApcPower());
        assertTrue(resolved.electrified().requirePower());
        assertEquals(7.5f, resolved.electrified().shockDamage());
        assertEquals(5f, resolved.electrified().shockSeconds());
        assertEquals(30f, resolved.electrified().electrificationCancelSeconds());
        assertEquals(1f, resolved.electrified().siemensCoefficient());
        assertTrue(resolved.door().bumpOpen());
        assertEquals(0.4, resolved.door().openTimeOneSeconds());
        assertEquals(2.4, resolved.door().crushStunSeconds()+resolved.door().openTimeOneSeconds(),1e-9);
        assertTrue(resolved.airlock().safety());
        assertTrue(resolved.wiresPanelSecurity().wiresAccessible());
        assertEquals(ToolProfile.Quality.SCREWING,resolved.wiresPanel().openingToolQuality());
        assertFalse(resolved.airlock().openPanelVisible());
        assertEquals(1, resolved.wiresPanel().toggleDelaySeconds());
        var devices = resolved.deviceComponents();
        var overload=devices.soundOnOverload().orElseThrow();
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","device/defib_zap"),overload.sound().orElseThrow());
        assertEquals(0f,overload.volumeModifier());
        assertEquals(200, devices.connection().orElseThrow().wirelessRange());
        assertEquals(java.util.Set.of("moonstation14:open", "moonstation14:close", "moonstation14:toggle", "moonstation14:auto_close", "moonstation14:door_bolt"), devices.sink().orElseThrow().ports().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of("moonstation14:door_status"), devices.source().orElseThrow().ports().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals(com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.LOW, devices.source().orElseThrow().lastSignals().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "door_status")));
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wireless"), devices.network().orElseThrow().networkPrototypeId());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "basic_device"), devices.network().orElseThrow().transmitFrequencyPrototypeId().orElseThrow());
        var noWireUi=new AirlockData(resolved.door(),resolved.airlock(),resolved.doorBolt(),resolved.apcPowerReceiver(),resolved.extensionCableReceiver(),resolved.wiresPanel(),new AirlockData.WiresPanelSecurity(false),resolved.wires(),resolved.electrified());
        assertTrue(com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity.resolvesPanelVisible(true,AirlockState.DoorPhase.CLOSED,noWireUi),"wire UI accessibility does not hide the physical panel");
        assertTrue(resolved.wires().supportedActions().contains("bolt"));
        assertEquals(8,resolved.wires().supportedActions().size());
        assertTrue(resolved.wires().supportedActions().containsAll(java.util.Set.of("power_one","power_two","log","bolt","bolt_light","timing","safety","ai_interact")));
        assertEquals(30,resolved.wires().powerOnePulseSeconds());
        assertEquals(15,resolved.wires().powerTwoPulseSeconds());
        assertEquals(30,resolved.wires().logPulseSeconds());
        var pulse=AirlockState.initial(java.util.UUID.randomUUID(),true,true).withPower(true)
                .applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.PULSE,5,resolved.wires());
        assertEquals(605,pulse.logPulseUntil());
    }
    @Test void unsafeCrushReleaseUsesTwoSecondDeadlineAndRechecksPowerAndBolts() {
        var opened=AirlockState.initial(java.util.UUID.randomUUID(),false,true).withPower(true)
                .open(80,false,0.4).tick(88,0.2,2,5).tick(92,0.2,2,5);
        var closing=opened.close(100,false,0.4).tick(108,0.4,2,5);
        assertEquals(AirlockState.DoorPhase.CLOSING_PASSABLE,closing.phase());
        assertEquals(closing,closing.reopenAfterCrush(147,0.4));
        var fullyClosed=closing.tick(148,0.4,2,5);
        assertEquals(AirlockState.DoorPhase.CLOSED,fullyClosed.phase());
        var reopened=fullyClosed.reopenAfterCrush(148,0.4);
        assertEquals(AirlockState.DoorPhase.OPENING,reopened.phase());
        assertTrue(reopened.canOpenOrClose());
        assertEquals(0,reopened.autoCloseAt());
        var passable=reopened.tick(156,0.2,2,5);
        assertEquals(AirlockState.DoorPhase.OPENING_PASSABLE,passable.phase());
        var fullyOpen=passable.tick(160,0.2,2,5);
        assertEquals(AirlockState.DoorPhase.OPEN,fullyOpen.phase());
        assertEquals(260,fullyOpen.autoCloseAt());
        assertEquals(AirlockState.DoorPhase.CLOSING_PASSABLE,closing.withPower(false).reopenAfterCrush(148,0.4).phase());
        assertFalse(fullyClosed.withPower(true).applyWire(AirlockState.Wire.BOLT,AirlockState.WireAction.CUT,0).reopenAfterCrush(148,0.4).canOpenOrClose());
        var consumed=AirlockState.initial(java.util.UUID.randomUUID(),false,true).withPower(true);
        assertEquals(AirlockState.DoorPhase.OPENING,consumed.reopenAfterCrush(200,0.4).phase(),
                "a bounded retry can reopen after a failed closing transition has been consumed");
    }
    @Test void powerAndCableReceiverInheritanceAreIndependent() throws Exception {
        var base=resource("base_airlock");
        base.getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts",35.0);
        base.getAsJsonObject("extensionCableReceiver").addProperty("localReachBlocks",7);
        base.getAsJsonObject("wires").addProperty("logPulseSeconds",12.5);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),base);
        raw.put(ModAirlocks.STANDARD,resource("standard_airlock"));
        var manager=new PrototypeManager();manager.register(ModAirlocks.TYPE);manager.reload(ModAirlocks.TYPE,raw);
        var resolved=manager.snapshot(ModAirlocks.TYPE).get(ModAirlocks.STANDARD);
        assertEquals(35,resolved.apcPowerReceiver().requestedWatts());
        assertEquals(7,resolved.extensionCableReceiver().localReachBlocks());
        assertEquals(12.5,resolved.wires().logPulseSeconds());
        var log=AirlockState.initial(java.util.UUID.randomUUID(),true,true).withPower(true)
                .applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.PULSE,20,resolved.wires());
        assertEquals(270,log.logPulseUntil());
        assertFalse(AirlockData.ApcPowerReceiver.class.getRecordComponents().length > 1);
        assertEquals(3,resource("base_airlock").getAsJsonObject("extensionCableReceiver").get("localReachBlocks").getAsInt());
    }
    @Test void bumpOpenCanBeOverriddenFalseInParentAndInherited() throws Exception {
        var base=resource("base_airlock");base.getAsJsonObject("door").addProperty("bumpOpen",false);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),base);raw.put(ModAirlocks.STANDARD,resource("standard_airlock"));
        var manager=new PrototypeManager();manager.register(ModAirlocks.TYPE);manager.reload(ModAirlocks.TYPE,raw);
        assertFalse(manager.snapshot(ModAirlocks.TYPE).get(ModAirlocks.STANDARD).door().bumpOpen());
    }
    @Test void invalidPowerDemandRejected() throws Exception {
        var base = resource("base_airlock");
        base.getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts", 0);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base_airlock"), base);
        raw.put(ModAirlocks.STANDARD, resource("standard_airlock"));
        var manager = new PrototypeManager(); manager.register(ModAirlocks.TYPE);
        assertThrows(RuntimeException.class, () -> manager.reload(ModAirlocks.TYPE, raw));
    }
    @Test void inheritedPowerDemandCanBeOverriddenWithinLiveSolverBounds() throws Exception {
        var base=resource("base_airlock");base.getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts",35.0);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),base);
        raw.put(ModAirlocks.STANDARD,resource("standard_airlock"));
        var manager=new PrototypeManager();manager.register(ModAirlocks.TYPE);manager.reload(ModAirlocks.TYPE,raw);
        assertEquals(35,manager.snapshot(ModAirlocks.TYPE).get(ModAirlocks.STANDARD).apcPowerReceiver().requestedWatts());
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,5000.01}){
            var invalidBase=resource("base_airlock");invalidBase.getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts",invalid);
            raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),invalidBase);
            assertThrows(RuntimeException.class,()->manager.reload(ModAirlocks.TYPE,raw));
        }
    }
    @Test void inheritedPanelAndWireOverridesReachTimedAndWireConsumers() throws Exception {
        var base=resource("base_airlock");
        base.getAsJsonObject("wiresPanel").addProperty("toggleDelaySeconds",0.75);
        base.getAsJsonObject("wiresPanel").addProperty("openingToolQuality","PULSING");
        base.getAsJsonObject("airlock").addProperty("openPanelVisible",true);
        base.getAsJsonObject("wires").addProperty("powerOnePulseSeconds",4.5);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),base);
        raw.put(ModAirlocks.STANDARD,resource("standard_airlock"));
        var manager=new PrototypeManager();manager.register(ModAirlocks.TYPE);manager.reload(ModAirlocks.TYPE,raw);
        var resolved=manager.snapshot(ModAirlocks.TYPE).get(ModAirlocks.STANDARD);
        assertEquals(0.75,resolved.wiresPanel().toggleDelaySeconds());
        assertEquals(ToolProfile.Quality.PULSING,resolved.wiresPanel().openingToolQuality());
        assertEquals("moonstation14:airlock/panel_open",resolved.wiresPanel().openSound());
        assertEquals("moonstation14:airlock/panel_close",resolved.wiresPanel().closeSound());
        assertTrue(resolved.airlock().openPanelVisible());
        assertTrue(com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity.resolvesPanelVisible(true,AirlockState.DoorPhase.OPEN,resolved));
        assertEquals(15,com.juicyslew.moonstation14.ms14.doors.AirlockToolInteraction.panelDurationTicks(resolved.wiresPanel().toggleDelaySeconds()));
        var toolProfiles=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:new String[]{"base_item","base_crowbar","crowbar","wirecutter","screwdriver","multitool","kitchen_knife"}){
            try(var in=new InputStreamReader(AirlockPrototypeTest.class.getResourceAsStream("/data/moonstation14/moonstation14/tool_profile/"+name+".json"),StandardCharsets.UTF_8)){
                toolProfiles.put(ResourceLocation.fromNamespaceAndPath("moonstation14",name),JsonParser.parseReader(in).getAsJsonObject());
            }
        }
        var tools=new PrototypeManager();tools.register(ModToolProfiles.TYPE);tools.reload(ModToolProfiles.TYPE,toolProfiles);
        var toolCatalog=tools.snapshot(ModToolProfiles.TYPE);
        assertTrue(ToolQualities.has(ModToolProfiles.MULTITOOL, resolved.wiresPanel().openingToolQuality(), toolCatalog));
        assertFalse(ToolQualities.has(ModToolProfiles.SCREWDRIVER, resolved.wiresPanel().openingToolQuality(), toolCatalog));
        var pulse=AirlockState.initial(java.util.UUID.randomUUID(),true,true).withPower(true)
                .applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.PULSE,10,resolved.wires());
        assertEquals(100,pulse.powerPulseUntil());
    }
}
