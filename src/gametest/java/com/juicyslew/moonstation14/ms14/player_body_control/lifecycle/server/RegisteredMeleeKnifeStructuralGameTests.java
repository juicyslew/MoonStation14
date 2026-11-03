package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Covers the registered knife's futile structural contact via the authenticated live request path. */
@GameTestTags({"registered-melee", "melee-roster", "structural", "melee-knife-structural"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeKnifeStructuralGameTests {
    private RegisteredMeleeKnifeStructuralGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_knife_structural", timeoutTicks=100)
    public static void registeredKitchenKnifeSteelWallContactEmitsWeakHitWithoutDamage(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-knife-structural");
        var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        fixture.observeOutbound(packet -> { if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) sounds.add(p); });
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) {
                try { fixture.close(); }
                catch (Throwable t) { if (i.getError() != null) i.getError().addSuppressed(t); else i.fail(t); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var origin = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
                for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++)
                    helper.getLevel().setBlock(origin.offset(x,-1,z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                body.teleportTo(origin.getX()+.5, origin.getY(), origin.getZ()+.5);
                body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0); body.tick();
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                if (hands == null) throw new GameTestAssertException("registered body has no LiveHands");
                var hand = hands.activeHand();
                var loose = com.juicyslew.moonstation14.entities.LooseItemEntity.create(helper.getLevel(), body.getEyePosition().add(body.getLookAngle().scale(.7)), new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get()));
                loose.setNoGravity(true);
                if (!helper.getLevel().addFreshEntity(loose)
                        || com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), hand, hands.revision(), ignored -> { })
                        != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                    throw new GameTestAssertException("canonical LiveHands knife pickup failed");
                 var pickedSelection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held knife profile unresolved"));
                 if (pickedSelection.kind() != MeleeWeaponResolver.Kind.HELD || !pickedSelection.profile().weapon().damage().containsKey("slash"))
                     throw new GameTestAssertException("knife did not resolve as held melee weapon: " + pickedSelection);
                 var pickedDeadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, pickedSelection).orElseThrow();
                 var now = helper.getLevel().getGameTime();
                 if (pickedDeadline <= now)
                     throw new GameTestAssertException("canonical knife pickup did not stage its expected selection cooldown: now="+now+",deadline="+pickedDeadline);
                 helper.runAfterDelay((int) Math.max(1L, pickedDeadline - now), () -> {
                 try {
                 var selection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held knife profile unresolved after selection cooldown"));
                 var legalNow = helper.getLevel().getGameTime();
                 var legalDeadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, selection).orElseThrow();
                 if (selection.kind() != MeleeWeaponResolver.Kind.HELD || legalNow < legalDeadline)
                     throw new GameTestAssertException("knife attack scheduled before legal selection cooldown: now="+legalNow+",deadline="+legalDeadline);
                 var pos = origin.offset(0, 1, 1);
                var wall = com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState();
                helper.getLevel().setBlock(pos, wall, 3);
                var point = new net.minecraft.world.phys.Vec3(pos.getX()+.5, pos.getY()+.5, pos.getZ());
                var nomination = new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos, net.minecraft.core.Direction.NORTH, point, net.minecraft.world.level.block.Block.getId(wall));
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                sounds.clear();
                 var request = new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, java.util.List.of(nomination), false);
                var damageBefore = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                var damageMapBefore = damageBefore == null ? java.util.Map.<String, Float>of() : new java.util.HashMap<>(damageBefore.getMap());
                var result = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), request);
                var cue = com.juicyslew.moonstation14.ms14.combat.MeleeSoundPolicy.outcome(selection.profile().weapon().audio(), null, false);
                var weakHit = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/effects/weak_hit");
                if (cue == null || !weakHit.equals(cue.event())) throw new GameTestAssertException("knife structural contact did not resolve exact NO_DAMAGE weak_hit cue: " + cue);
                var cueId = cue.event();
                var hits = sounds.stream().filter(p -> cueId.equals(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()))).toList();
                var normalHit = selection.profile().weapon().audio().hit();
                var normalHitCount = normalHit == null ? 0 : (int) sounds.stream().filter(p -> normalHit.event().equals(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()))).count();
                if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !result.cooldownConsumed()
                        || helper.getLevel().getBlockState(pos) != wall || hits.size() != 1 || normalHitCount != 0
                        || Math.abs(hits.getFirst().getX()-point.x()) > 1.5 || Math.abs(hits.getFirst().getY()-point.y()) > 1.5 || Math.abs(hits.getFirst().getZ()-point.z()) > 1.5)
                    throw new GameTestAssertException(knifeDiagnostic(result, helper.getLevel().getBlockState(pos), wall, hits, normalHitCount, sounds, body, selection, hands, nomination, cueId));
                var damageAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                if (!damageMapBefore.equals(damageAfter == null ? java.util.Map.<String, Float>of() : damageAfter.getMap()))
                    throw new GameTestAssertException("zero-damage knife structural contact altered initiator damage ledger");
                var after = MeleeWeaponResolver.resolve(body).orElseThrow();
                var deadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, after).orElseThrow();
                 var again = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 2, after.kind(), after.handRevision(), after.activeHand(), after.itemToken().orElse(""), com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, java.util.List.of(nomination), false));
                if (again.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED || again.cooldownConsumed()
                        || deadline != com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow())
                    throw new GameTestAssertException("second knife structural request bypassed normal cooldown");
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
                 });
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    private static String knifeDiagnostic(com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply result,
            net.minecraft.world.level.block.state.BlockState current, net.minecraft.world.level.block.state.BlockState expected,
            java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> weak,
            int normalCount, java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> all,
            net.minecraft.world.entity.LivingEntity body, MeleeWeaponResolver.Selection selection,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block nomination,
            net.minecraft.resources.ResourceLocation weakId) {
        var events = all.stream().limit(12).map(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()) + "@" + p.getX()+","+p.getY()+","+p.getZ()).toList();
        var detail = "status="+result.status()+",cooldown="+result.cooldownConsumed()+",block="+net.minecraft.world.level.block.Block.getId(current)+"/"+net.minecraft.world.level.block.Block.getId(expected)+",weak="+weak.size()+",normal="+normalCount+",sounds="+events+",body="+body.position()+",eyes="+body.getEyePosition()+",bounds="+body.getBoundingBox()+",look="+body.getLookAngle()+",selection="+selection.kind()+",token="+selection.itemToken().orElse("")+",handRev="+selection.handRevision()+",liveHandRev="+hands.revision()+",weakId="+weakId+",nomination="+nomination;
        System.out.println("MS14_KNIFE_STRUCTURAL_DIAGNOSTIC "+detail);
        return "knife structural contact diagnostic: "+detail;
    }
}
