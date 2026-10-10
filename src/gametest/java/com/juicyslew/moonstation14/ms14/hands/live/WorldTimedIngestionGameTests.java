package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"timed-interaction", "ingestion", "hands", "chemistry"})
@PrefixGameTestTemplate(false)
public final class WorldTimedIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private WorldTimedIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void worldSourceContinuesAfterLookAwayAndHandPutThenCompletesEmpty(GameTestHelper h) {
        Fixture f = fixture(h, 10);
        require(start(f, 1).name().equals("OK"), "world ingestion starts from exact loose source");
        require(source(f) == 1000 && stomach(f.body) == 0, "start transfers no reagent");
        f.body.setYRot(90); f.body.setYHeadRot(90); // no longer looking at the source
        LiveHands before = hands(f.body);
        ItemStack ordinary = new ItemStack(net.minecraft.world.item.Items.STICK);
        var put = before.putWhole(before.revision(), "left", new ItemToken("other-item"), ordinary);
        require(put.succeeded(), "ordinary item legitimately put into active hand");
        f.body.setData(ModDataAttachments.LIVE_HANDS.get(), put.state());
        h.runAfterDelay(21, () -> {
            require(source(f) == 500, "first dose committed after timed interval");
            require(f.level.getEntity(f.sourceId) == f.source && !f.source.isRemoved(), "source remains same world entity");
            require(ItemStack.matches(ordinary, hands(f.body).stackCopy("left").orElseThrow()), "active hand unchanged");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 21, () -> {
                require(source(f) == 0, "second dose empties source");
                require(f.level.getEntity(f.sourceId) == f.source && !f.source.isRemoved(), "empty bottle retained as world entity");
                require(f.source.getItem().getCount() == 1 && f.source.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        && "World bottle".equals(f.source.getItem().getHoverName().getString()), "original item count and custom name retained");
                require(ItemStack.matches(ordinary, hands(f.body).stackCopy("left").orElseThrow()), "dose did not overwrite hand");
                require(hasPhase(f.body, TimedOperation.Phase.COMPLETED), "operation completes without another empty repeat");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void replacingWorldSourceBeforeDeadlineCancelsWithoutDosingReplacement(GameTestHelper h) {
        Fixture f = fixture(h, 10);
        require(start(f, 2).name().equals("OK"), "world ingestion starts");
        require(source(f) == 1000, "no immediate dose");
        f.source.discard();
        LooseItemEntity replacement = LooseItemEntity.create(f.level, f.source.position(), f.source.getItem().copy());
        require(f.level.addFreshEntity(replacement), "replacement source spawned at same position");
        require(!replacement.getUUID().equals(f.sourceId), "replacement has different identity");
        h.runAfterDelay(2, () -> {
            require(cancelled(f.body), "removed admitted identity cancels operation");
            require(water(replacement) == 1000 && stomach(f.body) == 0, "replacement receives no dose");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 21, () -> {
                require(!hasPhase(f.body, TimedOperation.Phase.CANCELLED), "cancelled operation is pruned after terminal retention");
                require(water(replacement) == 1000 && stomach(f.body) == 0, "pruned operation never doses replacement");
                h.succeed();
            });
        });
    }

    private static Fixture fixture(GameTestHelper h, float units) {
        var level = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(p.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(p.getX() + .5, p.getY(), p.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body and hands initialized");
        LiveHands hands = hands(body);
        ItemStack stack = new ItemStack(ModItems.BOTTLE.get());
        stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("World bottle"));
        Vec3 pos = new Vec3(p.getX() + 2.5, p.getY(), p.getZ() + .2);
        LooseItemEntity source = LooseItemEntity.create(level, pos, stack);
        require(level.addFreshEntity(source), "loose bottle spawned");
        body.setYRot(-90); body.setYHeadRot(-90);
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - source.getBoundingBox().getCenter().y,
                Math.sqrt(Math.pow(body.getX() - source.getX(), 2) + Math.pow(body.getZ() - source.getZ(), 2)))));
        return new Fixture(level, body, source, source.getUUID(), hands.activeHand(), hands.revision());
    }

    private static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason start(Fixture f, long seq) {
        return BodyTimedIngestionService.startWorldOwned(f.body, f.sourceId, f.hand, f.revision, seq, () -> true);
    }
    private static LiveHands hands(Villager b) { return b.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static long source(Fixture f) { return water(f.source); }
    private static long water(net.minecraft.world.entity.item.ItemEntity e) {
        var c = e.getItem().get(ModDataComponents.REAGENT.get());
        return c == null ? 0 : ReagentUnits.fromFloat(c.contents().getOrDefault(WATER, 0f));
    }
    private static long stomach(Villager b) {
        var map = com.juicyslew.moonstation14.ms14.MS14Provider.get(b,
                com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
        return ReagentUnits.fromFloat(map.getOrDefault(WATER, 0f));
    }
    private static boolean cancelled(Villager b) { return hasPhase(b, TimedOperation.Phase.CANCELLED); }
    private static boolean hasPhase(Villager b, TimedOperation.Phase phase) {
        TimedOperationState s = b.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        return s.operations().stream().anyMatch(o -> o.phase() == phase);
    }
    private record Fixture(net.minecraft.server.level.ServerLevel level, Villager body, LooseItemEntity source,
                           UUID sourceId, String hand, long revision) { }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
