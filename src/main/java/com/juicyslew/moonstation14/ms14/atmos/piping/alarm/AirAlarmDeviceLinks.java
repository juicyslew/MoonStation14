package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Explicit bounded SS14 DeviceList allow-list adaptation; no device-network discovery or packets. */
public final class AirAlarmDeviceLinks {
    private static final String KEY = "AirAlarmDeviceLinks";
    private static final int HARD_LIMIT = 32;
    private final List<Link> links = new ArrayList<>();
    private boolean valid = true;

    public record Link(ResourceKey<Level> dimension, BlockPos pos, VentilationDeviceConfig.Kind kind, UUID deviceId) {
        public Link {
            if (dimension == null || pos == null || kind == null || deviceId == null)
                throw new IllegalArgumentException("incomplete device identity");
            pos = pos.immutable();
        }
    }

    public List<Link> snapshot() { return List.copyOf(links); }
    public boolean valid() { return valid; }

    /** Generic routing view over this canonical persisted membership; link identity/position/kind remain stored once. */
    public DeviceList deviceList(DeviceList.Config config) {
        if (config == null) return null;
        if (!valid || config.capacity() < links.size())
            return new DeviceList(List.of(), config.capacity(), config.allowList(), config.filterIncoming());
        return new DeviceList(links.stream().map(link -> link.deviceId().toString()).toList(),
                config.capacity(), config.allowList(), config.filterIncoming());
    }

    /** Removal uses the persisted identity, including offline devices; no chunk is loaded. */
    public boolean remove(UUID deviceId) {
        if (!valid || deviceId == null || links.stream().filter(link -> link.deviceId().equals(deviceId)).count() != 1)
            return false;
        return links.removeIf(link -> link.deviceId().equals(deviceId));
    }

    /** Atomic owner-list replacement; each supplied identity is revalidated as a live endpoint. */
    public boolean replace(ServerLevel level, List<VentilationDeviceBlockEntity> devices, int capacity) {
        if (!valid || level == null || devices == null || capacity < 0 || devices.size() > capacity) return false;
        List<Link> replacement = new ArrayList<>();
        for (var device : devices) {
            if (device == null || !addCandidate(level, device, replacement)) return false;
        }
        links.clear(); links.addAll(replacement); return true;
    }
    public boolean replaceLinks(List<Link> replacement, int capacity) {
        if(!valid||replacement==null||capacity<0||replacement.size()>capacity||replacement.stream().anyMatch(java.util.Objects::isNull)
                ||replacement.stream().map(Link::deviceId).distinct().count()!=replacement.size()
                ||replacement.stream().map(link->List.of(link.dimension(),link.pos())).distinct().count()!=replacement.size())return false;
        links.clear();links.addAll(replacement);return true;
    }
    private static boolean addCandidate(ServerLevel level, VentilationDeviceBlockEntity device, List<Link> target) {
        if (device.getLevel()!=level || device.isRemoved() || device.deviceId()==null) return false;
        BlockPos pos=device.getBlockPos(); LevelChunk chunk=loadedChunk(level,pos);
        if(chunk==null||chunk.getBlockEntity(pos)!=device||!(chunk.getBlockState(pos).getBlock() instanceof VentilationDeviceBlock block)||block.kind()!=device.kind())return false;
        Link link=new Link(level.dimension(),pos,block.kind(),device.deviceId());
        if(target.stream().anyMatch(existing->existing.deviceId().equals(link.deviceId())||existing.pos().equals(link.pos())))return false;
        target.add(link);return true;
    }

    /** Only the authoritative server can append an already-live, matching device. */
    public boolean add(ServerLevel level, VentilationDeviceBlockEntity device, int maxDevices) {
        if (!valid || level == null || device == null || !level.getServer().isSameThread()
                || maxDevices < 0 || maxDevices > HARD_LIMIT || links.size() >= maxDevices
                || device.getLevel() != level || device.isRemoved() || device.deviceId() == null) return false;
        BlockPos pos = device.getBlockPos();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || chunk.getBlockEntity(pos) != device
                || !(chunk.getBlockState(pos).getBlock() instanceof VentilationDeviceBlock block)
                || block.kind() != device.kind()) return false;
        Link link = new Link(level.dimension(), pos, block.kind(), device.deviceId());
        if (links.stream().anyMatch(existing -> existing.dimension().equals(link.dimension())
                && existing.pos().equals(link.pos()))) return false;
        links.add(link);
        return true;
    }

    /** Never requests a chunk or creates a dimension. Stale/replaced devices resolve to null. */
    public static VentilationDeviceBlockEntity resolve(ServerLevel origin, Link link) {
        if (origin == null || link == null || !origin.getServer().isSameThread()) return null;
        ServerLevel level = origin.getServer().getLevel(link.dimension());
        if (level == null) return null;
        LevelChunk chunk = loadedChunk(level, link.pos());
        if (chunk == null || !(chunk.getBlockState(link.pos()).getBlock() instanceof VentilationDeviceBlock block)
                || block.kind() != link.kind()
                || !(chunk.getBlockEntity(link.pos()) instanceof VentilationDeviceBlockEntity device)
                || device.isRemoved() || device.getLevel() != level || device.kind() != link.kind()
                || !link.deviceId().equals(device.deviceId())) return null;
        return device;
    }

    private static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL) ? chunk : null;
    }

    public void save(CompoundTag target) {
        if (!valid) {
            // Preserve malformed input as invalid rather than silently accepting a truncated association.
            target.putBoolean(KEY + "Invalid", true);
            return;
        }
        ListTag list = new ListTag();
        for (Link link : links) {
            CompoundTag item = new CompoundTag();
            item.putString("Dimension", link.dimension().location().toString());
            item.putInt("X", link.pos().getX());
            item.putInt("Y", link.pos().getY());
            item.putInt("Z", link.pos().getZ());
            item.putString("Kind", link.kind().name());
            item.putUUID("DeviceId", link.deviceId());
            list.add(item);
        }
        target.put(KEY, list);
    }

    public void load(CompoundTag source, int maxDevices) {
        links.clear();
        valid = false;
        if (maxDevices < 0 || maxDevices > HARD_LIMIT || source.getBoolean(KEY + "Invalid")
                || (source.contains(KEY) && !source.contains(KEY, Tag.TAG_LIST))) return;
        if (!source.contains(KEY)) { valid = true; return; } // pre-link legacy alarm
        ListTag list = source.getList(KEY, Tag.TAG_COMPOUND);
        if (!(source.get(KEY) instanceof ListTag raw) || (!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > maxDevices || list.size() > HARD_LIMIT) return;
        List<Link> parsed = new ArrayList<>();
        HashSet<List<Object>> positions = new HashSet<>();
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag item) || !item.contains("Dimension", Tag.TAG_STRING)
                    || !item.contains("X", Tag.TAG_INT) || !item.contains("Y", Tag.TAG_INT)
                    || !item.contains("Z", Tag.TAG_INT) || !item.contains("Kind", Tag.TAG_STRING)
                    || !item.hasUUID("DeviceId")) return;
            ResourceLocation id = ResourceLocation.tryParse(item.getString("Dimension"));
            VentilationDeviceConfig.Kind kind;
            try { kind = VentilationDeviceConfig.Kind.valueOf(item.getString("Kind")); }
            catch (IllegalArgumentException exception) { return; }
            if (id == null) return;
            Link link = new Link(ResourceKey.create(Registries.DIMENSION, id),
                    new BlockPos(item.getInt("X"), item.getInt("Y"), item.getInt("Z")), kind, item.getUUID("DeviceId"));
            if (!positions.add(List.of(link.dimension(), link.pos())) || parsed.contains(link)) return;
            parsed.add(link);
        }
        links.addAll(parsed);
        valid = true;
    }
}
