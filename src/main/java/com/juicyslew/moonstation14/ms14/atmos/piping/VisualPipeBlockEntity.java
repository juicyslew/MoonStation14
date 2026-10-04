package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Cosmetic layer state only; this entity has no gas storage or ticking. */
public class VisualPipeBlockEntity extends BlockEntity {
    public static final int PRIMARY = 1;
    public static final int SECONDARY = 2;
    public static final int TERTIARY = 4;
    private static final int VALID_MASK = PRIMARY | SECONDARY | TERTIARY;
    private static final String VERSION = "visual_pipe_version";
    private static final String MASK = "visual_pipe_mask";
    private static final int CURRENT_VERSION = 2;
    private static final String FUNCTIONAL_IDS = "pipe_functional_ids";
    private static final int FUNCTIONAL_IDS_VERSION = 1;
    private static final String FUNCTIONAL_VERSION = "version";
    private static final String FUNCTIONAL_ENTRIES = "lanes";
    private static final String FUNCTIONAL_LAYER = "layer";
    private static final String FUNCTIONAL_ID = "id";
    private int layerMask = PRIMARY;
    private int concealedJoinMask;
    private final Map<Integer, UUID> functionalIdentities = new HashMap<>();

    public VisualPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VISUAL_PIPE.get(), pos, state);
    }

    @Override public void onLoad() {
        super.onLoad();
        refreshConcealedJoins();
    }

    public static void refreshLoadedAdjacent(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (level.hasChunkAt(adjacent)
                    && level.getBlockEntity(adjacent) instanceof VisualPipeBlockEntity entity)
                entity.refreshConcealedJoins();
        }
    }

    /** Read-only complete layer bitmask for model/render consumers. */
    public int getLayerMask() { return layerMask; }
    public boolean hasLayer(int layer) { return validLayer(layer) && (layerMask & layer) != 0; }

    /** Inert functional identity only; this does not install gas or activate a network. */
    public Optional<UUID> functionalIdentity(int layer) {
        return Optional.ofNullable(functionalIdentities.get(layer));
    }

    Map<Integer, UUID> functionalIdentitiesSnapshot() { return Map.copyOf(functionalIdentities); }

    /** Removes only the identity observed by a guarded fresh-vacuum rollback. */
    boolean clearFunctionalLayer(int selectedLayer, UUID expectedIdentity) {
        if (!validLayer(selectedLayer) || expectedIdentity == null
                || !expectedIdentity.equals(functionalIdentities.get(selectedLayer))) return false;
        functionalIdentities.remove(selectedLayer);
        setChanged();
        refreshConcealedJoins();
        return true;
    }

    /** Restores an identity after a failed rollback CAS, only while its slot remains empty. */
    boolean restoreFunctionalLayer(int selectedLayer, UUID identity) {
        if (identity == null || !validLayer(selectedLayer) || functionalIdentities.containsKey(selectedLayer)
                || functionalIdentities.containsValue(identity)) return false;
        functionalIdentities.put(selectedLayer, identity);
        setChanged();
        refreshConcealedJoins();
        return true;
    }

    /** Server-only registration of a caller-generated identity for an existing cosmetic lane. */
    public boolean markFunctionalLayer(int selectedLayer, UUID freshIdentity) {
        if (freshIdentity == null || !validLayer(selectedLayer) || !hasLayer(selectedLayer)
                || level == null || level.isClientSide || !level.hasChunkAt(worldPosition)
                || !level.getBlockState(worldPosition).is(getBlockState().getBlock())
                || level.getBlockEntity(worldPosition) != this || functionalIdentities.containsKey(selectedLayer)
                || functionalIdentities.containsValue(freshIdentity)) return false;
        functionalIdentities.put(selectedLayer, freshIdentity);
        setChanged();
        refreshConcealedJoins();
        return true;
    }

    /** Renderer-facing arm query. It fails closed for unloaded or unlike neighbors. */
    public boolean hasLayerArm(int layer, Direction direction) {
        if (!validLayer(layer) || !hasLayer(layer) || level == null) return false;
        BlockPos neighbor = worldPosition.relative(direction);
        boolean visual = level.hasChunkAt(neighbor)
                && level.getBlockState(neighbor).is(getBlockState().getBlock())
                && level.getBlockEntity(neighbor) instanceof VisualPipeBlockEntity other && other.hasLayer(layer);
        if (visual) return true;
        if ((concealedJoinMask & concealedBit(layer, direction)) == 0 || !level.hasChunkAt(neighbor)) return false;
        return level.getBlockState(neighbor).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get());
    }

    private static int concealedBit(int layer, Direction direction) {
        int lane = layer == PRIMARY ? 0 : layer == SECONDARY ? 1 : 2;
        return 1 << (lane * 6 + direction.ordinal());
    }

    /** Server-derived visual confirmation of a functional gas lane; never persisted or treated as ownership. */
    public void refreshConcealedJoins() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !server.hasChunkAt(worldPosition)) return;
        int next = 0;
        for (int lane : new int[]{PRIMARY, SECONDARY, TERTIARY}) {
            if (!hasLayer(lane) || LoadedPipeGasOwner.activeSnapshot(server, worldPosition, lane).isEmpty()) continue;
            PipeTopology.Layer type = lane == PRIMARY ? PipeTopology.Layer.PRIMARY
                    : lane == SECONDARY ? PipeTopology.Layer.SECONDARY : PipeTopology.Layer.TERTIARY;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = worldPosition.relative(direction);
                if (!server.hasChunkAt(neighbor)) continue;
                var sealed = LoadedSealedWallSegment.matchingLive(server, neighbor, type).orElse(null);
                if (sealed == null) continue;
                if (LoadedSealedWallGasOwner.activeSnapshot(server, neighbor, type).isEmpty()) continue;
                int outward = 1 << direction.getOpposite().ordinal();
                if ((sealed.ports() & outward) != 0) next |= concealedBit(lane, direction);
            }
        }
        if (next != concealedJoinMask) {
            concealedJoinMask = next;
            setChanged();
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        BlockState state = server.getBlockState(worldPosition);
        if (state.hasProperty(ExposedPipeVisualBlock.PRIMARY_CONNECTED)) {
            BlockState updated = state;
            for (Direction direction : Direction.values()) {
                boolean sameBlock = server.hasChunkAt(worldPosition.relative(direction))
                        && server.getBlockState(worldPosition.relative(direction)).is(state.getBlock())
                        && server.getBlockEntity(worldPosition.relative(direction)) instanceof VisualPipeBlockEntity other
                        && other.hasLayer(PRIMARY);
                boolean connected = hasLayer(PRIMARY)
                        && (sameBlock || (next & concealedBit(PRIMARY, direction)) != 0);
                updated = updated.setValue(ExposedPipeVisualBlock.property(direction), connected);
            }
            if (!updated.equals(state)) server.setBlock(worldPosition, updated, 3);
        }
    }

    /** Only SECONDARY or TERTIARY may be added. Mutations are authoritative on the server. */
    public boolean addLayer(int layer) {
        return validLayer(layer) && setMask(layerMask | layer);
    }

    /** Adds a functional lane to a live pipe without disturbing existing lane identities. */
    public boolean addFunctionalPlacementLayer(int layer) {
        return validLayer(layer) && !hasLayer(layer) && setMask(layerMask | layer);
    }

    boolean rollbackAddedFunctionalLayer(int layer, int expectedMask, Map<Integer, UUID> expectedIdentities) {
        if (!validLayer(layer) || layerMask != expectedMask || (expectedMask & layer) == 0
                || functionalIdentities.containsKey(layer) || !functionalIdentities.equals(expectedIdentities)) return false;
        return setMask(expectedMask & ~layer);
    }

    /** Set the sole lane for a newly placed cosmetic pipe. */
    public boolean setLayersForPlacement(int chosenSingleLayer) {
        if (!validLayer(chosenSingleLayer)) return false;
        if (chosenSingleLayer == layerMask && functionalIdentities.isEmpty()
                && level != null && !level.isClientSide && level.hasChunkAt(worldPosition)
                && level.getBlockState(worldPosition).is(getBlockState().getBlock())
                && level.getBlockEntity(worldPosition) == this) return true;
        return setMask(chosenSingleLayer);
    }

    /** Server-side mask mutation for tests and cosmetic placement. */
    public boolean setLayersForTest(int nonzeroMask) {
        if (nonzeroMask == 0 || (nonzeroMask & ~VALID_MASK) != 0) return false;
        return setMask(nonzeroMask);
    }

    private boolean setMask(int mask) {
        if (level == null || level.isClientSide || !level.hasChunkAt(worldPosition)
                || !level.getBlockState(worldPosition).is(getBlockState().getBlock())
                || level.getBlockEntity(worldPosition) != this || mask == layerMask) return false;
        for (int layer : functionalIdentities.keySet()) {
            if ((mask & layer) == 0) return false;
        }
        layerMask = mask;
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(ExposedPipeVisualBlock.PRIMARY_CONNECTED)) {
            BlockState updated = state.setValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED, hasLayer(PRIMARY));
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = worldPosition.relative(direction);
                boolean connected = hasLayer(PRIMARY) && level.hasChunkAt(neighbor)
                        && level.getBlockState(neighbor).is(state.getBlock())
                        && level.getBlockEntity(neighbor) instanceof VisualPipeBlockEntity other && other.hasLayer(PRIMARY);
                updated = updated.setValue(ExposedPipeVisualBlock.property(direction), connected);
            }
            level.setBlock(worldPosition, updated, 3);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, state, level.getBlockState(worldPosition), 3);
        notifyLoadedPipeNeighbors();
        refreshConcealedJoins();
        return true;
    }

    private void notifyLoadedPipeNeighbors() {
        if (level == null || level.isClientSide) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (level.hasChunkAt(neighbor) && level.getBlockState(neighbor).is(getBlockState().getBlock())
                    && level.getBlockEntity(neighbor) instanceof VisualPipeBlockEntity other) {
                other.setChanged();
                other.refreshConcealedJoins();
                level.sendBlockUpdated(neighbor, other.getBlockState(), other.getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(VERSION, CURRENT_VERSION);
        tag.putInt(MASK, layerMask & VALID_MASK);
        if (!functionalIdentities.isEmpty()) {
            CompoundTag ids = new CompoundTag();
            ids.putInt(FUNCTIONAL_VERSION, FUNCTIONAL_IDS_VERSION);
            ListTag entries = new ListTag();
            functionalIdentities.forEach((layer, id) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt(FUNCTIONAL_LAYER, layer);
                entry.putString(FUNCTIONAL_ID, id.toString());
                entries.add(entry);
            });
            ids.put(FUNCTIONAL_ENTRIES, entries);
            tag.put(FUNCTIONAL_IDS, ids);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        functionalIdentities.clear();
        int loadedJoins = tag.contains("concealed_join_mask", Tag.TAG_INT) ? tag.getInt("concealed_join_mask") : 0;
        concealedJoinMask = (loadedJoins & ~((1 << 18) - 1)) == 0 ? loadedJoins : 0;
        boolean validVisualData = tag.contains(VERSION, Tag.TAG_INT) && tag.contains(MASK, Tag.TAG_INT);
        if (!validVisualData) {
            layerMask = PRIMARY;
        } else {
            int version = tag.getInt(VERSION);
            int loaded = tag.getInt(MASK);
            boolean validMask = loaded != 0 && (loaded & ~VALID_MASK) == 0;
            validVisualData = validMask && (version == 1 ? (loaded & PRIMARY) != 0 : version == CURRENT_VERSION);
        layerMask = validVisualData ? loaded : PRIMARY;
        }
        if (validVisualData) loadFunctionalIdentities(tag);
    }

    private void loadFunctionalIdentities(CompoundTag tag) {
        if (!tag.contains(FUNCTIONAL_IDS, Tag.TAG_COMPOUND)) return;
        try {
            CompoundTag ids = tag.getCompound(FUNCTIONAL_IDS);
            if (!ids.contains(FUNCTIONAL_VERSION, Tag.TAG_INT)
                    || ids.getInt(FUNCTIONAL_VERSION) != FUNCTIONAL_IDS_VERSION
                    || !ids.contains(FUNCTIONAL_ENTRIES, Tag.TAG_LIST)) throw new IllegalArgumentException();
            ListTag entries = ids.getList(FUNCTIONAL_ENTRIES, Tag.TAG_COMPOUND);
            if (entries.size() == 0 || entries.size() > 3) throw new IllegalArgumentException();
            Map<Integer, UUID> loaded = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                int layer = entry.getInt(FUNCTIONAL_LAYER);
                if (!entry.contains(FUNCTIONAL_LAYER, Tag.TAG_INT) || !validLayer(layer) || (layerMask & layer) == 0
                        || !entry.contains(FUNCTIONAL_ID, Tag.TAG_STRING) || loaded.containsKey(layer))
                    throw new IllegalArgumentException();
                UUID id = UUID.fromString(entry.getString(FUNCTIONAL_ID));
                if (loaded.containsValue(id)) throw new IllegalArgumentException();
                loaded.put(layer, id);
            }
            functionalIdentities.putAll(loaded);
        } catch (RuntimeException malformed) {
            functionalIdentities.clear();
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove(FUNCTIONAL_IDS);
        tag.putInt(VERSION, CURRENT_VERSION);
        tag.putInt(MASK, layerMask & VALID_MASK);
        tag.putInt("concealed_join_mask", concealedJoinMask);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
        if (level != null && level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static boolean validLayer(int layer) {
        return layer == PRIMARY || layer == SECONDARY || layer == TERTIARY;
    }
}
