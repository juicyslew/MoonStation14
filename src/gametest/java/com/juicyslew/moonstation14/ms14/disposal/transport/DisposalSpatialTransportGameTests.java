package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitLedger;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real owner blocks and natural entity ticks through a vertical disposal route. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisposalSpatialTransportGameTests {
    private DisposalSpatialTransportGameTests() { }

    @GameTest(template = "disposal_clear", batch = "disposals", timeoutTicks = 30)
    public static void verticalBendCarriesBodyWholeItemAndZeroAir(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos vertical = start.above(), bend = vertical.above(), outlet = bend.east();
        var world = DisposalTubeWorldData.forMutation(level);
        var verticalFrame = new DisposalOrientation(Direction.UP, Direction.NORTH);
        var bendFrame = new DisposalOrientation(Direction.DOWN, Direction.SOUTH);
        var outletFrame = new DisposalOrientation(Direction.EAST, Direction.UP);
        install(world, level, start, "disposal_pipe", verticalFrame);
        install(world, level, vertical, "disposal_pipe", verticalFrame);
        install(world, level, bend, "disposal_bend", bendFrame);
        install(world, level, outlet, "disposal_pipe", outletFrame);

        var routing = new LoadedWorldDisposalRouting(level);
        require(routing.reciprocal(routing.record(start), routing.record(vertical), Direction.UP), "real vertical port reciprocity");
        require(routing.reciprocal(routing.record(vertical), routing.record(bend), Direction.UP), "vertical bend entrance reciprocity");
        require(routing.reciprocal(routing.record(bend), routing.record(outlet), Direction.EAST), "bend outlet reciprocity");

        var ledger = DisposalUnitLedger.forLevel(level);
        UUID ownerId = UUID.randomUUID();
        var contents = new DisposalContents(ownerId, ledger);
        var token = new ItemToken(UUID.randomUUID().toString());
        var source = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, source).accepted(), "item token registered");
        ItemStack stack = new ItemStack(Items.BREAD, 3);
        require(contents.accept(token, source, "spatial-stack", stack, ledger.snapshot().revision()), "whole stack owned before travel");

        LivingEntity body = EntityType.ZOMBIE.create(level);
        require(body != null && level.addFreshEntity(body), "real body added");
        var sourceOwner = new SourceOwner(level, ownerId);
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        require(containment.bind(body, sourceOwner), "body exact owner established");
        var holder = DisposalTransitHolder.spawnFlushed(level, start,
                new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15), contents,
                List.copyOf(contents.snapshot().keySet()), List.of(body), sourceOwner).orElseThrow();
        require(holder.finiteAir().orElseThrow().sample().orElseThrow().mixture().totalMoles() == 0, "known zero-mole carrier air");

        awaitVertical(helper, holder, vertical, bend, outlet, level, body, token);
    }

    @GameTest(template = "disposal_clear", batch = "disposals", timeoutTicks = 20)
    public static void restoredHolderWithChangedTubeIdentitySafelyReleasesOwnedPayload(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos tube = helper.absolutePos(new BlockPos(3, 1, 3));
        var frame = new DisposalOrientation(Direction.EAST, Direction.UP);
        install(DisposalTubeWorldData.forMutation(level), level, tube, "disposal_pipe", frame);
        var ledger = DisposalUnitLedger.forLevel(level);
        UUID storeId = UUID.randomUUID();
        var sourceContents = new DisposalContents(storeId, ledger);
        var token = new ItemToken(UUID.randomUUID().toString());
        var sourceLocation = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, sourceLocation).accepted(), "reload item token registered");
        require(sourceContents.accept(token, sourceLocation, "reload-item", new ItemStack(Items.BREAD, 2),
                ledger.snapshot().revision()), "whole payload owned before reload simulation");

        LivingEntity body = EntityType.ZOMBIE.create(level);
        require(body != null, "reload body created");
        body.setInvisible(true); body.noPhysics = true; body.setNoGravity(true);
        require(level.addFreshEntity(body), "reload body added");
        var sourceOwner = new SourceOwner(level, storeId);
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        require(containment.bind(body, sourceOwner), "reload body starts with exact source owner");
        var holder = DisposalTransitHolder.spawnFlushed(level, tube,
                new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15), sourceContents,
                List.copyOf(sourceContents.snapshot().keySet()), List.of(body), sourceOwner).orElseThrow();
        var saved = new net.minecraft.nbt.CompoundTag();
        holder.addAdditionalSaveData(saved);
        saved.putUUID("TubeId", UUID.randomUUID()); // Loaded record at this cell is a different incarnation.
        holder.readAdditionalSaveData(saved);

        helper.runAfterDelay(1, () -> {
            require(holder.isRemoved(), "loaded identity mismatch exits rather than freezing on a missing record");
            require(level.getEntity(body.getUUID()) == body, "same body remains loaded");
            require(containment.ownerOf(body).isEmpty(), "body safely returned to world ownership");
            require(body.isInvisible() && body.noPhysics && body.isNoGravity(), "all original body flags restored exactly");
            require(sourceContents.snapshot().isEmpty(), "old holder relinquished its exact item payload");
            require(ledger.snapshot().locations().get(token) instanceof ItemLocation.WorldEntity,
                    "single item ownership transitions to world");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(tube).inflate(3));
            require(drops.size() == 1 && drops.getFirst().getItem().getCount() == 2, "one whole stack staged without duplication");
            helper.succeed();
        });
    }

    private static void install(DisposalTubeWorldData world, net.minecraft.server.level.ServerLevel level,
                                BlockPos pos, String prototype, DisposalOrientation frame) {
        var record = new DisposalTubeRecord(UUID.randomUUID(), pos, prototype, frame, true, "", "", 0,
                DisposalTubeRecord.HostKind.EXPOSED, "", null);
        require(world.installExposed(level, record), "real exposed tube owner block installed at " + pos);
    }

    private static void awaitVertical(GameTestHelper helper, DisposalTransitHolder holder, BlockPos vertical,
                                      BlockPos bend, BlockPos outlet, net.minecraft.server.level.ServerLevel level,
                                      LivingEntity body, ItemToken token) {
        helper.runAfterDelay(2, () -> {
            require(holder.currentPos().equals(vertical), "holder selected vertical cell after natural first interval");
            helper.runAfterDelay(2, () -> {
                require(holder.currentPos().equals(bend), "holder reached vertical bend in two ticks");
                require(atBlockCenter(holder, vertical), "holder arrived at vertical segment block center");
                require(Math.abs(body.getEyeY() - holder.position().y) < .15, "contained camera eye tracks vertical bore center");
                helper.runAfterDelay(2, () -> {
                    require(holder.currentPos().equals(outlet), "holder naturally traverses bend to outlet");
                    require(atBlockCenter(holder, bend), "holder arrived at bend block center");
                    require(Math.abs(body.getEyeY() - holder.position().y) < .15, "contained camera eye tracks bend bore center");
                    helper.runAfterDelay(5, () -> {
                        require(holder.isRemoved(), "open outlet released holder");
                        require(level.getEntity(body.getUUID()) == body, "same body entity retained");
                        require(com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(body).isEmpty(), "body returned to world ownership");
                        require(DisposalUnitLedger.forLevel(level).snapshot().locations().get(token) instanceof ItemLocation.WorldEntity,
                                "whole item ledger ownership became one world item");
                        var drops = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(outlet).inflate(3));
                        require(drops.size() == 1 && drops.getFirst().getItem().getCount() == 3, "one exact whole stack ejected");
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static boolean atBlockCenter(DisposalTransitHolder holder, BlockPos cell) {
        Vec3 p = holder.position();
        return Math.abs(p.x - (cell.getX() + .5)) < 1e-6
                && Math.abs(p.y - (cell.getY() + .5)) < 1e-6
                && Math.abs(p.z - (cell.getZ() + .5)) < 1e-6;
    }

    private record SourceOwner(net.minecraft.server.level.ServerLevel level, UUID identity)
            implements com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner {
        @Override public Vec3 position() { return Vec3.ZERO; }
        @Override public Stage stage() { return Stage.TRANSIT; }
        @Override public boolean isValid() { return true; }
        @Override public boolean tryEscape(LivingEntity body) { return false; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
