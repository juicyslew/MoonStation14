package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.WieldState;
import com.juicyslew.moonstation14.ms14.hands.live.WieldHandPolicy;

import java.util.List;
import java.util.UUID;

/** Public, presentation-only body inventory state. Contains no owner/action credentials or storage contents. */
public record BodyAppearanceSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid,
                                     long revision, List<Entry> hands, List<Entry> equipment, PoseContext poseContext)
        implements CustomPacketPayload {
    public static final Type<BodyAppearanceSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyAppearanceSnapshot> STREAM_CODEC = StreamCodec.of(
            (b, s) -> { b.writeResourceLocation(s.dimension); b.writeVarInt(s.entityId); b.writeUUID(s.entityUuid); b.writeVarLong(s.revision); write(b,s.hands); write(b,s.equipment); writePose(b,s.poseContext); },
            b -> new BodyAppearanceSnapshot(b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readVarLong(),read(b),read(b),readPose(b)));
    public record Entry(String id, ItemPresentation item) { }
    /** Public rendering hint only; carries no ownership proof or action authority. */
    public record PoseContext(String activeHand, String wieldSourceHand, String wieldSupportHand) {
        public PoseContext { if (activeHand == null) activeHand = ""; }
        public boolean hasValidatedWield() { return wieldSourceHand != null && wieldSupportHand != null; }
    }
    public BodyAppearanceSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid, long revision,
                                  List<Entry> hands, List<Entry> equipment) {
        this(dimension, entityId, entityUuid, revision, hands, equipment, null);
    }
    private static void writePose(RegistryFriendlyByteBuf b, PoseContext pose) {
        b.writeBoolean(pose != null);
        if (pose != null) { b.writeUtf(pose.activeHand, 128); b.writeBoolean(pose.hasValidatedWield()); if (pose.hasValidatedWield()) { b.writeUtf(pose.wieldSourceHand,128); b.writeUtf(pose.wieldSupportHand,128); } }
    }
    private static PoseContext readPose(RegistryFriendlyByteBuf b) {
        if (!b.readBoolean()) return null;
        String active=b.readUtf(128);
        return b.readBoolean() ? new PoseContext(active,b.readUtf(128),b.readUtf(128)) : new PoseContext(active,null,null);
    }
    private static void write(RegistryFriendlyByteBuf b, List<Entry> entries) { b.writeVarInt(entries.size()); for (var e:entries) { b.writeUtf(e.id,128); b.writeBoolean(e.item!=null); if(e.item!=null) ItemPresentation.STREAM_CODEC.encode(b,e.item); } }
    private static List<Entry> read(RegistryFriendlyByteBuf b) { int n=b.readVarInt(); if(n<0||n>64) throw new IllegalArgumentException("invalid appearance entry count"); var out=new java.util.ArrayList<Entry>(n); for(int i=0;i<n;i++) out.add(new Entry(b.readUtf(128),b.readBoolean()?ItemPresentation.STREAM_CODEC.decode(b):null)); return List.copyOf(out); }
    public BodyAppearanceSnapshot { hands=List.copyOf(hands); equipment=List.copyOf(equipment); poseContext=validatePose(poseContext,hands); }
    private static PoseContext validatePose(PoseContext pose, List<Entry> hands) {
        if (pose == null) return null;
        boolean active=hands.stream().anyMatch(e -> e.id().equals(pose.activeHand));
        if (!active) return null;
        if (!pose.hasValidatedWield()) return new PoseContext(pose.activeHand,null,null);
        Entry source=hands.stream().filter(e -> e.id().equals(pose.wieldSourceHand)).findFirst().orElse(null);
        Entry support=hands.stream().filter(e -> e.id().equals(pose.wieldSupportHand)).findFirst().orElse(null);
        if (source==null || source.item()==null || support==null || support.item()!=null || source.id().equals(support.id()))
            return new PoseContext(pose.activeHand,null,null);
        return pose;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static BodyAppearanceSnapshot from(Entity body, LiveHands state) {
        if (body == null || state == null || !state.compatible(body)) return null;
        var handEntries=state.handIds().stream().map(id -> new Entry(id,state.stackCopy(id).map(s -> s.getCount()>1 ? s.copyWithCount(1) : s).map(s -> ItemPresentation.fromStack(s, body.level())).orElse(null))).toList();
        var slots=LiveHands.equipmentSnapshot(body,state).orElse(null);
        var equipment=slots==null ? List.<Entry>of() : slots.slots().stream().map(slot -> new Entry(slot.id(),slot.id().equals("pocket1") || slot.id().equals("pocket2") ? null : slot.stack().map(s -> s.getCount()>1 ? s.copyWithCount(1) : s).map(s -> ItemPresentation.fromStack(s, body.level())).orElse(null))).toList();
        var wield=body instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.WIELD_STATE.get()) : null;
        PoseContext pose=new PoseContext(state.activeHand(),null,null);
        if (wield instanceof WieldState w && WieldHandPolicy.validates(body instanceof net.minecraft.world.entity.LivingEntity l ? l : null,state,w))
            pose=new PoseContext(state.activeHand(),w.sourceHand(),w.supportHand());
        return new BodyAppearanceSnapshot(body.level().dimension().location(),body.getId(),body.getUUID(),state.revision(),handEntries,equipment,pose);
    }
}
