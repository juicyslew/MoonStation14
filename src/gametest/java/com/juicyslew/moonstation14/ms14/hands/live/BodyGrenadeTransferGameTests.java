package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ThrownGrenadeEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BodyGrenadeTransferGameTests {
    private BodyGrenadeTransferGameTests() { }

    public static void transfersOnlyOwnedArmedGrenade(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands stocked = stock(body);
        long now = helper.getLevel().getGameTime();
        require(BodyGrenadePrime.primeOwned(body, "left", "grenade", stocked.revision(), () -> true)
                == BodyGrenadePrime.Result.SUCCESS, "active hand primes its owned grenade");
        LiveHands primed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        GrenadeFuseData fuse = primed.stackCopy("left").orElseThrow().get(ModDataComponents.GRENADE_FUSE.get());
        require(fuse.phase() == GrenadeFuse.Phase.ARMED && fuse.deadline() > now, "priming arms grenade");
        require(BodyGrenadeThrow.throwOwned(body, "right", "grenade", primed.revision(), () -> true)
                == BodyGrenadeThrow.Result.DENIED, "inactive hand cannot throw");
        require(BodyGrenadeThrow.throwOwned(body, "left", "wrong", primed.revision(), () -> true)
                == BodyGrenadeThrow.Result.DENIED, "wrong token cannot throw");
        require(BodyGrenadeThrow.throwGrenade(null, "left", "grenade", primed.revision())
                == BodyGrenadeThrow.Result.DENIED, "public route rejects absent player authority");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == primed,
                "denied requests leave ownership unchanged");

        require(BodyGrenadeThrow.throwOwned(body, "left", "grenade", primed.revision(), () -> true)
                == BodyGrenadeThrow.Result.SUCCESS, "fresh active-hand request throws");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after.revision() == primed.revision() + 1 && after.token("left").isEmpty(),
                "successful transfer clears hand and increments revision");
        require(BodyGrenadeThrow.throwOwned(body, "left", "grenade", primed.revision(), () -> true)
                == BodyGrenadeThrow.Result.DENIED, "stale request cannot replay");
        var thrown = helper.getLevel().getEntitiesOfClass(ThrownGrenadeEntity.class, body.getBoundingBox().inflate(8));
        require(thrown.size() == 1 && thrown.getFirst().getItem().getCount() == 1,
                "exactly one armed grenade projectile exists");
        GrenadeFuseData inFlight = thrown.getFirst().getItem().get(ModDataComponents.GRENADE_FUSE.get());
        require(inFlight.equals(fuse), "throw preserves original fuse deadline and state");
        helper.runAfterDelay(3, () -> {
            require(thrown.getFirst().isAlive() && thrown.getFirst().getItem().get(ModDataComponents.GRENADE_FUSE.get()).equals(fuse),
                    "normal collision does not immediately detonate or alter fuse");
            helper.succeed();
        });
    }

    public static void canceledProjectileSpawnRestoresExactOwnership(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands stocked = stock(body);
        require(BodyGrenadePrime.primeOwned(body, "left", "grenade", stocked.revision(), () -> true)
                == BodyGrenadePrime.Result.SUCCESS, "prime fixture grenade");
        LiveHands primed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack exact = primed.stackCopy("left").orElseThrow();
        AtomicInteger canceled = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof ThrownGrenadeEntity && event.getLevel() == helper.getLevel()) {
                canceled.incrementAndGet();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            require(BodyGrenadeThrow.throwOwned(body, "left", "grenade", primed.revision(), () -> true)
                    == BodyGrenadeThrow.Result.DENIED, "canceled spawn compensates ownership");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        LiveHands restored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(canceled.get() == 1 && restored.revision() == primed.revision() + 2
                && restored.token("left").orElseThrow().equals("grenade")
                && ItemStack.isSameItemSameComponents(exact, restored.stackCopy("left").orElseThrow()),
                "exact armed stack is restored once with a fresh token revision");
        require(helper.getLevel().getEntitiesOfClass(ThrownGrenadeEntity.class, body.getBoundingBox().inflate(8)).isEmpty(),
                "canceled transfer leaves no projectile orphan");
        require(BodyGrenadeThrow.throwOwned(body, "left", "grenade", primed.revision(), () -> true)
                == BodyGrenadeThrow.Result.DENIED, "pre-compensation request is stale");
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(body.isAddedToLevel() && LiveHands.initialize(body).isPresent(), "real spawned eligible body initialized");
        return body;
    }

    private static LiveHands stock(Villager body) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var inserted = before.putWhole(before.revision(), "left", new ItemToken("grenade"), new ItemStack(ModItems.GRENADE.get()));
        require(inserted.succeeded(), "insert grenade into owned hand");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        return inserted.state();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
