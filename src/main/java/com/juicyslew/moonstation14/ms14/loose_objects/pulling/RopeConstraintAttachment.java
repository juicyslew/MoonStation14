package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.ms14.character.components.TetherSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Ephemeral endpoint snapshots; velocities use blocks/second (not native blocks/tick). */
public record RopeConstraintAttachment(List<Constraint> constraints) {
    public enum EndpointRole { PULLER, PULLED }
    public record MotionSnapshot(Vec3 position, Vec3 velocityPerSecond) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MotionSnapshot> CODEC = StreamCodec.composite(
                vecCodec(), MotionSnapshot::position, vecCodec(), MotionSnapshot::velocityPerSecond, MotionSnapshot::new);
        public MotionSnapshot { if (!finite(position) || !finite(velocityPerSecond)) throw new IllegalArgumentException("invalid tether snapshot"); }
    }
    public record Constraint(UUID other, MotionSnapshot self, MotionSnapshot otherSnapshot,
                             double slack, TetherSettings tuning, EndpointRole role) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Constraint> CODEC = StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), Constraint::other,
                MotionSnapshot.CODEC, Constraint::self,
                MotionSnapshot.CODEC, Constraint::otherSnapshot,
                net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Constraint::slack,
                StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.DOUBLE,
                        TetherSettings::stiffness, net.minecraft.network.codec.ByteBufCodecs.DOUBLE,
                        TetherSettings::progressiveStiffness, net.minecraft.network.codec.ByteBufCodecs.DOUBLE,
                        TetherSettings::damping, net.minecraft.network.codec.ByteBufCodecs.DOUBLE,
                         TetherSettings::maximumAcceleration, net.minecraft.network.codec.ByteBufCodecs.DOUBLE,
                         TetherSettings::slackAllowance, TetherSettings::new), Constraint::tuning,
                net.minecraft.network.codec.ByteBufCodecs.idMapper(i -> EndpointRole.values()[i], Enum::ordinal), Constraint::role, Constraint::new);
        public Constraint {
            if (other == null || self == null || otherSnapshot == null
                    || !Double.isFinite(slack) || slack < 0 || tuning == null || role == null)
                throw new IllegalArgumentException("invalid tether endpoint");
        }
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeConstraintAttachment> STREAM_CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.collection(java.util.ArrayList::new,
                    Constraint.CODEC), RopeConstraintAttachment::constraints, RopeConstraintAttachment::new);
    public RopeConstraintAttachment { constraints = List.copyOf(constraints); if (constraints.size() > 2) throw new IllegalArgumentException("too many tether endpoints"); }
    private static StreamCodec<RegistryFriendlyByteBuf, Vec3> vecCodec() {
        return StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Vec3::x,
                net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Vec3::y,
                net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
