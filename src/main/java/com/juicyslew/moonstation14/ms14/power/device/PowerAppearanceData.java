package com.juicyslew.moonstation14.ms14.power.device;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved, display-only profile for the three exported power-machine families. */
public record PowerAppearanceData(String kind, String inactive, String active, String charging,
                                  String full, String lack, int lightLevel, Shape shape) {
    public record Shape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("minX").forGetter(Shape::minX), Codec.DOUBLE.fieldOf("minY").forGetter(Shape::minY),
                Codec.DOUBLE.fieldOf("minZ").forGetter(Shape::minZ), Codec.DOUBLE.fieldOf("maxX").forGetter(Shape::maxX),
                Codec.DOUBLE.fieldOf("maxY").forGetter(Shape::maxY), Codec.DOUBLE.fieldOf("maxZ").forGetter(Shape::maxZ)
        ).apply(i, Shape::new));
        public void validate() {
            if (!finite(minX,minY,minZ,maxX,maxY,maxZ) || minX<0 || minY<0 || minZ<0
                    || maxX>16 || maxY>16 || maxZ>16 || minX>=maxX || minY>=maxY || minZ>=maxZ)
                throw new IllegalArgumentException("invalid power appearance geometry");
        }
    }
    public static final Codec<PowerAppearanceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(PowerAppearanceData::kind),
            Codec.STRING.fieldOf("inactive").forGetter(PowerAppearanceData::inactive),
            Codec.STRING.fieldOf("active").forGetter(PowerAppearanceData::active),
            Codec.STRING.fieldOf("charging").forGetter(PowerAppearanceData::charging),
            Codec.STRING.fieldOf("full").forGetter(PowerAppearanceData::full),
            Codec.STRING.fieldOf("lack").forGetter(PowerAppearanceData::lack),
            Codec.INT.fieldOf("lightLevel").forGetter(PowerAppearanceData::lightLevel),
            Shape.CODEC.fieldOf("shape").forGetter(PowerAppearanceData::shape)
    ).apply(i, PowerAppearanceData::new));
    public void validate() {
        if (!java.util.Set.of("hv_source","hv_mv_substation","apc").contains(kind))
            throw new IllegalArgumentException("unsupported power appearance kind: " + kind);
        for (String state : new String[]{inactive,active,charging,full,lack})
            if (!java.util.Set.of("off","on","fixed_on","unpowered","dead","charging","full","lack").contains(state))
                throw new IllegalArgumentException("invalid native appearance state: " + state);
        if (lightLevel < 0 || lightLevel > 15) throw new IllegalArgumentException("invalid power appearance light level");
        shape.validate();
    }
    private static boolean finite(double... values) { for(double v:values) if(!Double.isFinite(v)) return false; return true; }
}
