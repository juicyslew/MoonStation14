package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Local 3D adaptation policy; deliberately not an SS14 YAML component. */
public record AngularRecoveryComponent(double graceSeconds, double recoverySeconds,
                                       double badLandingTiltDegrees, double badLandingAngularSpeed,
                                       double comfortTiltDegrees, double comfortAngularSpeed,
                                       double knockdownSeconds, boolean comfortReturnEnabled)
        implements CharacterComponent {
    public static final String TYPE = "AngularRecovery";
    public static final AngularRecoveryComponent DEFAULT = new AngularRecoveryComponent(.65, 1.0, 45, 1, 10, .25, .5, true);
    public static final Codec<AngularRecoveryComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(AngularRecoveryComponent::type),
            Codec.DOUBLE.optionalFieldOf("grace_seconds", DEFAULT.graceSeconds).forGetter(AngularRecoveryComponent::graceSeconds),
            Codec.DOUBLE.optionalFieldOf("recovery_seconds", DEFAULT.recoverySeconds).forGetter(AngularRecoveryComponent::recoverySeconds),
            Codec.DOUBLE.optionalFieldOf("bad_landing_tilt_degrees", DEFAULT.badLandingTiltDegrees).forGetter(AngularRecoveryComponent::badLandingTiltDegrees),
            Codec.DOUBLE.optionalFieldOf("bad_landing_angular_speed", DEFAULT.badLandingAngularSpeed).forGetter(AngularRecoveryComponent::badLandingAngularSpeed),
            Codec.DOUBLE.optionalFieldOf("comfort_tilt_degrees", DEFAULT.comfortTiltDegrees).forGetter(AngularRecoveryComponent::comfortTiltDegrees),
            Codec.DOUBLE.optionalFieldOf("comfort_angular_speed", DEFAULT.comfortAngularSpeed).forGetter(AngularRecoveryComponent::comfortAngularSpeed),
            Codec.DOUBLE.optionalFieldOf("knockdown_seconds", DEFAULT.knockdownSeconds).forGetter(AngularRecoveryComponent::knockdownSeconds),
            Codec.BOOL.optionalFieldOf("comfort_return_enabled", DEFAULT.comfortReturnEnabled).forGetter(AngularRecoveryComponent::comfortReturnEnabled)
    ).apply(i, (type, grace, recovery, tilt, spin, comfortTilt, comfortSpin, knockdown, comfort) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        AngularRecoveryComponent c = new AngularRecoveryComponent(grace, recovery, tilt, spin, comfortTilt, comfortSpin, knockdown, comfort);
        if (!Double.isFinite(grace) || grace < 0 || !Double.isFinite(recovery) || recovery <= 0 ||
                !Double.isFinite(tilt) || tilt <= 0 || tilt > 180 || !Double.isFinite(spin) || spin < 0 ||
                !Double.isFinite(comfortTilt) || comfortTilt < 0 || comfortTilt > tilt ||
                !Double.isFinite(comfortSpin) || comfortSpin < 0 || !Double.isFinite(knockdown) || knockdown < 0)
            throw new IllegalArgumentException("invalid AngularRecovery policy values");
        return c;
    }));
    @Override public String type() { return TYPE; }
    public static AngularRecoveryComponent fromJsonValues(double grace, double recovery, double tilt, double spin,
            double comfortTilt, double comfortSpin, double knockdown, boolean comfort) {
        return validate(new AngularRecoveryComponent(grace,recovery,tilt,spin,comfortTilt,comfortSpin,knockdown,comfort));
    }
    private static AngularRecoveryComponent validate(AngularRecoveryComponent c) {
        if (!Double.isFinite(c.graceSeconds)||c.graceSeconds<0||!Double.isFinite(c.recoverySeconds)||c.recoverySeconds<=0||
                !Double.isFinite(c.badLandingTiltDegrees)||c.badLandingTiltDegrees<=0||c.badLandingTiltDegrees>180||
                !Double.isFinite(c.badLandingAngularSpeed)||c.badLandingAngularSpeed<0||!Double.isFinite(c.comfortTiltDegrees)||
                c.comfortTiltDegrees<0||c.comfortTiltDegrees>c.badLandingTiltDegrees||!Double.isFinite(c.comfortAngularSpeed)||
                c.comfortAngularSpeed<0||!Double.isFinite(c.knockdownSeconds)||c.knockdownSeconds<0) throw new IllegalArgumentException("invalid AngularRecovery policy values");
        return c;
    }
    public AngularRecoveryComponent {
        validateRaw(graceSeconds,recoverySeconds,badLandingTiltDegrees,badLandingAngularSpeed,comfortTiltDegrees,comfortAngularSpeed,knockdownSeconds);
    }
    private static void validateRaw(double grace,double recovery,double tilt,double spin,double comfortTilt,double comfortSpin,double knockdown) {
        if (!Double.isFinite(grace)||grace<0||!Double.isFinite(recovery)||recovery<=0||!Double.isFinite(tilt)||tilt<=0||tilt>180||!Double.isFinite(spin)||spin<0||!Double.isFinite(comfortTilt)||comfortTilt<0||comfortTilt>tilt||!Double.isFinite(comfortSpin)||comfortSpin<0||!Double.isFinite(knockdown)||knockdown<0) throw new IllegalArgumentException("invalid AngularRecovery policy values");
    }
}
