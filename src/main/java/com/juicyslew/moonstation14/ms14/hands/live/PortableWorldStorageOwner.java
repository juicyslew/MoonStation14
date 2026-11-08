package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource;
import com.juicyslew.moonstation14.ms14.atmos.exposure.WorldCellExposureAir;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.item.ModItems;

import java.util.Optional;
import java.util.UUID;

/** Derived containment owner for one direct portable key entry in a loaded world bag/belt. */
public final class PortableWorldStorageOwner implements ContainedBodyOwner {
    private final ServerLevel level;
    private final UUID rootId;
    private final String childToken;
    private final PortableBodyReference reference;

    public PortableWorldStorageOwner(ServerLevel level, UUID rootId, String childToken,
                                     PortableBodyReference reference) {
        this.level = java.util.Objects.requireNonNull(level);
        this.rootId = java.util.Objects.requireNonNull(rootId);
        this.childToken = java.util.Objects.requireNonNull(childToken);
        this.reference = java.util.Objects.requireNonNull(reference);
        if (childToken.isBlank() || childToken.length() > com.juicyslew.moonstation14.ms14.hands.ItemToken.MAX_LENGTH)
            throw new IllegalArgumentException("invalid world-grid child token");
    }

    public UUID rootId() { return rootId; }
    public String childToken() { return childToken; }
    public PortableBodyReference reference() { return reference; }
    public ItemEntity root() { return level.getEntity(rootId) instanceof ItemEntity item ? item : null; }

    private boolean exactRootEntry() {
        ItemEntity root = root();
        if (root == null || !(root.getItem().is(ModItems.BAG) || root.getItem().is(ModItems.BELT))
                || root.isRemoved() || !root.isAddedToLevel() || root.level() != level
                || level.getEntity(rootId) != root) return false;
        return PortableBodyCarrying.exactWorldEntry(root.getItem(), childToken, reference);
    }

    @Override public UUID identity() { return reference.token(); }
    @Override public ServerLevel level() { return level; }
    @Override public Vec3 position() { return root() == null ? Vec3.ZERO : root().position(); }
    @Override public Stage stage() { return Stage.STATIONARY; }
    @Override public boolean isValid() {
        return exactRootEntry() && reference.dimension().equals(level.dimension().location())
                && level.getEntity(reference.body()) instanceof LivingEntity body
                && !body.getUUID().equals(rootId)
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(body, level);
    }
    @Override public Optional<ExposureAirSource> airSource() {
        ItemEntity root = root();
        if (root == null || !root.isAddedToLevel() || level.getEntity(rootId) != root
                || !level.hasChunkAt(root.blockPosition())) return Optional.empty();
        return Optional.of(new WorldCellExposureAir(level, BlockPos.containing(root.position().add(0, .5, 0))));
    }
    @Override public boolean tryEscape(LivingEntity body) { return tryEscape(body, 0L); }
    @Override public boolean tryEscape(LivingEntity body, long originSequence) {
        return PortableBodyEscapeCustody.tryEscape(this, body, originSequence);
    }
}
