package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComponents;
import java.util.Set;

/** Resolved local subset of SS14 Airlock -> Door/Airlock/DoorBolt/ApcPowerReceiver/WiresPanel. */
public record AirlockData(Door door, Airlock airlock, DoorBolt doorBolt,
                          ApcPowerReceiver apcPowerReceiver, ExtensionCableReceiver extensionCableReceiver,
                           WiresPanel wiresPanel,WiresPanelSecurity wiresPanelSecurity,Wires wires, ElectrifiedData electrified,
                            ExamineMetadata metadata, DeviceComponents deviceComponents) {
    public AirlockData { if (metadata == null) throw new IllegalArgumentException("missing airlock metadata"); if (deviceComponents == null) throw new IllegalArgumentException("missing device components"); }
    public AirlockData(Door door, Airlock airlock, DoorBolt doorBolt, ApcPowerReceiver apcPowerReceiver,
                       ExtensionCableReceiver extensionCableReceiver, WiresPanel wiresPanel,
                       WiresPanelSecurity wiresPanelSecurity, Wires wires, ElectrifiedData electrified) {
        this(door, airlock, doorBolt, apcPowerReceiver, extensionCableReceiver, wiresPanel, wiresPanelSecurity,
                 wires, electrified, ExamineMetadata.EMPTY, DeviceComponents.empty());
    }
    public record Door(double openTimeOneSeconds, double openTimeTwoSeconds,
                        double closeTimeOneSeconds, double closeTimeTwoSeconds,
                        double denyDurationSeconds, double crushDamageBlunt,double crushStunSeconds, boolean bumpOpen) {
        static final Codec<Door> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("openTimeOneSeconds").forGetter(Door::openTimeOneSeconds),
                Codec.DOUBLE.fieldOf("openTimeTwoSeconds").forGetter(Door::openTimeTwoSeconds),
                Codec.DOUBLE.fieldOf("closeTimeOneSeconds").forGetter(Door::closeTimeOneSeconds),
                Codec.DOUBLE.fieldOf("closeTimeTwoSeconds").forGetter(Door::closeTimeTwoSeconds),
                Codec.DOUBLE.fieldOf("denyDurationSeconds").forGetter(Door::denyDurationSeconds),
                Codec.DOUBLE.fieldOf("crushDamageBlunt").forGetter(Door::crushDamageBlunt),
                 Codec.DOUBLE.fieldOf("crushStunSeconds").forGetter(Door::crushStunSeconds),
                 Codec.BOOL.optionalFieldOf("bumpOpen", true).forGetter(Door::bumpOpen)
        ).apply(i, Door::new));
    }
    public record Airlock(boolean safety, boolean autoClose, double autoCloseSeconds,
                          double unboltedPryModifier, double boltedPryMultiplier,boolean openPanelVisible) {
        static final Codec<Airlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("safety").forGetter(Airlock::safety), Codec.BOOL.fieldOf("autoClose").forGetter(Airlock::autoClose),
                Codec.DOUBLE.fieldOf("autoCloseSeconds").forGetter(Airlock::autoCloseSeconds),
                Codec.DOUBLE.fieldOf("unboltedPryModifier").forGetter(Airlock::unboltedPryModifier),
                Codec.DOUBLE.fieldOf("boltedPryMultiplier").forGetter(Airlock::boltedPryMultiplier),Codec.BOOL.fieldOf("openPanelVisible").forGetter(Airlock::openPanelVisible)
        ).apply(i, Airlock::new));
    }
    public record DoorBolt(boolean boltLightsEnabled) {
        static final Codec<DoorBolt> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("boltLightsEnabled").forGetter(DoorBolt::boltLightsEnabled)
        ).apply(i, DoorBolt::new));
    }
    public record ApcPowerReceiver(double requestedWatts) {
        static final Codec<ApcPowerReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("requestedWatts").forGetter(ApcPowerReceiver::requestedWatts)
        ).apply(i, ApcPowerReceiver::new));
    }
    public record ExtensionCableReceiver(int localReachBlocks) {
        static final Codec<ExtensionCableReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("localReachBlocks").forGetter(ExtensionCableReceiver::localReachBlocks)
        ).apply(i, ExtensionCableReceiver::new));
    }
    /** Supported panel controls plus physical inactive placeholders for source AI/log wire slots. */
    public record WiresPanel(ToolProfile.Quality openingToolQuality, double toggleDelaySeconds,
                             String openSound, String closeSound) {
        static final Codec<WiresPanel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(ToolProfile.Quality::valueOf,ToolProfile.Quality::name).fieldOf("openingToolQuality").forGetter(WiresPanel::openingToolQuality),
                Codec.DOUBLE.fieldOf("toggleDelaySeconds").forGetter(WiresPanel::toggleDelaySeconds),
                Codec.STRING.optionalFieldOf("openSound", "moonstation14:airlock/panel_open").forGetter(WiresPanel::openSound),
                Codec.STRING.optionalFieldOf("closeSound", "moonstation14:airlock/panel_close").forGetter(WiresPanel::closeSound)
        ).apply(i, WiresPanel::new));
    }
    public record WiresPanelSecurity(boolean wiresAccessible) {
        static final Codec<WiresPanelSecurity> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.BOOL.fieldOf("wiresAccessible").forGetter(WiresPanelSecurity::wiresAccessible)
        ).apply(i,WiresPanelSecurity::new));
    }
    /** Supported action definitions are resolved data, not mutable per-door wire state. */
    public record Wires(String layoutId, Set<String> supportedActions, double powerOnePulseSeconds,
                         double powerTwoPulseSeconds,double timingCutModifier,double timingPulseModifier,double timingPulseSeconds,
                         double safetyPulseSeconds,double logPulseSeconds) {
        public Wires(String layoutId,Set<String> supportedActions,double powerOnePulseSeconds,double powerTwoPulseSeconds,
                     double timingCutModifier,double timingPulseModifier,double timingPulseSeconds,double safetyPulseSeconds) {
            this(layoutId,supportedActions,powerOnePulseSeconds,powerTwoPulseSeconds,timingCutModifier,timingPulseModifier,timingPulseSeconds,safetyPulseSeconds,30.0);
        }
        static final Codec<Wires> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("layoutId").forGetter(Wires::layoutId),
                Codec.STRING.listOf().xmap(Set::copyOf, values -> values.stream().sorted().toList()).fieldOf("supportedActions").forGetter(Wires::supportedActions),
                Codec.DOUBLE.fieldOf("powerOnePulseSeconds").forGetter(Wires::powerOnePulseSeconds),Codec.DOUBLE.fieldOf("powerTwoPulseSeconds").forGetter(Wires::powerTwoPulseSeconds),
                Codec.DOUBLE.fieldOf("timingCutModifier").forGetter(Wires::timingCutModifier),Codec.DOUBLE.fieldOf("timingPulseModifier").forGetter(Wires::timingPulseModifier),Codec.DOUBLE.fieldOf("timingPulseSeconds").forGetter(Wires::timingPulseSeconds),Codec.DOUBLE.fieldOf("safetyPulseSeconds").forGetter(Wires::safetyPulseSeconds),Codec.DOUBLE.optionalFieldOf("logPulseSeconds",30.0).forGetter(Wires::logPulseSeconds)
        ).apply(i, Wires::new));
    }

    private static final Codec<AirlockData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Door.CODEC.fieldOf("door").forGetter(AirlockData::door), Airlock.CODEC.fieldOf("airlock").forGetter(AirlockData::airlock),
            DoorBolt.CODEC.fieldOf("doorBolt").forGetter(AirlockData::doorBolt),
             ApcPowerReceiver.CODEC.fieldOf("apcPowerReceiver").forGetter(AirlockData::apcPowerReceiver),
             ExtensionCableReceiver.CODEC.fieldOf("extensionCableReceiver").forGetter(AirlockData::extensionCableReceiver),
             WiresPanel.CODEC.fieldOf("wiresPanel").forGetter(AirlockData::wiresPanel),WiresPanelSecurity.CODEC.fieldOf("wiresPanelSecurity").forGetter(AirlockData::wiresPanelSecurity),
             Wires.CODEC.fieldOf("wires").forGetter(AirlockData::wires),
             ElectrifiedData.CODEC.optionalFieldOf("electrified", ElectrifiedData.AIRLOCK_DEFAULTS).forGetter(AirlockData::electrified),
              ExamineMetadata.CODEC.optionalFieldOf("metadata", ExamineMetadata.EMPTY).forGetter(AirlockData::metadata),
              DeviceComponents.CODEC.optionalFieldOf("deviceComponents", DeviceComponents.empty()).forGetter(AirlockData::deviceComponents)
     ).apply(i, AirlockData::new));
    public static final Codec<AirlockData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<AirlockData,T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement e ? e : ops.convertTo(JsonOps.INSTANCE,input);
                audit(json);
                return STRUCTURAL.decode(ops,input).flatMap(pair -> {
                    try { pair.getFirst().validate(); return DataResult.success(pair); }
                    catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
                });
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid airlock: " + e.getMessage()); }
        }
    });
    public void validate() {
        if (door == null || airlock == null || doorBolt == null || apcPowerReceiver == null || extensionCableReceiver == null || wiresPanel == null || wiresPanelSecurity==null||wires == null || electrified == null) throw new IllegalArgumentException("missing composed airlock component");
        positive(door.openTimeOneSeconds(), "openTimeOne"); positive(door.openTimeTwoSeconds(), "openTimeTwo");
        positive(door.closeTimeOneSeconds(), "closeTimeOne"); positive(door.closeTimeTwoSeconds(), "closeTimeTwo");
        positive(door.denyDurationSeconds(), "denyDuration"); positive(door.crushDamageBlunt(), "crushDamage"); positive(door.crushStunSeconds(),"crushStunSeconds");
        if (!Double.isFinite(apcPowerReceiver.requestedWatts()) || apcPowerReceiver.requestedWatts() <= 0
                || apcPowerReceiver.requestedWatts() > com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop.VENT_MAX_DEMAND_WATTS
                ) throw new IllegalArgumentException("invalid airlock APC receiver");
        if (extensionCableReceiver.localReachBlocks() < 1 || extensionCableReceiver.localReachBlocks() > 16) throw new IllegalArgumentException("invalid airlock extension cable receiver");
        positive(airlock.autoCloseSeconds(), "autoCloseSeconds"); positive(airlock.unboltedPryModifier(), "unboltedPryModifier");
        if (airlock.boltedPryMultiplier() < 1 || airlock.boltedPryMultiplier() > 20) throw new IllegalArgumentException("invalid bolted pry modifier");
        positive(wiresPanel.toggleDelaySeconds(), "panel toggle delay");if(wiresPanel.openingToolQuality()==null)throw new IllegalArgumentException("missing panel opening quality");
        if(!"moonstation14:airlock/panel_open".equals(wiresPanel.openSound())||!"moonstation14:airlock/panel_close".equals(wiresPanel.closeSound()))throw new IllegalArgumentException("unsupported airlock panel sounds");
        if (!"standard_airlock".equals(wires.layoutId()) || wires.supportedActions().size()!=8 || !Set.of("power_one", "power_two", "log", "bolt", "bolt_light", "timing", "safety", "ai_interact").equals(wires.supportedActions())) throw new IllegalArgumentException("unsupported airlock wire layout/action");
        positive(wires.powerOnePulseSeconds(),"powerOnePulseSeconds");positive(wires.powerTwoPulseSeconds(),"powerTwoPulseSeconds");positive(wires.timingCutModifier(),"timingCutModifier");positive(wires.timingPulseModifier(),"timingPulseModifier");positive(wires.timingPulseSeconds(),"timingPulseSeconds");positive(wires.safetyPulseSeconds(),"safetyPulseSeconds");positive(wires.logPulseSeconds(),"logPulseSeconds");
    }
    private static void positive(double d,String field) { if(!Double.isFinite(d)||d<=0) throw new IllegalArgumentException(field+" must be finite and positive"); }
    private static void audit(JsonElement json) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("expected object"); JsonObject o=json.getAsJsonObject();
           fields(o,Set.of("id","parent","abstract","door","airlock","doorBolt","apcPowerReceiver","extensionCableReceiver","wiresPanel","wiresPanelSecurity","wires","electrified","metadata","deviceComponents"));
          if(o.has("metadata")) nested(o,"metadata",Set.of("name","description"));
        nested(o,"door",Set.of("openTimeOneSeconds","openTimeTwoSeconds","closeTimeOneSeconds","closeTimeTwoSeconds","denyDurationSeconds","crushDamageBlunt","crushStunSeconds","bumpOpen"));
        nested(o,"airlock",Set.of("safety","autoClose","autoCloseSeconds","unboltedPryModifier","boltedPryMultiplier","openPanelVisible"));
         nested(o,"doorBolt",Set.of("boltLightsEnabled")); nested(o,"apcPowerReceiver",Set.of("requestedWatts")); nested(o,"extensionCableReceiver",Set.of("localReachBlocks"));
          nested(o,"wiresPanel",Set.of("openingToolQuality","toggleDelaySeconds","openSound","closeSound"));nested(o,"wiresPanelSecurity",Set.of("wiresAccessible")); nested(o,"wires",Set.of("layoutId","supportedActions","powerOnePulseSeconds","powerTwoPulseSeconds","timingCutModifier","timingPulseModifier","timingPulseSeconds","safetyPulseSeconds","logPulseSeconds"));
           if(o.has("electrified"))nested(o,"electrified",Set.of("enabled","usesApcPower","requirePower","shockDamage","shockSeconds","electrificationCancelSeconds","probability","siemensCoefficient","onTouch","onBump","onUsing","onAttacked"));
            if(o.has("deviceComponents")){ JsonObject d=o.getAsJsonObject("deviceComponents"); fields(d,Set.of("deviceNetwork","deviceConnection","deviceLinkSource","deviceLinkSink","deviceList","soundOnOverload")); }
    }
    private static void nested(JsonObject o,String name,Set<String> allowed) { if(!o.has(name)||!o.get(name).isJsonObject()) throw new IllegalArgumentException("missing/invalid "+name); fields(o.getAsJsonObject(name),allowed); }
    private static void fields(JsonObject o,Set<String> allowed) { for(String k:o.keySet()) if(!allowed.contains(k)||o.get(k).isJsonNull()) throw new IllegalArgumentException("unknown or null airlock field: "+k); }
}
