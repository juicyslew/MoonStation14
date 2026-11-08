package com.juicyslew.moonstation14.ms14.singularity.containment;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Presentation-only complete snapshot of authoritative containment field faces. */
public record FieldFaceSnapshotPayload(ResourceLocation dimension, List<Face> faces) implements CustomPacketPayload {
    public static final Type<FieldFaceSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "field_face_snapshot"));
    public static final int MAX_FACES = 4096;
    public static final StreamCodec<RegistryFriendlyByteBuf, FieldFaceSnapshotPayload> STREAM_CODEC = StreamCodec.of(FieldFaceSnapshotPayload::write, FieldFaceSnapshotPayload::read);

    public FieldFaceSnapshotPayload {
        Objects.requireNonNull(dimension); faces = List.copyOf(faces);
        if (faces.size() > MAX_FACES) throw new IllegalArgumentException("too many field faces");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf b, FieldFaceSnapshotPayload p) {
        b.writeResourceLocation(p.dimension); b.writeVarInt(p.faces.size());
        for (Face f : p.faces) { b.writeUtf(f.key, 128); b.writeEnum(f.axis); b.writeInt(f.plane); b.writeInt(f.minU); b.writeInt(f.maxU); b.writeInt(f.minV); b.writeInt(f.maxV); }
    }
    private static FieldFaceSnapshotPayload read(RegistryFriendlyByteBuf b) {
        ResourceLocation dimension=b.readResourceLocation(); int n=b.readVarInt();
        if(n<0||n>MAX_FACES) throw new IllegalArgumentException("invalid field face count");
        List<Face> faces=new ArrayList<>(n);
        for(int i=0;i<n;i++) faces.add(new Face(b.readUtf(128),b.readEnum(Direction.Axis.class),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt()));
        if(b.isReadable()) throw new IllegalArgumentException("trailing field face bytes");
        return new FieldFaceSnapshotPayload(dimension,faces);
    }
    public record Face(String key, Direction.Axis axis, int plane, int minU, int maxU, int minV, int maxV) {
        public Face { Objects.requireNonNull(key); Objects.requireNonNull(axis); if(key.length()>128||minU>=maxU||minV>=maxV) throw new IllegalArgumentException("invalid field face"); }
        public static Face from(FieldContainmentRuntime.Face f) { return new Face(f.key(),f.axis(),f.plane(),f.minU(),f.maxU(),f.minV(),f.maxV()); }
    }
}

