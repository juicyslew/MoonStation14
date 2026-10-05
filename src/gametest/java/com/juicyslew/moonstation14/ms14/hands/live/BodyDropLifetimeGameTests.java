package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BodyDropLifetimeGameTests {
    private BodyDropLifetimeGameTests() { }

    public static void droppedHandItemSurvivesLifetimeAndRoundTrips(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(LiveHands.initialize(body).isPresent(), "body hand fixture initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = new ItemStack(ModItems.PICKAXE.get(), 1);
        LiveHands.WholeResult stored = initial.putWhole(initial.revision(), "left", new ItemToken("drop-lifetime"), stack);
        require(stored.succeeded(), "fixture item stored in body hand");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());

        require(BodyHandItemTransfer.dropOwned(body, "left", "drop-lifetime", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "trusted fixture drop succeeds");
        LiveHands emptied = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(emptied.token("left").isEmpty(), "drop empties body hand");
        var droppedEntities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(ModItems.PICKAXE));
        require(droppedEntities.size() == 1, "exactly one dropped item exists");
        ItemEntity dropped = droppedEntities.getFirst();
        require(dropped.getItem().getCount() == 1 && dropped.getAge() < 6000,
                "spawned entity owns the full stack before expiration");

        CompoundTag saved = dropped.saveWithoutId(new CompoundTag());
        require(saved.getInt("Age") == -32768, "unlimited lifetime is represented by the persistent age sentinel");
        ItemEntity loaded = EntityType.ITEM.create(helper.getLevel());
        require(loaded != null, "item entity load fixture created");
        loaded.load(saved);
        require(loaded.getAge() == -32768 && !loaded.isRemoved(), "unlimited lifetime survives entity save/load");
        loaded.tick();
        require(!loaded.isRemoved() && loaded.getAge() == -32768, "dropped item persists with unlimited lifetime");
        require(ItemStack.matches(dropped.getItem(), loaded.getItem()), "round-trip preserves the exact stack");

        dropped.discard();
        require(dropped.isRemoved(), "original dropped item removed before round-trip entity is added");
        loaded.setPos(body.position());
        require(helper.getLevel().addFreshEntity(loaded), "round-tripped item entity added");
        loaded.tick();
        require(!loaded.isRemoved() && loaded.getAge() == -32768, "added round-tripped item persists with unlimited lifetime");
        require(ItemStack.matches(dropped.getItem(), loaded.getItem()), "world entity retains exact stack after ticking");

        ItemEntity vanilla = new ItemEntity(helper.getLevel(), body.getX(), body.getY(), body.getZ(), new ItemStack(Items.STICK));
        require(helper.getLevel().addFreshEntity(vanilla), "vanilla control item added");
        require(vanilla.getAge() == 0 && vanilla.saveWithoutId(new CompoundTag()).getInt("Age") == 0,
                "unrelated vanilla item keeps its default age/lifetime state");
        loaded.discard();
        vanilla.discard();
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
