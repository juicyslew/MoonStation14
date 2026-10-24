package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Point-in-time action availability plus the exact query proof echoed for client correlation. */
public record BodyTargetActionSnapshot(long bodyEpoch, long sequence, long expectedRevision,
                                       String handId, String expectedToken, UUID targetId,
                                       Component targetName, List<ActionEntry> actions) implements CustomPacketPayload {
    public enum ActionId {
        BUTCHER, EMPTY_REVOLVER, SPIN_REVOLVER;
        public BodyHandActionRequest.Action requestAction() { return switch (this) {
            case BUTCHER -> BodyHandActionRequest.Action.BUTCHER;
            case EMPTY_REVOLVER -> BodyHandActionRequest.Action.GUN_EMPTY;
            case SPIN_REVOLVER -> BodyHandActionRequest.Action.GUN_SPIN;
        }; }
    }
    public record ActionEntry(ActionId id, Component label, boolean enabled, Component disabledReason) {
        public ActionEntry {
            if (id == null || label == null || enabled && disabledReason != null || !enabled && disabledReason == null)
                throw new IllegalArgumentException("Invalid target action entry");
        }
    }
    public static final Type<BodyTargetActionSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_target_action_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyTargetActionSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.bodyEpoch()); buf.writeVarLong(value.sequence()); buf.writeVarLong(value.expectedRevision());
                buf.writeUtf(value.handId(), HandState.MAX_ID_LENGTH); buf.writeBoolean(value.expectedToken() != null);
                if (value.expectedToken() != null) buf.writeUtf(value.expectedToken(), ItemToken.MAX_LENGTH);
                buf.writeUUID(value.targetId()); ComponentSerialization.STREAM_CODEC.encode(buf, value.targetName());
                buf.writeVarInt(value.actions().size());
                for (var entry : value.actions()) {
                    buf.writeEnum(entry.id()); ComponentSerialization.STREAM_CODEC.encode(buf, entry.label());
                    buf.writeBoolean(entry.enabled()); buf.writeBoolean(entry.disabledReason() != null);
                    if (entry.disabledReason() != null) ComponentSerialization.STREAM_CODEC.encode(buf, entry.disabledReason());
                }
            }, buf -> {
                long epoch = buf.readVarLong(), sequence = buf.readVarLong(), revision = buf.readVarLong();
                String hand = buf.readUtf(HandState.MAX_ID_LENGTH); int tokenFlag = buf.readUnsignedByte();
                if (tokenFlag > 1) throw new IllegalArgumentException("Invalid target-action token flag");
                String token = tokenFlag == 1 ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                UUID target = buf.readUUID(); Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
                int count = buf.readVarInt(); if (count < 0 || count > ActionId.values().length) throw new IllegalArgumentException("Invalid target action count");
                var entries = new java.util.ArrayList<ActionEntry>(count);
                for (int i = 0; i < count; i++) {
                    ActionId id = buf.readEnum(ActionId.class); Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
                    boolean enabled = buf.readBoolean(); int reasonFlag = buf.readUnsignedByte();
                    if (reasonFlag > 1) throw new IllegalArgumentException("Invalid target-action reason flag");
                    entries.add(new ActionEntry(id, label, enabled, reasonFlag == 1 ? ComponentSerialization.STREAM_CODEC.decode(buf) : null));
                }
                if (buf.isReadable()) throw new IllegalArgumentException("Trailing target-action snapshot data");
                return new BodyTargetActionSnapshot(epoch, sequence, revision, hand, token, target, name, entries);
            });
    /** Source compatibility for existing Butcher UI callers while it migrates to row lists. */
    public BodyTargetActionSnapshot(long epoch, long sequence, long revision, String hand, String token, UUID target,
                                    Component name, boolean hasButcher, boolean enabled, Component reason) {
        this(epoch, sequence, revision, hand, token, target, name, hasButcher
                ? List.of(new ActionEntry(ActionId.BUTCHER, Component.translatable("moonstation14.target_action.butcher"), enabled, reason)) : List.of());
    }
    public BodyTargetActionSnapshot {
        actions = List.copyOf(actions);
        if (bodyEpoch <= 0 || sequence <= 0 || expectedRevision < 0 || handId == null || handId.isBlank()
                || handId.length() > HandState.MAX_ID_LENGTH || expectedToken != null
                && (expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH)
                || targetId == null || targetName == null || actions.size() > ActionId.values().length
                || new HashSet<>(actions.stream().map(ActionEntry::id).toList()).size() != actions.size())
            throw new IllegalArgumentException("Invalid target-action snapshot");
    }
    public boolean hasButcher() { return action(ActionId.BUTCHER) != null; }
    public boolean enabled() { var e = action(ActionId.BUTCHER); return e != null && e.enabled(); }
    public Component disabledReason() { var e = action(ActionId.BUTCHER); return e == null ? null : e.disabledReason(); }
    private ActionEntry action(ActionId id) { return actions.stream().filter(a -> a.id() == id).findFirst().orElse(null); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
