package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Body/state transition coverage; deliberately does not claim connected-player request authority. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyGridFreshSnapshotGameTests {
    private BodyGridFreshSnapshotGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void freshlyEquippedHostsReadAsEmptyWithoutMutatingStacks(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands.WholeResult leftPut = initial.putWhole(initial.revision(), "left", new ItemToken("bag"),
                    new ItemStack(ModItems.BAG.get()));
            require(leftPut.succeeded(), "put bag in left hand");
            LiveHands withBag = leftPut.state();
            require(withBag.revision() == initial.revision() + 1
                    && withBag.stackCopy("left").orElseThrow().is(ModItems.BAG), "first put advances revision and publishes bag");
            LiveHands.WholeResult rightPut = withBag.putWhole(withBag.revision(), "right", new ItemToken("belt"),
                    new ItemStack(ModItems.BELT.get()));
            require(rightPut.succeeded(), "put belt in right hand");
            LiveHands carrying = rightPut.state();
            require(carrying.revision() == withBag.revision() + 1
                    && carrying.stackCopy("right").orElseThrow().is(ModItems.BELT), "second put advances revision and publishes belt");
            require(carrying.revision() == initial.revision() + 2, "both puts advance exactly two revisions");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), carrying);
            require(BodyHandStateService.representable(carrying, body), "empty grid hosts remain representable");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", carrying.revision(), true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip fresh bag");
            LiveHands afterBag = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "right", "belt", "belt", afterBag.revision(), true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip fresh belt");
            var equipment = LiveHands.equipmentSnapshot(body).orElseThrow();
            for (var slot : equipment.slots()) {
                var stack = slot.stack().orElseThrow();
                var before = stack.copy();
                var grid = GridContents.read(stack).orElseThrow();
                require(grid.entries().isEmpty(), slot.id() + " starts with empty grid contents");
                require(stack.is(ModItems.BAG) == slot.id().equals("back"), slot.id() + " host item matches slot");
                require(ItemStack.matches(stack, before)
                        && stack.getCount() == before.getCount()
                        && stack.getComponents().equals(before.getComponents()),
                        slot.id() + " read does not mutate source stack");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gridRoundTripRequiresFreshRepresentableStateAndConservesItem(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands withBag = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), withBag);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip grid bag");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            LiveHands carrying = equipped.putWhole(2, "left", new ItemToken("apple"), new ItemStack(Items.APPLE, 3)).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), carrying);

            // This is the same server-derived readiness/projection gate used by a full query, without
            // manufacturing a ServerPlayer or asserting that GameTest proved connection authority.
            require(BodyHandStateService.representable(carrying, body), "initial full state is representable");
            long sourceRevision = carrying.revision();
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "apple", sourceRevision,
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, true, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "GRID_STORE_BACK transition");
            LiveHands stored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(stored.revision() == sourceRevision + 1 && stored.stackCopy("left").isEmpty(), "store publishes one revision");
            var host = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(slot -> slot.id().equals("back")).findFirst().orElseThrow().stack().orElseThrow();
            var entries = GridContents.read(host).orElseThrow().entries();
            require(entries.size() == 1 && entries.getFirst().token().equals("apple")
                    && entries.getFirst().stack().getCount() == 3, "stored item/count conserved");
            require(BodyHandStateService.representable(stored, body), "fresh post-store state is representable");

            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "wrong-bag", "left", "apple", stored.revision(),
                    new GridGeometry.Cell(0, 0), null, false, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "wrong host token denied");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "apple", sourceRevision,
                    new GridGeometry.Cell(0, 0), null, false, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "stale revision denied");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "apple", stored.revision(),
                    new GridGeometry.Cell(1, 0), null, false, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "wrong cell/overlap denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == stored
                    && GridContents.read(host).orElseThrow().entries().size() == 1, "denials leave ownership unchanged");

            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "apple", stored.revision(),
                    new GridGeometry.Cell(0, 0), null, false, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "GRID_TAKE_BACK transition");
            LiveHands taken = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(taken.revision() == stored.revision() + 1 && taken.stackCopy("left").orElseThrow().is(Items.APPLE)
                    && taken.stackCopy("left").orElseThrow().getCount() == 3, "take conserves original stack");
            require(BodyHandStateService.representable(taken, body), "fresh post-take state is representable");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
