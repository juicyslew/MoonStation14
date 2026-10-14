package com.juicyslew.moonstation14.ms14.devicenet.control;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceComponents;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolved wallmount composition and prototype-authored toggle output policy. */
public record SignalControlData(WallMount wallMount, DeviceComponents deviceComponents,
                                List<Output> onOutputs, List<Output> offOutputs, String clickSound) {
    public record WallMount(boolean anchored, boolean nonColliding, boolean allowVerticalMounts) {
        static final Codec<WallMount> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("anchored").forGetter(WallMount::anchored),
                Codec.BOOL.fieldOf("nonColliding").forGetter(WallMount::nonColliding),
                Codec.BOOL.optionalFieldOf("allowVerticalMounts", true).forGetter(WallMount::allowVerticalMounts)
        ).apply(i, WallMount::new));
    }
    public record Output(String port, DeviceSignal signal) {
        static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("port").forGetter(Output::port),
                Codec.STRING.xmap(DeviceSignal::valueOf, DeviceSignal::name).fieldOf("signal").forGetter(Output::signal)
        ).apply(i, Output::new));
        public Output { if (port == null || port.isBlank() || signal == null) throw new IllegalArgumentException("invalid control output"); }
    }
    public static final Codec<SignalControlData> CODEC = RecordCodecBuilder.create(i -> i.group(
            WallMount.CODEC.fieldOf("wallMount").forGetter(SignalControlData::wallMount),
            DeviceComponents.CODEC.fieldOf("deviceComponents").forGetter(SignalControlData::deviceComponents),
            Output.CODEC.listOf().fieldOf("onOutputs").forGetter(SignalControlData::onOutputs),
            Output.CODEC.listOf().fieldOf("offOutputs").forGetter(SignalControlData::offOutputs),
            Codec.STRING.optionalFieldOf("clickSound", "moonstation14:device_lightswitch").forGetter(SignalControlData::clickSound)
    ).apply(i, SignalControlData::new));
    public SignalControlData {
        Objects.requireNonNull(wallMount); Objects.requireNonNull(deviceComponents);
        onOutputs = List.copyOf(onOutputs); offOutputs = List.copyOf(offOutputs);
        Objects.requireNonNull(clickSound);
        if (!wallMount.anchored() || !wallMount.nonColliding()) throw new IllegalArgumentException("signal controls require anchored non-colliding wallmount composition");
        for (Output output : onOutputs) Objects.requireNonNull(output);
        for (Output output : offOutputs) Objects.requireNonNull(output);
    }
    public List<Output> outputs(boolean isOn) { return isOn ? onOutputs : offOutputs; }
    public void validateResolvedOutputs(DeviceComposition composition) {
        var sources=composition.sources().stream().map(com.juicyslew.moonstation14.ms14.devicenet.DevicePort::id).collect(java.util.stream.Collectors.toSet());
        for(Output output:onOutputs)if(!sources.contains(output.port()))throw new IllegalArgumentException("on output is not a resolved source port: "+output.port());
        for(Output output:offOutputs)if(!sources.contains(output.port()))throw new IllegalArgumentException("off output is not a resolved source port: "+output.port());
    }
}

