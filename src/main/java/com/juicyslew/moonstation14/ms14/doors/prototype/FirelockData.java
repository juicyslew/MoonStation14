package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComponents;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmableData;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Resolved supported Firelock -> BaseFirelock -> BaseStructure composition. */
public record FirelockData(AirlockData.Door door, AirlockData.ApcPowerReceiver apcPowerReceiver,
                           AirlockData.ExtensionCableReceiver extensionCableReceiver, boolean bumpOpen, boolean clickOpen,
                           boolean initiallyOpen, Timing timing,
                           boolean airtightWhenCollidable, DeviceComponents deviceComponents,
                            AtmosAlarmableData atmosAlarmableData, AirlockData.Collision collision) {
    /** Resolved pressure/pry/retry and authored animation timing fields from the flat prototype map. */
    public record Timing(double pressureDifferenceKpa,double temperatureDifferenceK,double pryModifier,
                         double handPryTimeModifier,double pryCooldownSeconds,double retrySeconds,
                         double openingAnimationSeconds,double closingAnimationSeconds) {
        private static final com.mojang.serialization.MapCodec<Timing> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
                Codec.DOUBLE.fieldOf("pressureDifferenceKpa").forGetter(Timing::pressureDifferenceKpa),
                Codec.DOUBLE.fieldOf("temperatureDifferenceK").forGetter(Timing::temperatureDifferenceK),
                Codec.DOUBLE.fieldOf("pryModifier").forGetter(Timing::pryModifier),
                Codec.DOUBLE.fieldOf("handPryTimeModifier").forGetter(Timing::handPryTimeModifier),
                Codec.DOUBLE.fieldOf("pryCooldownSeconds").forGetter(Timing::pryCooldownSeconds),
                Codec.DOUBLE.fieldOf("retrySeconds").forGetter(Timing::retrySeconds),
                Codec.DOUBLE.fieldOf("openingAnimationSeconds").forGetter(Timing::openingAnimationSeconds),
                Codec.DOUBLE.fieldOf("closingAnimationSeconds").forGetter(Timing::closingAnimationSeconds)
        ).apply(i,Timing::new));
        public void validate(){for(double d:new double[]{pressureDifferenceKpa,temperatureDifferenceK,pryModifier,handPryTimeModifier,pryCooldownSeconds,retrySeconds,openingAnimationSeconds,closingAnimationSeconds})if(!Double.isFinite(d)||d<=0)throw new IllegalArgumentException("invalid firelock timing");}
    }
    private static final Codec<FirelockData> STRUCTURAL=RecordCodecBuilder.create(i->i.group(
            AirlockData.Door.CODEC.fieldOf("door").forGetter(FirelockData::door), AirlockData.ApcPowerReceiver.CODEC.fieldOf("apcPowerReceiver").forGetter(FirelockData::apcPowerReceiver),
            AirlockData.ExtensionCableReceiver.CODEC.fieldOf("extensionCableReceiver").forGetter(FirelockData::extensionCableReceiver),
            Codec.BOOL.fieldOf("bumpOpen").forGetter(FirelockData::bumpOpen),Codec.BOOL.fieldOf("clickOpen").forGetter(FirelockData::clickOpen),Codec.BOOL.fieldOf("initiallyOpen").forGetter(FirelockData::initiallyOpen),
             Timing.CODEC.forGetter(FirelockData::timing),
            Codec.BOOL.fieldOf("airtightWhenCollidable").forGetter(FirelockData::airtightWhenCollidable),DeviceComponents.CODEC.optionalFieldOf("deviceComponents",DeviceComponents.empty()).forGetter(FirelockData::deviceComponents),
            AtmosAlarmableData.CODEC.optionalFieldOf("atmosAlarmable",new AtmosAlarmableData(Set.of(),Set.of(),false)).forGetter(FirelockData::atmosAlarmableData),
            AirlockData.Collision.CODEC.optionalFieldOf("collision",new AirlockData.Collision(158,205)).forGetter(FirelockData::collision)
    ).apply(i,FirelockData::new));
    public static final Codec<FirelockData> CODEC=Codec.of(STRUCTURAL,new Decoder<>() {
        @Override public <T> DataResult<Pair<FirelockData,T>> decode(DynamicOps<T> ops,T input) {
            try {
                JsonElement json=input instanceof JsonElement e?e:ops.convertTo(JsonOps.INSTANCE,input);
                audit(json);
                return STRUCTURAL.decode(ops,input).flatMap(pair->{try{pair.getFirst().validate();return DataResult.success(pair);}catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}});
            } catch(RuntimeException e) { return DataResult.error(()->"invalid firelock: "+e.getMessage()); }
        }
    });
    public FirelockData { if(door==null||apcPowerReceiver==null||extensionCableReceiver==null||timing==null||deviceComponents==null||atmosAlarmableData==null||collision==null)throw new IllegalArgumentException("missing firelock composition"); }
    public double pressureDifferenceKpa(){return timing.pressureDifferenceKpa();} public double temperatureDifferenceK(){return timing.temperatureDifferenceK();}
    public double pryModifier(){return timing.pryModifier();} public double handPryTimeModifier(){return timing.handPryTimeModifier();}
    public double pryCooldownSeconds(){return timing.pryCooldownSeconds();} public double retrySeconds(){return timing.retrySeconds();}
    public double openingAnimationSeconds(){return timing.openingAnimationSeconds();} public double closingAnimationSeconds(){return timing.closingAnimationSeconds();}
    public void validate(){timing.validate();for(double d:new double[]{apcPowerReceiver.requestedWatts(),door.openTimeOneSeconds(),door.openTimeTwoSeconds(),door.closeTimeOneSeconds(),door.closeTimeTwoSeconds(),door.denyDurationSeconds(),door.crushDamageBlunt(),door.crushStunSeconds()})if(!Double.isFinite(d)||d<=0)throw new IllegalArgumentException("invalid firelock value");if(apcPowerReceiver.requestedWatts()>com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop.VENT_MAX_DEMAND_WATTS)throw new IllegalArgumentException("firelock APC demand exceeds supported bound");if(extensionCableReceiver.localReachBlocks()<1||extensionCableReceiver.localReachBlocks()>16)throw new IllegalArgumentException("invalid extension receiver");}
    private static void audit(JsonElement json) {
        if(!json.isJsonObject())throw new IllegalArgumentException("expected object"); JsonObject o=json.getAsJsonObject();
         AirlockData.fields(o,Set.of("id","parent","abstract","door","apcPowerReceiver","extensionCableReceiver","bumpOpen","clickOpen","initiallyOpen","pressureDifferenceKpa","temperatureDifferenceK","pryModifier","handPryTimeModifier","pryCooldownSeconds","retrySeconds","openingAnimationSeconds","closingAnimationSeconds","airtightWhenCollidable","deviceComponents","atmosAlarmable","collision"));
         AirlockData.nested(o,"door",Set.of("openTimeOneSeconds","openTimeTwoSeconds","closeTimeOneSeconds","closeTimeTwoSeconds","denyDurationSeconds","crushDamageBlunt","crushStunSeconds","bumpOpen","pryTimeSeconds"));
        AirlockData.nested(o,"apcPowerReceiver",Set.of("requestedWatts")); AirlockData.nested(o,"extensionCableReceiver",Set.of("localReachBlocks"));
        if(o.has("collision"))AirlockData.nested(o,"collision",Set.of("mask","layer"));
        if(o.has("atmosAlarmable"))AirlockData.nested(o,"atmosAlarmable",Set.of("sourceTags","syncWith","ignoreAlarms"));
        if(o.has("deviceComponents")) {
            JsonObject d=o.getAsJsonObject("deviceComponents"); AirlockData.fields(d,Set.of("deviceNetwork","deviceConnection","deviceLinkSource","deviceLinkSink","deviceList","soundOnOverload"));
            if(d.has("deviceNetwork"))AirlockData.nested(d,"deviceNetwork",Set.of("network","transmit","receive","transmitFrequency","receiveFrequency","addressPrefix","autoConnect","savableAddress","customAddress","receiveAll","broadcastHook","requiresPower"));
            if(d.has("deviceConnection"))AirlockData.nested(d,"deviceConnection",Set.of("wired","wireless","wirelessRange"));
            if(d.has("deviceLinkSource"))AirlockData.nested(d,"deviceLinkSource",Set.of("ports","range","lastSignals"));
            if(d.has("deviceLinkSink"))AirlockData.nested(d,"deviceLinkSink",Set.of("ports","limit"));
            if(d.has("deviceList"))AirlockData.nested(d,"deviceList",Set.of("capacity","allowList","filterIncoming"));
            if(d.has("soundOnOverload"))AirlockData.nested(d,"soundOnOverload",Set.of("sound","volumeModifier"));
        }
    }
}
