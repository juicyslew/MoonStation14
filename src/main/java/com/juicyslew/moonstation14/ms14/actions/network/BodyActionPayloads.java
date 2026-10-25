package com.juicyslew.moonstation14.ms14.actions.network;

import com.juicyslew.moonstation14.ms14.actions.*;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Wire envelopes for the authoritative body-action service. */
public final class BodyActionPayloads {
    private BodyActionPayloads() { }
    private static final String MOD="moonstation14";
    private static CustomPacketPayload.Type<?> type(String path) { return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MOD,path)); }
    private static UUID readUuid(FriendlyByteBuf b) { return b.readUUID(); }
    private static void writeSnapshot(FriendlyByteBuf b, BodyActionSnapshot s) {
        b.writeUUID(s.bodyId()); b.writeVarLong(s.bodyEpoch()); b.writeVarLong(s.revision()); b.writeVarLong(s.serverGameTime());
        b.writeCollection(s.actions(), (buf,e)-> { buf.writeResourceLocation(e.actionId()); buf.writeUtf(e.providerId(),128); buf.writeUtf(e.displayName(),256); buf.writeResourceLocation(e.icon()); buf.writeOptional(e.iconOn(),FriendlyByteBuf::writeResourceLocation); buf.writeEnum(e.kind()); buf.writeBoolean(e.enabled()); buf.writeBoolean(e.active()); buf.writeVarLong(e.cooldownEndTick()); buf.writeVarInt(e.useDelayTicks()); buf.writeUtf(e.disabledReason(),256); buf.writeVarInt(e.priority()); });
    }
    private static BodyActionSnapshot readSnapshot(FriendlyByteBuf b) {
        UUID id=b.readUUID(); long epoch=b.readVarLong(), revision=b.readVarLong(), time=b.readVarLong();
        int count=b.readVarInt();
        if(count<0 || count>256) throw new IllegalArgumentException("Too many body actions");
        List<BodyActionSnapshot.Entry> entries=new ArrayList<>(count);
        for(int i=0;i<count;i++) entries.add(new BodyActionSnapshot.Entry(b.readResourceLocation(),b.readUtf(128),b.readUtf(256),b.readResourceLocation(),b.readOptional(FriendlyByteBuf::readResourceLocation),b.readEnum(ActionPrototype.Kind.class),b.readBoolean(),b.readBoolean(),b.readVarLong(),b.readVarInt(),b.readUtf(256),b.readVarInt()));
        return new BodyActionSnapshot(id,epoch,revision,time,entries);
    }
    public record Query() implements CustomPacketPayload { public static final Type<Query> TYPE=(Type<Query>)BodyActionPayloads.type("body_action_query"); public static final StreamCodec<FriendlyByteBuf,Query> STREAM_CODEC=StreamCodec.unit(new Query()); public Type<? extends CustomPacketPayload> type(){return TYPE;} }
    public record Activate(BodyActionRequest request) implements CustomPacketPayload {
        public static final Type<Activate> TYPE=(Type<Activate>)BodyActionPayloads.type("body_action_activate");
        public static final StreamCodec<FriendlyByteBuf,Activate> STREAM_CODEC=StreamCodec.of((b,p)->{var r=p.request;b.writeUUID(r.bodyId());b.writeVarLong(r.bodyEpoch());b.writeVarLong(r.revision());b.writeResourceLocation(r.actionId());b.writeUtf(r.providerId(),128);b.writeVarLong(r.sequence());},b->new Activate(new BodyActionRequest(b.readUUID(),b.readVarLong(),b.readVarLong(),b.readResourceLocation(),b.readUtf(128),b.readVarLong())));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(Optional<BodyActionSnapshot> snapshot) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=(Type<Snapshot>)BodyActionPayloads.type("body_action_snapshot"); public static final StreamCodec<FriendlyByteBuf,Snapshot> STREAM_CODEC=StreamCodec.of((b,p)->b.writeOptional(p.snapshot,BodyActionPayloads::writeSnapshot),b->new Snapshot(b.readOptional(BodyActionPayloads::readSnapshot))); public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Result(BodyActionResult result) implements CustomPacketPayload {
        public static final Type<Result> TYPE=(Type<Result>)BodyActionPayloads.type("body_action_result"); public static final StreamCodec<FriendlyByteBuf,Result> STREAM_CODEC=StreamCodec.of((b,p)->{var r=p.result;b.writeEnum(r.status());b.writeUtf(r.reason(),256);b.writeVarLong(r.sequence());b.writeOptional(r.snapshot(),BodyActionPayloads::writeSnapshot);},b->new Result(new BodyActionResult(b.readEnum(BodyActionResult.Status.class),b.readUtf(256),b.readVarLong(),b.readOptional(BodyActionPayloads::readSnapshot)))); public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
