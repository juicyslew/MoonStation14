package com.juicyslew.moonstation14.ms14.damage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative independent hurt-presentation flags for one native hurt event. */
public record DamageFeedbackPayload(int entityId, UUID entityUuid, boolean redFlash,
                                   boolean cameraShake, int duration) implements CustomPacketPayload {
    private static volatile Consumer<DamageFeedbackPayload> dispatchObserver;
    public static final Type<DamageFeedbackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "damage_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DamageFeedbackPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeUUID(value.entityUuid);
                buf.writeBoolean(value.redFlash);
                buf.writeBoolean(value.cameraShake);
                buf.writeVarInt(value.duration);
            }, buf -> new DamageFeedbackPayload(buf.readVarInt(), buf.readUUID(), buf.readBoolean(),
                    buf.readBoolean(), buf.readVarInt()));

    public DamageFeedbackPayload {
        if (entityId < 0 || duration < 0 || duration > 40) throw new IllegalArgumentException("invalid damage feedback payload");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void publish(LivingEntity entity, DamageFeedbackProfile profile) {
        var payload = new DamageFeedbackPayload(entity.getId(), entity.getUUID(), profile.redFlash(),
                profile.cameraShake(), 10);
        Consumer<DamageFeedbackPayload> observer = dispatchObserver;
        if (observer != null) observer.accept(payload);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }
    /** Scoped observation seam for server integration fixtures; null restores normal dispatch. */
    public static void observeDispatch(Consumer<DamageFeedbackPayload> observer) { dispatchObserver = observer; }
}

