package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.UUID;

/** Observer wire messages contain only semantic input and server-authored identity snapshots. */
public final class ObserverPayloads {
    private ObserverPayloads() { }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "observer_" + path));
    }
    public enum Mode { CORPSE, GHOST, END }
    public record Snapshot(Mode mode, long epoch, UUID body, int bodyEntity, UUID ghost, int ghostEntity, boolean returnAvailable) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=ObserverPayloads.type("snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeEnum(v.mode);b.writeLong(v.epoch);b.writeUUID(v.body);b.writeVarInt(v.bodyEntity);b.writeBoolean(v.ghost!=null);if(v.ghost!=null){b.writeUUID(v.ghost);b.writeVarInt(v.ghostEntity);}b.writeBoolean(v.returnAvailable);},b->{Mode m=b.readEnum(Mode.class);long e=b.readLong();UUID body=b.readUUID();int bi=b.readVarInt();UUID g=b.readBoolean()?b.readUUID():null;int gi=g==null?-1:b.readVarInt();return new Snapshot(m,e,body,bi,g,gi,b.readBoolean());});
        public Snapshot { if(mode==null||epoch<0||body==null||bodyEntity< -1||ghostEntity< -1)throw new IllegalArgumentException("invalid observer snapshot"); }
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Request(Kind kind,long epoch) implements CustomPacketPayload {
        public enum Kind { QUERY, GHOST, RETURN }
        public static final Type<Request> TYPE=ObserverPayloads.type("request");
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeEnum(v.kind);b.writeLong(v.epoch);},b->new Request(b.readEnum(Kind.class),b.readLong()));
        public Request {if(kind==null||epoch<0)throw new IllegalArgumentException("invalid observer request");}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Intent(long epoch,int strafe,int forward,int vertical,boolean sprint,float yaw) implements CustomPacketPayload {
        public static final Type<Intent> TYPE=ObserverPayloads.type("intent");
        /** Builds a wire intent from continuous camera yaw. */
        public static Intent fromCamera(long epoch,int strafe,int forward,int vertical,boolean sprint,float yaw) {
            if (!Float.isFinite(yaw)) throw new IllegalArgumentException("invalid observer intent");
            return new Intent(epoch,strafe,forward,vertical,sprint,Mth.wrapDegrees(yaw));
        }
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeLong(v.epoch);b.writeShort(v.strafe);b.writeShort(v.forward);b.writeByte(v.vertical);b.writeBoolean(v.sprint);b.writeFloat(v.yaw);},b->new Intent(b.readLong(),b.readShort(),b.readShort(),b.readByte(),b.readBoolean(),b.readFloat()));
        public Intent {if(epoch<0||strafe< -1000||strafe>1000||forward< -1000||forward>1000||vertical< -1||vertical>1||!Float.isFinite(yaw))throw new IllegalArgumentException("invalid observer intent"); yaw=Mth.wrapDegrees(yaw);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
