package com.juicyslew.moonstation14.ms14.power.cable.network;

import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Server-authoritative replacement snapshot for one watched chunk's sparse cable faces. */
public record CableVisualPayload(ResourceLocation dimension, int chunkX, int chunkZ, long revision,
                                 boolean resetSnapshot, List<CableChunkData.Record> records)
        implements CustomPacketPayload {
    public static final Type<CableVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "cable_visual"));
    public static final int MAX_RECORDS = CableChunkData.MAX_RECORDS;
    public static final StreamCodec<RegistryFriendlyByteBuf, CableVisualPayload> STREAM_CODEC =
            StreamCodec.of(CableVisualPayload::write, CableVisualPayload::read);

    public CableVisualPayload {
        Objects.requireNonNull(dimension, "dimension");
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        Objects.requireNonNull(records, "records");
        if (records.size() > MAX_RECORDS) throw new IllegalArgumentException("too many cable records");
        Set<Key> keys = new HashSet<>();
        for (CableChunkData.Record record : records) {
            Objects.requireNonNull(record, "record");
            if (record.x() < 0 || record.x() > 15 || record.z() < 0 || record.z() > 15
                    || record.face() == null || record.tier() == null)
                throw new IllegalArgumentException("invalid cable record");
            if (!keys.add(new Key(record.x(), record.y(), record.z(), record.face(), record.tier())))
                throw new IllegalArgumentException("duplicate cable node");
        }
        records = List.copyOf(records);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Conservative wire-work estimate used by the server's aggregate tick budget. */
    public int estimatedEncodedBytes() {
        return 32 + dimension.toString().length() + records.size() * 8;
    }

    private static void write(RegistryFriendlyByteBuf buf, CableVisualPayload value) {
        buf.writeResourceLocation(value.dimension);
        buf.writeInt(value.chunkX);
        buf.writeInt(value.chunkZ);
        buf.writeVarLong(value.revision);
        buf.writeBoolean(value.resetSnapshot);
        buf.writeVarInt(value.records.size());
        for (CableChunkData.Record record : value.records) {
            buf.writeByte(record.x()); buf.writeInt(record.y()); buf.writeByte(record.z());
            buf.writeByte(record.face().ordinal()); buf.writeByte(record.tier().ordinal());
        }
    }

    private static CableVisualPayload read(RegistryFriendlyByteBuf buf) {
        try {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt(), chunkZ = buf.readInt();
            long revision = buf.readVarLong();
            boolean reset = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException("invalid cable count: " + count);
            List<CableChunkData.Record> records = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = buf.readUnsignedByte(), y = buf.readInt(), z = buf.readUnsignedByte();
                int face = buf.readUnsignedByte(), tier = buf.readUnsignedByte();
                if (face >= Direction.values().length || tier >= CableTier.values().length)
                    throw new IllegalArgumentException("invalid cable face or tier ordinal");
                records.add(new CableChunkData.Record(x, y, z, Direction.values()[face], CableTier.values()[tier]));
            }
            if (buf.isReadable()) throw new IllegalArgumentException("trailing bytes in cable visual payload");
            return new CableVisualPayload(dimension, chunkX, chunkZ, revision, reset, records);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed cable visual payload", exception);
        }
    }

    private record Key(int x, int y, int z, Direction face, CableTier tier) { }
}
