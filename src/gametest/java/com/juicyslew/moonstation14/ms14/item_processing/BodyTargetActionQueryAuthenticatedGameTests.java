package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Live authenticated-player query policy tests; direct service calls do not claim packet-transport coverage. */
@PrefixGameTestTemplate(false)
@GameTestTags({"butchering", "butchering-query", "interaction"})
public final class BodyTargetActionQueryAuthenticatedGameTests {
    private BodyTargetActionQueryAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000, batch = "catalog-query-admission")
    @GameTestTags({"butchering-query-authenticated", "merge-main-remaining", "merge-main-stage-two"})
    public static void liveQueryAdmissionEchoesProofAndTracksSourceAndSharpEligibility(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(
                helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "target-action-query", (h, fixture, epoch, hand,
                        tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var toolType = ModToolProfiles.TYPE;
            var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var done = new AtomicBoolean();
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, done, manager, procType, oldProcessing,
                    toolType, oldTools, ingestionType, oldIngestion, null);
            try {
                var tomatoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                var processing = new HashMap<>(oldProcessing);
                processing.put(tomatoId, new ItemProcessingProfile(null, null, new ItemProcessingProfile.Butcherable(
                        java.util.List.of(new ItemProcessingProfile.ItemOutput(
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get()), 1)),
                        .25f, ItemProcessingProfile.Butcherable.Type.KNIFE)));
                manager.publishDecoded(procType, processing);

                var actor = fixture.player();
                var authority = HandActorAuthority.resolve(actor).orElseThrow();
                var body = authority.body();
                var source = LooseItemEntity.create(level, body.position().add(0, .1, 1.5),
                        new ItemStack(ModItems.TOMATO.get()));
                source.setNoGravity(true);
                require(level.addFreshEntity(source), "scoped butcherable source spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        source.getBoundingBox().getCenter());
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hand.equals(hands.activeHand()) && hands.token(hand).isEmpty(), "fixture starts in active empty hand");

                var emptyProof = new BodyTargetActionQuery(epoch, 1, hands.revision(), hand, null, source.getUUID());
                var empty = BodyTargetActionService.query(actor, emptyProof);
                require(echoes(emptyProof, empty), "valid nominated source echoes exact empty-hand proof");
                require(empty.hasButcher() && !empty.enabled(), "butcherable remains visible with empty/non-sharp hand");
                require(empty.disabledReason() != null
                                && "moonstation14.target_action.need_sharp".equals(
                                        ((net.minecraft.network.chat.contents.TranslatableContents) empty.disabledReason().getContents()).getKey()),
                        "disabled reason uses localized source sharp-object wording");

                var wrongTarget = new BodyTargetActionQuery(epoch, 2, hands.revision(), hand, null,
                        java.util.UUID.randomUUID());
                require(BodyTargetActionService.query(actor, wrongTarget) == null, "wrong UUID is rejected without target disclosure");
                var far = LooseItemEntity.create(level, body.position().add(0, .1, 6.5), new ItemStack(ModItems.TOMATO.get()));
                far.setNoGravity(true);
                require(level.addFreshEntity(far), "out-of-reach target spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
                require(BodyTargetActionService.query(actor, new BodyTargetActionQuery(epoch, 3, hands.revision(), hand,
                        null, far.getUUID())) == null, "out-of-reach nominated source denied");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());

                var staleEpoch = new BodyTargetActionQuery(epoch + 1, 4, hands.revision(), hand, null, source.getUUID());
                var staleRevision = new BodyTargetActionQuery(epoch, 5, hands.revision() + 1, hand, null, source.getUUID());
                var staleToken = new BodyTargetActionQuery(epoch, 6, hands.revision(), hand, "not-the-live-token", source.getUUID());
                var staleHand = new BodyTargetActionQuery(epoch, 7, hands.revision(), "not-a-hand", null, source.getUUID());
                require(BodyTargetActionService.query(actor, staleEpoch) == null, "stale body epoch rejected");
                require(BodyTargetActionService.query(actor, staleRevision) == null, "stale hand revision rejected");
                require(BodyTargetActionService.query(actor, staleToken) == null, "stale hand token rejected");
                require(BodyTargetActionService.query(actor, staleHand) == null, "wrong hand rejected");
                require(!source.isRemoved() && hands.revision() == emptyProof.expectedRevision(),
                        "denied queries preserve source and hand state");

                // A registered production Kitchen Knife is used without changing its resolved Sharp profile.
                var knifeProfile = com.juicyslew.moonstation14.ms14.tools.ToolQualities.profile(
                        new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                require(knifeProfile != null && knifeProfile.sharp() != null, "production Kitchen Knife resolves Sharp");
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8),
                        new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true);
                require(level.addFreshEntity(knife), "production Kitchen Knife spawned");
                require(BodyHandItemTransfer.pickupSelected(actor, knife.getUUID(), hand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "authenticated fixture acquires production knife");
                hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var knifeProof = new BodyTargetActionQuery(epoch, 8, hands.revision(), hand,
                        hands.token(hand).orElseThrow(), source.getUUID());
                var enabled = BodyTargetActionService.query(actor, knifeProof);
                require(echoes(knifeProof, enabled) && enabled.hasButcher() && enabled.enabled()
                                && enabled.disabledReason() == null,
                        "production Sharp tool enables butcher and echoes exact token proof");

                var plain = LooseItemEntity.create(level, body.position().add(0, .1, 2.0),
                        new ItemStack(net.minecraft.world.item.Items.APPLE));
                plain.setNoGravity(true);
                require(level.addFreshEntity(plain), "source without butcherable composition spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, plain.getBoundingBox().getCenter());
                var absent = BodyTargetActionService.query(actor, new BodyTargetActionQuery(epoch, 9, hands.revision(), hand,
                        hands.token(hand).orElseThrow(), plain.getUUID()));
                require(absent != null && !absent.hasButcher() && !absent.enabled() && absent.disabledReason() == null,
                        "valid target without butcherable composition reports no butcher action");

                // Service tests intentionally bypass packet handling; the registered server route remains separately unverified.
                cleanup.finish(null);
            } catch (Throwable failure) { cleanup.finish(failure); }
        });
    }

    private static boolean echoes(BodyTargetActionQuery query, com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot snapshot) {
        return snapshot != null && snapshot.bodyEpoch() == query.bodyEpoch() && snapshot.sequence() == query.sequence()
                && snapshot.expectedRevision() == query.expectedRevision() && snapshot.handId().equals(query.handId())
                && java.util.Objects.equals(snapshot.expectedToken(), query.expectedToken())
                && snapshot.targetId().equals(query.targetId());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
