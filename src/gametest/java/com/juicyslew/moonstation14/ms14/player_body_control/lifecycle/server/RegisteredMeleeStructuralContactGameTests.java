package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises structural contacts through the authenticated live melee request path. */
@GameTestTags({"melee", "combat", "shared-melee-runtime-fixture"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeStructuralContactGameTests {
    private RegisteredMeleeStructuralContactGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void optedInStationBlocksResolveButOpenAirlockAndNaturalTerrainDoNotContact(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            var level = helper.getLevel();
            var fists = new com.juicyslew.moonstation14.ms14.combat.MeleeWeaponData(java.util.Map.of("blunt", 5f), 1, 1.5f, false, false, 1, false);
            var construction = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverConstructionEntities();
            var contacts = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverBlockStructuralContacts();
            var floor = com.juicyslew.moonstation14.block.ModBlocks.STATION_FLOOR.get();
            var door = com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get();
            var wood = net.minecraft.world.level.block.Blocks.STONE;
            for (var block : java.util.List.of(floor, door, com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get(),
                    com.juicyslew.moonstation14.block.ModBlocks.PLASTIC_WALL.get(), com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_GIRDER_BLOCK.get())) {
                if (com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(block, fists, construction, contacts)
                        != com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT)
                    throw new GameTestAssertException("registered opted-in block did not resolve ineffective melee contact: " + block);
            }
            if (com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(wood, fists, construction, contacts)
                    != com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN)
                throw new GameTestAssertException("natural terrain was opted into station structural contact");
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
            var open = door.defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN, true);
            level.setBlock(pos, open, 3);
            if (!open.getCollisionShape(level, pos).isEmpty()) throw new GameTestAssertException("open airlock unexpectedly retained collision");
            helper.succeed();
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void resolverAppliesModifiersToComposedDamageForBothMappingKinds(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            var construction=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverConstructionEntities();
            var contacts=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverBlockStructuralContacts();
            var wall=com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get();
            var floor=com.juicyslew.moonstation14.block.ModBlocks.STATION_FLOOR.get();
            for(var block:java.util.List.of(wall,floor)) {
                for(var damage:java.util.List.of(java.util.Map.of("blunt",5f),java.util.Map.of("blunt",8f),
                        java.util.Map.of("slash",10f),java.util.Map.of("blunt",10f))) {
                    var expected=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT;
                    if(com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(block,damage,construction,contacts)!=expected)
                        throw new GameTestAssertException("composed damage modifiers failed to absorb " + damage + " on " + block);
                }
                if(com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(block,java.util.Map.of("blunt",11f),construction,contacts)
                        !=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.UNSUPPORTED_POSITIVE_DAMAGE)
                    throw new GameTestAssertException("positive post-modifier damage was incorrectly admitted for " + block);
                if(com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(block,
                        new com.juicyslew.moonstation14.ms14.combat.MeleeWeaponData(java.util.Map.of("blunt",5f),1,1.5f,false,false,1,false),construction,contacts)
                        !=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT)
                    throw new GameTestAssertException("weapon resolver overload diverged for " + block);
            }
            helper.succeed();
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void floorLightAndAirlockWideAreAuthenticatedIneffectiveContacts(GameTestHelper helper) {
        runStationContact(helper, com.juicyslew.moonstation14.block.ModBlocks.STATION_FLOOR.get(),
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, "melee-floor-light");
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void closedAirlockWideIsAuthenticatedIneffectiveContact(GameTestHelper helper) {
        runStationContact(helper, com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get(),
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, "melee-airlock-wide");
    }

    private static void runStationContact(GameTestHelper helper, net.minecraft.world.level.block.Block block,
            com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode, String id) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), id);
        helper.testInfo.addListener(cleanup(fixture));
        helper.runAtTickTime(2, () -> {
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); var origin=positionBody(helper,body);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var selection=MeleeWeaponResolver.resolve(body).orElseThrow();
                var pos=origin.offset(0, block == com.juicyslew.moonstation14.block.ModBlocks.STATION_FLOOR.get() ? 0 : 1, 1); var state=block.defaultBlockState();
                helper.getLevel().setBlock(pos,state,3);
                if (block instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock)
                    helper.getLevel().setBlock(pos.above(), state.setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
                var point=new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ());
                var nomination=new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos,net.minecraft.core.Direction.NORTH,point,net.minecraft.world.level.block.Block.getId(state));
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(),new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(),1,true));
                var result=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),request(active,selection,1,mode,java.util.List.of(nomination)));
                if(result.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT||!result.cooldownConsumed()
                        ||helper.getLevel().getBlockState(pos)!=state)
                    throw new GameTestAssertException("authenticated station contact was not admitted without block damage: "+result.status());
                // A consumed attack cooldown is deliberately preserved; negative probes are
                // separate legal attempts, not accidentally rejected requests in the same tick.
                long staleDeadline=com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body,MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow();
                helper.runAfterDelay((int)Math.max(0, staleDeadline-helper.getLevel().getGameTime()), () -> {
                    try {
                        var stale=new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos,net.minecraft.core.Direction.NORTH,point,net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
                        var staleResult=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),request(active,selection,2,mode,java.util.List.of(stale)));
                        if(staleResult.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS || !staleResult.cooldownConsumed())
                            throw new GameTestAssertException("stale-state nomination was not an admitted miss: " + staleResult.status());
                        long farDeadline=com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body,MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow();
                        helper.runAfterDelay((int)Math.max(0, farDeadline-helper.getLevel().getGameTime()), () -> {
                            try {
                                var farPos=pos.offset(5,0,0); var far=new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(farPos,net.minecraft.core.Direction.NORTH,new net.minecraft.world.phys.Vec3(farPos.getX()+.5,farPos.getY()+.5,farPos.getZ()),net.minecraft.world.level.block.Block.getId(state));
                                var farResult=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),request(active,selection,3,mode,java.util.List.of(far)));
                                if(farResult.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS || !farResult.cooldownConsumed())
                                    throw new GameTestAssertException("out-of-range nomination was not an admitted miss: " + farResult.status());
                                helper.succeed();
                            } catch(RuntimeException|Error e){fixture.close();throw e;}
                        });
                    } catch(RuntimeException|Error e){fixture.close();throw e;}
                });
            } catch(RuntimeException|Error e){fixture.close();throw e;}
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void lightStructuralContactAdmissionAndRejection(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-structural-light");
        var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        fixture.observeOutbound(packet -> { if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) sounds.add(p); });
        helper.testInfo.addListener(cleanup(fixture));
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); var origin = positionBody(helper, body);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var selection = MeleeWeaponResolver.resolve(body).orElseThrow();
                var wall = com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState();
                var pos = origin.offset(0, 1, 1); helper.getLevel().setBlock(pos, wall, 3);
                var point = new net.minecraft.world.phys.Vec3(pos.getX()+.5, pos.getY()+.5, pos.getZ());
                var blockId = net.minecraft.world.level.block.Block.getId(wall);
                var nomination = new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos, net.minecraft.core.Direction.NORTH, point, blockId);
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                var hit = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), request(active, selection, 1, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, java.util.List.of(nomination)));
                if (hit.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !hit.cooldownConsumed() || helper.getLevel().getBlockState(pos)!=wall)
                    throw new GameTestAssertException("adjacent steel wall contact failed admission or changed state: "+hit.status());
                var deadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow();
                var again = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), request(active, selection, 2, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, java.util.List.of(nomination)));
                if (again.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED || again.cooldownConsumed() || deadline!=com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow())
                    throw new GameTestAssertException("second structural request did not preserve consumed cooldown");
                var hitCue = com.juicyslew.moonstation14.ms14.combat.MeleeSoundPolicy.outcome(selection.profile().weapon().audio(), null, false);
                var hitSounds = sounds.stream().filter(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()).equals(hitCue.event())).toList();
                if (hitSounds.size() != 1 || Math.abs(hitSounds.getFirst().getX() - point.x()) > 1.5 || Math.abs(hitSounds.getFirst().getY() - point.y()) > 1.5 || Math.abs(hitSounds.getFirst().getZ() - point.z()) > 1.5)
                    throw new GameTestAssertException("structural contact did not emit exactly one zero-damage cue at the server contact point");
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void wideMixedEntityAndStructuralContactsUseOneAttack(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-structural-wide");
        var refs = new net.minecraft.world.entity.Entity[2];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) { try { for (var e:refs) if(e!=null&&!e.isRemoved())e.discard(); fixture.close(); } catch(Throwable t) { if(i.getError()!=null)i.getError().addSuppressed(t);else i.fail(t); } }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); var origin=positionBody(helper,body);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var selection=MeleeWeaponResolver.resolve(body).orElseThrow();
                var animal=net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel()); if(animal==null)throw new GameTestAssertException("target create failed"); refs[0]=animal; animal.setNoAi(true); animal.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.9); helper.getLevel().addFreshEntity(animal);
                var pos=origin.offset(0,1,1); var wall=com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(); helper.getLevel().setBlock(pos,wall,3);
                var nomination=new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos,net.minecraft.core.Direction.NORTH,new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()),net.minecraft.world.level.block.Block.getId(wall));
                float before=typed(animal,"blunt");
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(),new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(),1,true));
                var req=request(active,selection,1,com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,java.util.List.of(new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Entity(animal.getUUID()),nomination));
                var result=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),req);
                if(result.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT||!result.cooldownConsumed()||Math.abs(typed(animal,"blunt")-before-5f)>.011f||helper.getLevel().getBlockState(pos)!=wall)throw new GameTestAssertException("mixed wide contacts did not produce one entity hit and retain wall");
                helper.succeed();
            } catch(RuntimeException|Error e){fixture.close();throw e;}
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_structural", timeoutTicks=100)
    public static void wideStructuralContactWithoutEntityUsesZeroDamageFeedback(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-structural-wide-only");
        var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        fixture.observeOutbound(packet -> { if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) sounds.add(p); });
        helper.testInfo.addListener(cleanup(fixture));
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); var origin = positionBody(helper, body);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var selection = MeleeWeaponResolver.resolve(body).orElseThrow();
                var pos = origin.offset(0, 1, 1);
                var wall = com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState();
                helper.getLevel().setBlock(pos, wall, 3);
                var point = new net.minecraft.world.phys.Vec3(pos.getX()+.5, pos.getY()+.5, pos.getZ());
                var nomination = new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block(pos, net.minecraft.core.Direction.NORTH, point, net.minecraft.world.level.block.Block.getId(wall));
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                sounds.clear();
                var result = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), request(active, selection, 1, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, java.util.List.of(nomination)));
                var hitCue = com.juicyslew.moonstation14.ms14.combat.MeleeSoundPolicy.outcome(selection.profile().weapon().audio(), null, false);
                var missCue = selection.profile().weapon().audio().swing();
                var hitSounds = sounds.stream().filter(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()).equals(hitCue.event())).toList();
                boolean hasMiss = missCue != null && sounds.stream().anyMatch(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()).equals(missCue.event()));
                if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !result.cooldownConsumed()
                        || helper.getLevel().getBlockState(pos) != wall || hitSounds.size() != 1 || hasMiss
                        || Math.abs(hitSounds.getFirst().getX()-point.x()) > 1.5 || Math.abs(hitSounds.getFirst().getY()-point.y()) > 1.5 || Math.abs(hitSounds.getFirst().getZ()-point.z()) > 1.5)
                    throw new GameTestAssertException("block-only wide contact did not emit one zero-damage cue at contact point: " + result.status());
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    private static com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest request(RegisteredPlayerReadyDriver.Active active, MeleeWeaponResolver.Selection s,long seq,com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode,java.util.List<com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination> nominations) {
          return new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(),seq,s.kind(),s.handRevision(),s.activeHand(),s.itemToken().orElse(""),mode,nominations,false);
    }
    private static float typed(net.minecraft.world.entity.LivingEntity e,String type){var d=e.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());return d==null?0f:d.getMap().getOrDefault(type,0f);}
    private static net.minecraft.core.BlockPos positionBody(GameTestHelper helper,net.minecraft.world.entity.LivingEntity body){var p=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)helper.getLevel().setBlock(p.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);body.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);body.tick();return p;}
    private static net.minecraft.gametest.framework.GameTestListener cleanup(RegisteredServerPlayerFixture fixture) {
        return new net.minecraft.gametest.framework.GameTestListener() { private void clean(net.minecraft.gametest.framework.GameTestInfo i){try{fixture.close();}catch(Throwable t){if(i.getError()!=null)i.getError().addSuppressed(t);else i.fail(t);}} public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean(o);} };
    }
}
