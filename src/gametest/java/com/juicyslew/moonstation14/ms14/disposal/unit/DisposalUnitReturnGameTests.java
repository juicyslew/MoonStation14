package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisposalUnitReturnGameTests {
    private DisposalUnitReturnGameTests() { }

    @GameTest(batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void holderReturnsItemsAndExactBodyToUnitThenEjects(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(4, 2, 4), unitPos = helper.absolutePos(local), trunkPos = unitPos.below();
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        unit.setPowerSwitch(false);
         level.setBlock(trunkPos, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
         var record = new DisposalTubeRecord(UUID.randomUUID(), trunkPos, ModDisposals.id("disposal_trunk").toString(), Direction.DOWN, true, "", "", 0);
         require(DisposalTubeWorldData.forMutation(level).add(level, record, p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "return trunk registered on real block");

        var sourceOwner = new TestOwner(level);
        LivingEntity body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        boolean wasInvisible = body.isInvisible(), wasNoPhysics = body.noPhysics, wasNoGravity = body.isNoGravity();
        var containment = BodyContainmentService.forLevel(level);
        require(containment.bind(body, sourceOwner), "source body ownership bound");
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(level);
        DisposalContents source = new DisposalContents(sourceOwner.identity(), ledger);
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        ItemLocation.WorldEntity external = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, external).accepted(), "item token registered");
        require(source.accept(token, external, "whole-stack", new ItemStack(Items.BREAD, 3), ledger.snapshot().revision()), "source payload owned");

        var holder = DisposalTransitHolder.spawnFlushed(level, trunkPos, new GasMixture(Map.of(), 293.15), source,
                source.snapshot().keySet(), java.util.List.of(body), sourceOwner).orElseThrow();
        BlockPos wrongPos = helper.absolutePos(new BlockPos(7, 2, 4));
        level.setBlock(wrongPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var wrongUnit = (DisposalUnitBlockEntity) level.getBlockEntity(wrongPos);
        wrongUnit.setPowerSwitch(false);
        require(!wrongUnit.receiveReturnedHolder(holder), "wrong-cell return refused");
        require(holder.contents().snapshot().size() == 1 && holder.bodyIds().contains(body.getUUID()), "rejected return leaves holder ownership intact");
        require(unit.receiveReturnedHolder(holder), "exact same-floor holder accepted");
        require(holder.contents().snapshot().isEmpty() && holder.bodyIds().isEmpty(), "holder relinquishes only after return acceptance");
        require(unit.contents().snapshot().isEmpty() && unit.bodyIds().isEmpty(), "unit immediately ejects accepted payloads");
         var ejected = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                 new net.minecraft.world.phys.AABB(unitPos).inflate(2)).stream().filter(item -> item.getItem().is(Items.BREAD)
                         && item.getItem().getCount() == 3).toList();
         require(ejected.size() == 1, "whole stack is staged exactly once after retrying bounded candidates; unit entries="
                 + unit.contents().snapshot().size() + ", item drops=" + ejected.size());
        require(!body.isRemoved() && body.isInvisible() == wasInvisible && body.noPhysics == wasNoPhysics
                && body.isNoGravity() == wasNoGravity, "body identity and original flags survive transfer/ejection");
        helper.succeed();
    }

    @GameTest(batch = "disposals", template = "disposal_clear", timeoutTicks = 30)
    public static void blockedReturnExitRetainsValidUnitOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(4, 2, 4), unitPos = helper.absolutePos(local), trunkPos = unitPos.below();
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        unit.setPowerSwitch(false);
         level.setBlock(trunkPos, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
         require(DisposalTubeWorldData.forMutation(level).add(level,
                 new DisposalTubeRecord(UUID.randomUUID(), trunkPos, ModDisposals.id("disposal_trunk").toString(), Direction.DOWN, true, "", "", 0), p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "return trunk registered on real block");
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 2; dy++) {
            BlockPos p = unitPos.offset(dx, dy, dz);
             if (!p.equals(unitPos) && !p.equals(trunkPos)) level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        }
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(level);
        UUID ownerId = UUID.randomUUID(); DisposalContents source = new DisposalContents(ownerId, ledger);
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        ItemLocation.WorldEntity external = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, external).accepted(), "item token registered");
        require(source.accept(token, external, "blocked-stack", new ItemStack(Items.BREAD), ledger.snapshot().revision()), "source stack owned");
        var holder = DisposalTransitHolder.spawnFlushed(level, trunkPos, new GasMixture(Map.of(), 293.15), source,
                source.snapshot().keySet(), java.util.List.of(), new TestOwner(level, ownerId)).orElseThrow();
        require(unit.receiveReturnedHolder(holder), "unit accepts return even when spatial ejection unavailable");
        require(unit.isValid() && unit.contents().snapshot().size() == 1 && holder.contents().snapshot().isEmpty(),
                "blocked ejection leaves payload with a valid unit owner, not the holder or nowhere");
        helper.succeed();
    }

    @GameTest(batch = "disposals", template = "disposal_clear", timeoutTicks = 30)
    public static void disconnectedReturnTrunkStillAllowsTerminalUnitReturn(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 2, 4)), trunkPos = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        unit.setPowerSwitch(false);
        var data = DisposalTubeWorldData.forMutation(level);
         level.setBlock(trunkPos, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
         var trunk = new DisposalTubeRecord(UUID.randomUUID(), trunkPos, ModDisposals.id("disposal_trunk").toString(), Direction.DOWN, true, "", "", 0);
         require(data.add(level, trunk, p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "return trunk registered on real block");
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(level);
        UUID sourceId = UUID.randomUUID(); DisposalContents source = new DisposalContents(sourceId, ledger);
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        ItemLocation.WorldEntity external = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, external).accepted(), "item token registered");
        require(source.accept(token, external, "disconnected-stack", new ItemStack(Items.BREAD, 2), ledger.snapshot().revision()), "holder owns stack");
        LivingEntity body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        var owner = new TestOwner(level, sourceId);
        var containment = BodyContainmentService.forLevel(level);
        require(containment.bind(body, owner), "body bound to holder owner");
        var holder = DisposalTransitHolder.spawnFlushed(level, trunkPos, new GasMixture(Map.of(), 293.15), source,
                source.snapshot().keySet(), java.util.List.of(body), owner).orElseThrow();
        level.setBlock(unitPos, level.getBlockState(unitPos).setValue(DisposalUnitBlock.ANCHORED, false), 3);
        require(!unit.receiveReturnedHolder(holder), "unanchored unit refuses return");
        require(holder.bodyIds().contains(body.getUUID()) && holder.contents().snapshot().size() == 1,
                "unanchored-unit refusal preserves holder ownership");
        level.setBlock(unitPos, level.getBlockState(unitPos).setValue(DisposalUnitBlock.ANCHORED, true), 3);
         require(data.anchor(level, trunk, false, p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "exit trunk unanchored while holder active");
        var routing = new com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting(level);
        require(routing.deferToUnitReturn(trunkPos), "live anchored unit remains an eligible terminal return");
        require(routing.tryReturnToUnit(holder, trunkPos), "unit accepts exact holder at below cell after exit disconnect");
        require(holder.bodyIds().isEmpty() && holder.contents().snapshot().isEmpty(), "holder relinquishes accepted payload");
        require(!unit.bodyIds().contains(body.getUUID()) && containment.ownerOf(body).isEmpty(), "body was ejected exactly once");
        require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(unitPos).inflate(2))
                .stream().mapToInt(item -> item.getItem().getCount()).sum() == 2, "one whole item stack was ejected");
        helper.succeed();
    }

    private static final class TestOwner implements ContainedBodyOwner {
        private final net.minecraft.server.level.ServerLevel level;
        private final UUID id;
        TestOwner(net.minecraft.server.level.ServerLevel level) { this(level, UUID.randomUUID()); }
        TestOwner(net.minecraft.server.level.ServerLevel level, UUID id) { this.level = level; this.id = id; }
        @Override public UUID identity() { return id; }
        @Override public net.minecraft.server.level.ServerLevel level() { return level; }
        @Override public Vec3 position() { return new Vec3(2.5, 2, 2.5); }
        @Override public Stage stage() { return Stage.TRANSIT; }
        @Override public boolean isValid() { return true; }
        @Override public boolean tryEscape(LivingEntity body) { return false; }
    }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
