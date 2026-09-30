package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BodyDropCompensationGameTests {
    private BodyDropCompensationGameTests() { }

    public static void canceledSpawnRestoresOwnershipAndStalesRequest(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands before = stocked(body);
        AtomicInteger canceled = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)
                    && event.getLevel() == helper.getLevel()) {
                canceled.incrementAndGet();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            require(BodyHandItemTransfer.dropOwned(body, "left", "owned", before.revision(), () -> true)
                    == BodyHandItemTransfer.Result.DENIED, "canceled spawn must compensate");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(canceled.get() == 1 && after != null && after.revision() == before.revision() + 2
                && after.token("left").orElseThrow().equals("owned")
                && after.stackCopy("left").orElseThrow().getCount() == 3,
                "original ownership/token must be restored at a new revision");
        require(BodyHandItemTransfer.dropOwned(body, "left", "owned", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "original request cannot replay");
        require(helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "cancellation cannot leave a second copy");
        CompoundTag saved = body.saveWithoutId(new CompoundTag());
        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        require(loaded != null, "load fixture");
        loaded.load(saved);
        LiveHands persisted = loaded.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(persisted != null && persisted.revision() == after.revision()
                && persisted.token("left").orElseThrow().equals("owned")
                && persisted.stackCopy("left").orElseThrow().getCount() == 3,
                "same body save/load retains compensated owner");
        helper.succeed();
    }

    public static void successfulSpawnAndInvalidHandDoNotDuplicate(GameTestHelper helper) {
        Villager body = body(helper);
        body.setYRot(-90);
        LiveHands before = stocked(body);
        require(BodyHandItemTransfer.dropOwned(body, "right", "owned", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "empty hand cannot drop another hand's token");
        require(BodyHandItemTransfer.dropOwned(body, "left", "different", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "mismatched token cannot drop");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before,
                "invalid requests cannot alter ownership");
        require(BodyHandItemTransfer.dropOwned(body, "left", "owned", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "normal spawn succeeds");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after != null && after.revision() == before.revision() + 1 && after.token("left").isEmpty(),
                "successful drop clears exactly one hand");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND));
        require(items.size() == 1 && items.getFirst().getItem().getCount() == 3,
                "one world item owns the original stack");
        require(items.getFirst().getX() > body.getX() + .5 && items.getFirst().getZ() == body.getZ(),
                "exact spawned entity is placed ahead of authoritative body yaw");
        require(BodyHandItemTransfer.dropOwned(body, "left", "owned", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "successful request cannot replay");
        helper.succeed();
    }

    public static void blockedForwardDropFallsBackToFeet(GameTestHelper helper) {
        Villager body = body(helper);
        body.setYRot(-90);
        LiveHands before = stocked(body);
        BlockPos blocker = BlockPos.containing(body.getX() + .7, body.getY(), body.getZ());
        helper.getLevel().setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
        require(BodyHandItemTransfer.dropOwned(body, "left", "owned", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "blocked forward position uses safe fallback");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(2),
                item -> item.getItem().is(Items.DIAMOND));
        require(items.size() == 1 && items.getFirst().position().equals(body.position()),
                "one exact item at feet, not inside forward block");
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(body.isAddedToLevel() && LiveHands.initialize(body).isPresent(), "spawned human fixture");
        return body;
    }

    private static LiveHands stocked(Villager body) {
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands.WholeResult put = empty.putWhole(empty.revision(), "left", new ItemToken("owned"),
                new ItemStack(Items.DIAMOND, 3));
        require(put.succeeded(), "fixture source inserted");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), put.state());
        return put.state();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
