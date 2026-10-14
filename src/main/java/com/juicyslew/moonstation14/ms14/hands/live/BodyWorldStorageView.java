package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Point-in-time, read-only world storage observation. This is not a transfer capability or a refresh permission. */
public final class BodyWorldStorageView {
    private BodyWorldStorageView() { }

    public record Summary(UUID worldEntityId, ResourceLocation itemId, String kind, int width, int height,
                          List<GridContents.Entry> entries, long sessionEpoch, long handRevision) {
        public Summary {
            // GridContents.Entry copies its stack on construction and on access; the list is detached.
            entries = List.copyOf(entries.stream().map(e -> new GridContents.Entry(
                    e.token(), e.stack(), e.x(), e.y(), e.rotation())).toList());
        }
    }

    /** A nominated UUID never grants open authority: it must be the body's aimed first hit right now. */
    public static Optional<Summary> inspect(ServerPlayer actor, UUID target, long epoch, long expectedHandRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || authority.orElseThrow().epoch() != epoch) return Optional.empty();
        var lease = authority.orElseThrow();
        return inspectOwned(lease.body(), target, epoch, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, lease));
    }

    // Spawned-body fixture seam. Caller supplies an independent server validity predicate; never exposed to clients.
    static Optional<Summary> inspectOwned(LivingEntity body, UUID target, long epoch, long expectedHandRevision,
                                          BooleanSupplier authorityValid) {
        if (body == null || target == null || epoch <= 0 || expectedHandRevision < 0 || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !authorityValid.getAsBoolean()) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != expectedHandRevision || !hands.compatible(body)) return Optional.empty();
        Entity entity = level.getEntity(target);
        if (!(entity instanceof ItemEntity item) || item.level() != level || item.isRemoved()
                || !item.isAddedToLevel() || level.getEntity(target) != item || !target.equals(item.getUUID()))
            return Optional.empty();
        Vec3 position = item.position();
        ItemStack original = item.getItem();
        ItemStack source = original.copy();
        Optional<GridContents> contents = GridContents.read(source);
        if (contents.isEmpty() || BodyItemTargetProbe.probe(level, body, item, BodyItemTargetProbe.MAX_REACH) == null)
            return Optional.empty();
        // Shape callbacks are not assumed pure. Reject changes to either live owner after the probe.
        if (!authorityValid.getAsBoolean() || body.level() != level || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != expectedHandRevision
                || item.level() != level || item.isRemoved() || !item.isAddedToLevel()
                || level.getEntity(target) != item || !position.equals(item.position())
                || item.getTarget() != null || !ItemStack.matches(source, item.getItem())) return Optional.empty();
        boolean bag = source.is(com.juicyslew.moonstation14.item.ModItems.BAG);
        return Optional.of(new Summary(target, BuiltInRegistries.ITEM.getKey(source.getItem()),
                bag ? "bag" : "belt", bag ? 7 : 8, bag ? 4 : 2,
                contents.orElseThrow().entries(), epoch, expectedHandRevision));
    }
}

