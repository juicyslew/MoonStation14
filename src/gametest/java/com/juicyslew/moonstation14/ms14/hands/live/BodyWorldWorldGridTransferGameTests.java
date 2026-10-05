package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class BodyWorldWorldGridTransferGameTests {
    private BodyWorldWorldGridTransferGameTests() { }
    private static final GridGeometry.Rotation R0 = GridGeometry.Rotation.DEG_0;
    private static final GridGeometry.Cell ZERO = new GridGeometry.Cell(0, 0);

    public static void movesBetweenTwoWorldHosts(GameTestHelper helper) {
        var setup = setup(helper);
        ItemStack beforeSource = setup.source.getItem(), beforeDestination = setup.destination.getItem();
        require(BodyWorldWorldGridTransfer.transfer(setup.body, setup.source, setup.destination, "child",
                0, 0, R0, 1, 0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.SUCCESS, "transfer succeeds");
        var from = grid(setup.source).entries(); var to = grid(setup.destination).entries();
        require(from.size() == 1 && from.getFirst().token().equals("stay") && from.getFirst().stack().getCount() == 2,
                "source unrelated child retained");
        require(to.size() == 2 && to.get(1).token().equals("child") && to.get(1).x() == 1
                && to.get(1).stack().getCount() == 37
                && to.get(1).stack().get(DataComponents.CUSTOM_NAME).getString().equals("world-child"), "child token/count/components preserved");
        require(setup.source.getItem() != beforeSource && setup.destination.getItem() != beforeDestination,
                "both world host stacks published");
        helper.succeed();
    }

    public static void staleDuplicateCollisionAndValidationDenialsDoNotWrite(GameTestHelper helper) {
        var setup = setup(helper);
        ItemStack a = setup.source.getItem(), b = setup.destination.getItem();
        require(transfer(setup, 0, 1, R0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "stale anchor denied");
        require(transfer(setup, 0, 0, GridGeometry.Rotation.DEG_90, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "stale rotation denied");
        require(transfer(setup, 0, 0, R0, R0, () -> false) == BodyWorldWorldGridTransfer.Outcome.DENIED, "revalidation denied");
        require(BodyWorldWorldGridTransfer.transfer(setup.body, setup.source, setup.destination, "child", 0, 0, R0,
                -1, 0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "invalid destination anchor denied");
        require(setup.source.getItem() == a && setup.destination.getItem() == b,
                "invalid anchor denial leaves both hosts unchanged");
        require(BodyWorldWorldGridTransfer.transfer(setup.body, setup.source, setup.source, "child", 0, 0, R0,
                1, 0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "same entity denied");
        ItemStack duplicate = setup.destination.getItem().copy();
        duplicate.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("child", new ItemStack(Items.STICK), 0, 0, R0))));
        setup.destination.setItem(duplicate);
        ItemStack collisionBefore = setup.destination.getItem();
        require(transfer(setup, 0, 0, R0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "duplicate token denied");
        require(setup.source.getItem() == a && setup.destination.getItem() == collisionBefore, "duplicate denial has no writes");
        ItemStack occupied = setup.destination.getItem().copy();
        occupied.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("block", new ItemStack(Items.STICK), 1, 0, R0))));
        setup.destination.setItem(occupied);
        ItemStack fullBefore = setup.destination.getItem();
        require(transfer(setup, 0, 0, R0, R0, () -> true) == BodyWorldWorldGridTransfer.Outcome.DENIED, "colliding destination denied");
        require(setup.source.getItem() == a && setup.destination.getItem() == fullBefore && b != fullBefore,
                "collision denial leaves both hosts unchanged");
        helper.succeed();
    }

    public static void revalidationAfterSourcePublicationCompensates(GameTestHelper helper) {
        var setup = setup(helper); AtomicInteger calls = new AtomicInteger();
        ItemStack a = setup.source.getItem(), b = setup.destination.getItem();
        var outcome = transfer(setup, 0, 0, R0, R0, () -> calls.incrementAndGet() < 3);
        require(outcome == BodyWorldWorldGridTransfer.Outcome.DENIED, "stale authority denied after publication");
        require(setup.source.getItem() != a && ItemStack.matches(setup.source.getItem(), a)
                && setup.destination.getItem() == b && grid(setup.source).entries().size() == 2,
                "untouched source conditionally compensated; child remains available");
        helper.succeed();
    }

    public static void mutationDuringPublicationRequiresRecovery(GameTestHelper helper) {
        var setup = setup(helper); AtomicInteger calls = new AtomicInteger();
        var outcome = transfer(setup, 0, 0, R0, R0, () -> {
            if (calls.incrementAndGet() == 3) {
                ItemStack changed = setup.source.getItem().copy();
                changed.set(DataComponents.CUSTOM_NAME, Component.literal("external mutation"));
                setup.source.setItem(changed);
            }
            return true;
        });
        require(outcome == BodyWorldWorldGridTransfer.Outcome.RECOVERY_REQUIRED,
                "external stack mutation after first publication is not accepted");
        helper.succeed();
    }

    public static void inPlaceCallbackMutationsAreNotOverwritten(GameTestHelper helper) {
        var preflight = setup(helper); AtomicInteger calls = new AtomicInteger();
        ItemStack original = preflight.source.getItem();
        var denied = transfer(preflight, 0, 0, R0, R0, () -> {
            if (calls.incrementAndGet() == 2) original.setCount(original.getCount() + 1);
            return true;
        });
        require(denied == BodyWorldWorldGridTransfer.Outcome.DENIED && preflight.source.getItem() == original
                && original.getCount() == 2, "preflight in-place mutation is detected without publication");

        var afterPublish = setup(helper); calls.set(0);
        ItemStack published = null;
        var result = transfer(afterPublish, 0, 0, R0, R0, () -> {
            if (calls.incrementAndGet() == 3) afterPublish.source.getItem().setCount(4);
            return true;
        });
        published = afterPublish.source.getItem();
        require(result == BodyWorldWorldGridTransfer.Outcome.RECOVERY_REQUIRED && published.getCount() == 4,
                "post-publication in-place mutation is detected and not compensated over");
        helper.succeed();
    }

    private static BodyWorldWorldGridTransfer.Outcome transfer(Setup s, int oldX, int oldY,
            GridGeometry.Rotation oldRotation, GridGeometry.Rotation newRotation, java.util.function.BooleanSupplier validate) {
        return BodyWorldWorldGridTransfer.transfer(s.body, s.source, s.destination, "child", oldX, oldY,
                oldRotation, 1, 0, newRotation, validate);
    }

    private static Setup setup(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1)); body.setNoAi(true);
        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack left = host(List.of(new GridContents.Entry("child", child(), 0, 0, R0),
                new GridContents.Entry("stay", new ItemStack(Items.GOLD_INGOT, 2), 2, 0, R0)));
        ItemStack right = host(List.of(new GridContents.Entry("other", new ItemStack(Items.APPLE), 0, 0, R0)));
        ItemEntity source = spawn(helper, p, left, 2.5), destination = spawn(helper, p, right, 3.5);
        return new Setup(body, source, destination);
    }
    private static ItemStack child() {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 37);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("world-child")); return stack;
    }
    private static ItemStack host(List<GridContents.Entry> entries) {
        ItemStack stack = new ItemStack(ModItems.BAG.get()); stack.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(entries)); return stack;
    }
    private static ItemEntity spawn(GameTestHelper helper, BlockPos p, ItemStack stack, double dx) {
        ItemEntity item = new ItemEntity(helper.getLevel(), p.getX() + dx, p.getY() + 1.5, p.getZ() + .5, stack);
        require(helper.getLevel().addFreshEntity(item), "spawn host"); item.setNoPickUpDelay(); return item;
    }
    private static GridContents grid(ItemEntity host) { return GridContents.read(host.getItem()).orElseThrow(); }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private record Setup(Villager body, ItemEntity source, ItemEntity destination) { }
}
