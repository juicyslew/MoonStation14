package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireOpen;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequestFixtureBridge;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsFixtureTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered-player normal BodyBlockUse wire-menu path; this is not connected cursor evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"feature-readiness"})
public final class AirlockWireOpenRequestGameTests {
    private AirlockWireOpenRequestGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void normalToolUseOpensAndRetainsWireSession(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(
                helper.getLevel(), "readiness-wire-open");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos root = helper.absolutePos(new BlockPos(5, 1, 5));
                var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
                helper.getLevel().setBlock(root.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(root, lower, 3);
                helper.getLevel().setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
                AirlockBlockEntity door = (AirlockBlockEntity) helper.getLevel().getBlockEntity(root);
                require(door != null && door.prototype() != null
                                && door.prototype().wiresPanelSecurity().wiresAccessible()
                                && door.prototype().wiresPanel().openingToolQuality()
                                == com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.SCREWING,
                        "resolved airlock composition supplies the accessible panel and wire policy");
                var body = owner.body();
                body.setPos(root.getX() + 1.5, root.getY(), root.getZ() + 0.5);
                // The lower half's reachable face is occluded by the adjacent upper half; use the
                // actual upper-half hit that AirlockToolInteraction resolves back to this root.
                var bodyHit = RegisteredPlayerReadyDriver.aimAt(body,
                        (net.minecraft.server.level.ServerLevel) helper.getLevel(), root.above());
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, "left", "wire-screwdriver",
                                new net.minecraft.world.item.ItemStack(ModItems.SCREWDRIVER.get()))
                                && LiveHandsFixtureTransfer.put(fixture.player(), body, "right", "wire-cutter",
                                new net.minecraft.world.item.ItemStack(ModItems.WIRECUTTER.get())),
                        "resolved screwdriver and cutter staged through supported hand transfer/gateway");
                var state = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(
                        fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                require(state.reason() == BodyHandStateSnapshot.Reason.OK,
                        "full state query issues proof after actual tool staging");
                require(com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(
                                new net.minecraft.world.item.ItemStack(ModItems.SCREWDRIVER.get()),
                                com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.SCREWING)
                                && com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(
                                new net.minecraft.world.item.ItemStack(ModItems.WIRECUTTER.get()),
                                com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.CUTTING),
                        "resolved tool catalogs authorize screwdriver opening and cutter operation");
                var screwdriver = state.hands().stream().filter(slot -> slot.id().equals("left")).findFirst().orElseThrow();

                BodyBlockUseRequest staleEpoch = new BodyBlockUseRequest(owner.epoch() + 1, 1, state.revision(),
                        "left", screwdriver.token(), bodyHit.getBlockPos(), bodyHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                require(BodyHandActionNetworking.handleBlockUse(fixture.player(), staleEpoch)
                                == BodyBlockUseResult.Outcome.DENIED && !door.panelOpen(),
                        "stale body epoch cannot trigger panel opening or mutate the door");
                BodyBlockUseRequest panel = new BodyBlockUseRequest(owner.epoch(), 1, state.revision(),
                        "left", screwdriver.token(), bodyHit.getBlockPos(), bodyHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                require(BodyHandActionNetworking.handleBlockUse(fixture.player(), panel)
                                == BodyBlockUseResult.Outcome.NO_CHANGE,
                        "normal admitted USE reaches resolved screwdriver panel interaction");
                helper.runAfterDelay(23, () -> {
                    try {
                        require(door.panelOpen(), "normal timed panel operation opens the maintenance panel");
                        var afterPanel = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(
                                fixture.player(), new BodyHandStateQuery(owner.epoch(), 3));
                        var select = new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                                "right", owner.epoch(), 1, afterPanel.revision(), null, null);
                        require(BodyHandRequestService.apply(fixture.player(), select).accepted(),
                                "ordinary hand selection activates the staged cutter");
                        var active = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(
                                fixture.player(), new BodyHandStateQuery(owner.epoch(), 4));
                        var cutter = active.hands().stream().filter(slot -> slot.id().equals("right")).findFirst().orElseThrow();
                        var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var wireAuthority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow();
                        var actualCutter = live.stackCopy("right").orElseThrow();
                        require(active.reason() == BodyHandStateSnapshot.Reason.OK && active.activeHand().equals("right")
                                        && live.revision() == active.revision() && cutter.token().equals(live.token("right").orElse(null))
                                        && actualCutter.is(ModItems.WIRECUTTER.get())
                                        && com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(actualCutter,
                                        com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.CUTTING)
                                        && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), wireAuthority)
                                        && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                                        && door.panelOpen() && door.panelVisible() && door.prototype().wiresPanelSecurity().wiresAccessible()
                                        && com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight.canReachRoot(
                                        body, (net.minecraft.server.level.ServerLevel) helper.getLevel(), root, door, door.identity()),
                                "wire dispatcher live preconditions: state=" + active.reason() + " hand=" + active.activeHand()
                                        + " revisions=" + live.revision() + "/" + active.revision() + " token="
                                        + (cutter.token() != null && cutter.token().equals(live.token("right").orElse(null)))
                                        + " actor=" + com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), wireAuthority)
                                        + " canAct=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                                        + " actualItem=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actualCutter.getItem())
                                        + " actualCut=" + com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(actualCutter,
                                        com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.CUTTING)
                                        + " panel=" + door.panelOpen() + "/" + door.panelVisible());
                        BodyBlockUseRequest openWire = new BodyBlockUseRequest(owner.epoch(), 2, active.revision(),
                                "right", cutter.token(), bodyHit.getBlockPos(), bodyHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), openWire)
                                        == BodyBlockUseResult.Outcome.NO_CHANGE,
                                "ordinary admitted cutter USE routes through BodyWorldItemActions and opens wire session");
                        AirlockWireOpen opened = null;
                        AirlockWireSnapshot initial = null;
                        java.util.ArrayList<String> outboundTypes = new java.util.ArrayList<>();
                        Object packet;
                        while ((packet = fixture.pollOutbound()) != null) {
                            var payloads = findWirePayloads(packet, outboundTypes);
                            for (Object payload : payloads) {
                                if (payload instanceof AirlockWireOpen open) opened = open;
                                if (payload instanceof AirlockWireSnapshot snapshot) initial = snapshot;
                            }
                        }
                        require(opened != null && initial != null && initial.containerId() == opened.windowId()
                                        && initial.session().equals(opened.session()) && !initial.rows().isEmpty(),
                                "normal dispatch emits matching wire OPEN and full authoritative snapshot; payloads="
                                        + outboundTypes + " panel=" + door.panelOpen() + "/" + door.panelVisible()
                                        + " active=" + body.getExistingDataOrNull(
                                        com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).activeHand()
                                        + " cutting=" + com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(
                                        new net.minecraft.world.item.ItemStack(ModItems.WIRECUTTER.get()),
                                        com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.CUTTING)
                                        + " cutterAlsoOpener=" + com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(
                                        new net.minecraft.world.item.ItemStack(ModItems.WIRECUTTER.get()),
                                        door.prototype().wiresPanel().openingToolQuality())
                                        + " rootReach=" + com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight.canReachRoot(
                                        body, (net.minecraft.server.level.ServerLevel) helper.getLevel(), root, door, door.identity())
                                        + " containerMenu=" + (fixture.player().containerMenu == fixture.player().inventoryMenu));
                        AirlockWireSnapshot refreshed = AirlockWireRequestFixtureBridge.request(fixture.player(),
                                new AirlockWireRequest(opened.windowId(), opened.session(), 1,
                                        initial.rootRevision(), -1, AirlockWireRequest.Operation.QUERY));
                        require(refreshed != null && refreshed.accepted() && refreshed.session().equals(opened.session()),
                                "same session accepts normal query refresh after ordinary server ticks");
                        var rowToCut = refreshed.rows().stream().filter(row -> !row.cut()).findFirst().orElse(null);
                        require(rowToCut != null, "authoritative wire snapshot has an uncut row for cutting-tool click");
                        AirlockWireSnapshot clicked = AirlockWireRequestFixtureBridge.request(fixture.player(),
                                new AirlockWireRequest(opened.windowId(), opened.session(), 2,
                                        refreshed.rootRevision(), rowToCut.id(),
                                        AirlockWireRequest.Operation.CLICK));
                        var clickedRow = clicked == null ? null : clicked.rows().stream()
                                .filter(row -> row.id() == rowToCut.id()).findFirst().orElse(null);
                        require(clicked != null && clicked.accepted() && clicked.rejection() == AirlockWireSnapshot.Rejection.NONE
                                        && clicked.session().equals(opened.session()) && clicked.requestId() == 2
                                        && clicked.rootRevision() > refreshed.rootRevision()
                                        && clickedRow != null && clickedRow.cut(),
                                "normal cutter CLICK mutates the specifically selected row: accepted="
                                        + (clicked != null && clicked.accepted()) + " rejection="
                                        + (clicked == null ? null : clicked.rejection()) + " request="
                                        + (clicked == null ? -1 : clicked.requestId()) + " session="
                                        + (clicked != null && clicked.session().equals(opened.session())) + " revision="
                                        + refreshed.rootRevision() + "->" + (clicked == null ? -1 : clicked.rootRevision())
                                        + " row=" + rowToCut.id() + " cut=" + rowToCut.cut() + "->"
                                        + (clickedRow != null && clickedRow.cut()));
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "Feature-readiness wire click accepted={}, rejection={}, request={}, revision={}->{}, row={} cut={}->{}",
                                clicked.accepted(), clicked.rejection(), clicked.requestId(), refreshed.rootRevision(),
                                clicked.rootRevision(), rowToCut.id(), rowToCut.cut(), clickedRow.cut());
                        helper.succeed();
                    } finally { fixture.close(); }
                });
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    private static java.util.List<Object> findWirePayloads(Object packet, java.util.List<String> types) {
        types.add(packet.getClass().getSimpleName());
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
            var found = new java.util.ArrayList<Object>();
            for (var nested : bundle.subPackets()) found.addAll(findWirePayloads(nested, types));
            return found;
        }
        if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) {
            types.add(custom.payload().getClass().getSimpleName());
            return java.util.List.of(custom.payload());
        }
        return java.util.List.of();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
