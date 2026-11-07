package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.combat.*;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native integration coverage for the packaged Mk58 gun/profile composition. */
@GameTestTags({"guns", "gun-fire", "owned-combat"})
@PrefixGameTestTemplate(false)
public final class RegisteredGunsContentGameTests {
    private RegisteredGunsContentGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_ground_controls", timeoutTicks=100)
    @GameTestTags({"guns", "gun-fire"})
    public static void registeredMk58GroundBoltControlQueriesAndDispatches(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-mk58-ground-bolt");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String hand=hands.activeHand();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get()));
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun);
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose))throw new GameTestAssertException("Mk58 world fixture spawn failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var token=hands.token(hand).orElse(null);
            if(token!=null || !hands.stackCopy(hand).orElse(net.minecraft.world.item.ItemStack.EMPTY).isEmpty())
                throw new GameTestAssertException("ground Mk58 fixture unexpectedly reserved the actor's active hand");
            var query=new BodyTargetActionQuery(active.epoch(),1,hands.revision(),hand,token,loose.getUUID());
            var snapshot=BodyTargetActionService.query(fixture.player(),query);
            if(snapshot==null)throw new GameTestAssertException("authenticated ground Mk58 target query was denied");
            if(snapshot.actions().stream().noneMatch(row->row.id()==BodyTargetActionSnapshot.ActionId.GUN_TOGGLE_BOLT))
                throw new GameTestAssertException("ground Mk58 query omitted bolt control row");
            if(snapshot.actions().isEmpty())throw new GameTestAssertException("ground Mk58 query returned no control rows");
            var before=loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(before==null)throw new GameTestAssertException("Mk58 has no initialized bolt state");
            var request=new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_TOGGLE_BOLT,hand,active.epoch(),1,hands.revision(),loose.getUUID(),token);
            var result=BodyHandRequestService.apply(fixture.player(),request);
            if(!result.accepted())throw new GameTestAssertException("authenticated ground bolt toggle denied: "+result.reason());
            var same=h.getLevel().getEntity(loose.getUUID());
            if(same!=loose||!(same instanceof com.juicyslew.moonstation14.entities.LooseItemEntity))
                throw new GameTestAssertException("ground bolt toggle picked up or replaced the target entity");
            var after=loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(after==null||after.boltClosed()==before.boltClosed())throw new GameTestAssertException("ground bolt toggle did not change bolt state");
            var handsAfter=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(handsAfter==null || handsAfter.token(hand).isPresent()
                    || !handsAfter.stackCopy(hand).orElse(net.minecraft.world.item.ItemStack.EMPTY).isEmpty())
                throw new GameTestAssertException("ground bolt toggle unexpectedly granted or reserved the active hand");
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_gun_prone_aim", timeoutTicks=100)
    @GameTestTags({"guns", "gun-fire"})
    public static void registeredWieldedLecterKeepsSupportedAimWhenProneAndStationary(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-lecter-prone-aim");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)); body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            body.setYRot(90); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String hand=hands.activeHand();
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_RIFLE_LECTER.get())));
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException("Lecter authenticated pickup failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(!com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem.toggle(fixture.player(),hand,hands.token(hand).orElseThrow()))throw new GameTestAssertException("Lecter wield acquisition failed");
            if(CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true)).status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("combat mode activation failed");
            if(!com.juicyslew.moonstation14.ms14.standing.PostureSystem.voluntaryToggle(body))throw new GameTestAssertException("voluntary prone transition failed");
            if(!com.juicyslew.moonstation14.ms14.standing.PostureSystem.voluntaryRoll(body))throw new GameTestAssertException("supine-to-face-down roll failed");
            h.runAfterDelay(2,()->{try {
                var posture=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get()).data();
                if(!posture.down()||posture.faceUp())throw new GameTestAssertException("expected down, face-down posture");
                if(!posture.supportedGunAim())throw new GameTestAssertException("stationary wielded Lecter did not retain supported prone aim");
                if(Math.abs(net.minecraft.util.Mth.wrapDegrees(posture.movementFacingYaw()-90f))>.01f)throw new GameTestAssertException("stationary prone aim lost its 90-degree facing");
                if(Float.compare(body.getEyeHeight(),com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry.EYE_HEIGHT)!=0)throw new GameTestAssertException("supported prone eye height differs from shared geometry");
                if(Math.abs(body.getBbHeight()-com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry.HEIGHT)>.001)throw new GameTestAssertException("supported prone physical height differs from shared geometry");
                if(Float.compare(body.getEyeHeight(),body.getDimensions(net.minecraft.world.entity.Pose.STANDING).eyeHeight())==0)throw new GameTestAssertException("supported prone eye height remained standing height");
                var endHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),2,false));
                com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem.toggle(fixture.player(),hand,endHands.token(hand).orElseThrow());
                h.runAfterDelay(1,()->{try {
                    var ordinary=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get()).data();
                    if(ordinary.supportedGunAim())throw new GameTestAssertException("supported aim remained after combat exit and unwield");
                    h.succeed();
                }catch(RuntimeException|Error e){cleanup.run();throw e;}});
            }catch(RuntimeException|Error e){cleanup.run();throw e;}});
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); }catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_mk58", timeoutTicks=100)
    public static void droppingWieldedLecterPublishesBeforeDeferredCleanup(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-wield-drop");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String source=hands.activeHand();
            String support=hands.handIds().stream().filter(id->!id.equals(source)).findFirst().orElseThrow();
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_RIFLE_LECTER.get()));
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),source,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("Lecter pickup failed");
            var current=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String token=current.token(source).orElseThrow();
            if(!com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem.toggle(fixture.player(),source,token))
                throw new GameTestAssertException("Lecter wield acquisition failed");
            var wielded=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(wielded.stackCopy(source).isEmpty()||wielded.reservation(support).isEmpty())throw new GameTestAssertException("wield state did not reserve support hand");
            if(BodyHandItemTransfer.drop(fixture.player(),source,token,wielded.revision())!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("wielded Lecter drop failed before deferred cleanup");
            int drops=0;
            for(var entity:h.getLevel().getEntities().getAll())if(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item&&item.getItem().is(com.juicyslew.moonstation14.item.ModItems.WEAPON_RIFLE_LECTER.get())){drops++;owned.add(entity);}
            if(drops!=1)throw new GameTestAssertException("expected exactly one world Lecter immediately after drop, got "+drops);
            com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem.tick(h.getLevel());
            var cleaned=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(body.hasData(com.juicyslew.moonstation14.component.ModDataAttachments.WIELD_STATE.get())||cleaned.reservation(support).isPresent())
                throw new GameTestAssertException("deferred cleanup did not release wield lease");
            if(cleaned.reservation(source).isPresent())throw new GameTestAssertException("wield cleanup changed unrelated source reservation");
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}});
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_mk58", timeoutTicks=100)
    public static void registeredMk58RacksAndFiresWithResolvedProfiles(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-mk58-content");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String hand=hands.activeHand();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get()));
            var initialized=gun.get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            var initialMagazine=initialized==null?null:initialized.magazine().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(initialized==null||initialized.magazine().isEmpty()||initialMagazine==null||initialMagazine.rounds().size()!=10||!initialized.chamber().isEmpty()||initialized.boltClosed())
                throw new GameTestAssertException("resolved Mk58 factory state was not a 10-round magazine, empty-chamber, open-bolt");
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun);
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("authenticated Mk58 pickup failed");
            var current=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var token=current.token(hand).orElseThrow();
            var rack=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),1,current.revision(),null,token));
            if(!rack.accepted())throw new GameTestAssertException("authenticated Mk58 rack request denied: "+rack.reason());
            var selected=GunWeaponResolver.resolve(body).orElseThrow(()->new GameTestAssertException("Mk58 selection unresolved after rack"));
            var racked=GunWeaponResolver.readOwned(body,selected).orElseThrow().stack().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            var rackedMagazine=racked==null?null:racked.magazine().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(racked==null||rackedMagazine==null||rackedMagazine.rounds().size()!=9||racked.chamber().isEmpty()||!racked.boltClosed())throw new GameTestAssertException("Mk58 rack did not chamber one round and close the bolt");
            var mode=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(mode.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("authenticated combat-mode request failed");
            var target=EntityType.VILLAGER.create(h.getLevel()); if(target==null)throw new GameTestAssertException("target creation failed");
            owned.add(target); target.setNoAi(true); target.setPos(body.getX(),body.getY(),body.getZ()+3); h.getLevel().addFreshEntity(target);
            var before=new HashSet<UUID>();for(var e:h.getLevel().getEntities().getAll())before.add(e.getUUID());
            var casingEvents=new ArrayList<com.juicyslew.moonstation14.ms14.combat.SpentCasingPayload>();
            fixture.observeOutbound(packet->{if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    &&custom.payload() instanceof com.juicyslew.moonstation14.ms14.combat.SpentCasingPayload casing)casingEvents.add(casing);});
            selected=GunWeaponResolver.resolve(body).orElseThrow();
            var reply=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selected.handRevision(),hand,selected.itemToken(),target.position().add(0,target.getBbHeight()*.55,0)));
            if(reply.status()!=GunFireSystem.Status.FIRED||!reply.consumedSource())throw new GameTestAssertException("authenticated Mk58 press did not fire");
            var afterHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var after=afterHands.stackCopy(hand).orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            var afterMagazine=after==null?null:after.magazine().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(after==null||afterMagazine==null||afterMagazine.rounds().size()!=8||after.chamber().isEmpty()||!after.boltClosed())throw new GameTestAssertException("Mk58 fire did not consume one round and auto-cycle the next into the closed chamber");
            int projectiles=0;for(var e:h.getLevel().getEntities().getAll())if(e instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity&&!before.contains(e.getUUID())){projectiles++;owned.add(e);if(!((com.juicyslew.moonstation14.entities.TypedProjectileEntity)e).getOwner().getUUID().equals(body.getUUID()))throw new GameTestAssertException("projectile shooter was not the controlled body");}
            if(projectiles!=1)throw new GameTestAssertException("expected one published Mk58 projectile");
            var spentCases=new ArrayList<com.juicyslew.moonstation14.entities.LooseItemEntity>();
            for(var e:h.getLevel().getEntities().getAll())if(!before.contains(e.getUUID())&&e instanceof com.juicyslew.moonstation14.entities.LooseItemEntity l
                    &&l.getItem().is(com.juicyslew.moonstation14.item.ModItems.CARTRIDGE_PISTOL.get())
                    &&Boolean.TRUE.equals(l.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))){spentCases.add(l);owned.add(e);}
            if(spentCases.size()!=1)throw new GameTestAssertException("expected exactly one persistent spent case, got "+spentCases.size());
            var spentCase=spentCases.get(0);
            var spentProfile=spentCase.profile();
            if(!spentProfile.equals(com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(spentCase.getItem(),false))
                    ||spentProfile.physicalWidth()>.09f||spentProfile.physicalHeight()>.03f
                    ||!"flat_sprite".equals(spentProfile.renderKind())
                    ||!net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","cartridge_pistol").equals(
                    spentCase.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.LOOSE_OBJECT_PROFILE.get())))
                throw new GameTestAssertException("published spent case did not retain resolved tiny cartridge profile: "+spentProfile);
            if(spentCase.getAge()>=0)throw new GameTestAssertException("spent case lacks the unlimited native item lifetime policy");
            spentCase.setNoGravity(true); spentCase.setPos(body.getX()+.4,body.getY(),body.getZ()+.4); spentCase.setDeltaMovement(Vec3.ZERO);
            for(int i=0;i<220;i++)spentCase.tick();
            if(spentCase.isRemoved()||!Boolean.TRUE.equals(spentCase.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())))
                throw new GameTestAssertException("spent case did not persist through ticks beyond the observer particle lifetime");
            var emptyHand=hands.handIds().stream().filter(id->!id.equals(hand)).findFirst().orElseThrow();
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(BodyHandItemTransfer.pickupSelected(fixture.player(),spentCase.getUUID(),emptyHand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("authenticated pickup of the spent case failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var heldSpent=hands.stackCopy(emptyHand).orElseThrow();
            if(!heldSpent.is(com.juicyslew.moonstation14.item.ModItems.CARTRIDGE_PISTOL.get())
                    ||!Boolean.TRUE.equals(heldSpent.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())))
                throw new GameTestAssertException("picked-up case was converted into live ammunition");
            var spentToken=hands.token(emptyHand).orElseThrow();
            if(BodyHandItemTransfer.drop(fixture.player(),emptyHand,spentToken,hands.revision())!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("authenticated spent case drop failed");
            var droppedSpent=java.util.stream.StreamSupport.stream(h.getLevel().getEntities().getAll().spliterator(),false).filter(e->!before.contains(e.getUUID())
                    &&e instanceof com.juicyslew.moonstation14.entities.LooseItemEntity l
                    &&l.getItem().is(com.juicyslew.moonstation14.item.ModItems.CARTRIDGE_PISTOL.get())
                    &&Boolean.TRUE.equals(l.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))).count();
            if(droppedSpent!=1)throw new GameTestAssertException("spent case did not round-trip back to the world without becoming live ammunition");
            if(casingEvents.size()!=0)throw new GameTestAssertException("persistent spent case must not be duplicated by casing presentation events, got "+casingEvents.size());
            var beforeRack=new HashSet<UUID>();for(var e:h.getLevel().getEntities().getAll())beforeRack.add(e.getUUID());
            var rackHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var manualRack=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),2,rackHands.revision(),null,rackHands.token(hand).orElseThrow()));
            if(!manualRack.accepted())throw new GameTestAssertException("post-shot manual rack denied: "+manualRack.reason());
            int recoverableLiveRounds=0;
            for(var e:h.getLevel().getEntities().getAll())if(!beforeRack.contains(e.getUUID())&&e instanceof com.juicyslew.moonstation14.entities.LooseItemEntity l
                    &&l.getItem().is(com.juicyslew.moonstation14.item.ModItems.CARTRIDGE_PISTOL.get())
                    &&!Boolean.TRUE.equals(l.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))){recoverableLiveRounds++;owned.add(e);}
            if(recoverableLiveRounds!=1)throw new GameTestAssertException("manual rack must publish one real recoverable live round, got "+recoverableLiveRounds);
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); }
        catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_cadence", timeoutTicks=100)
    public static void registeredMosinRejectsHeldRepeatDuringCooldown(GameTestHelper h) {
        registeredCadence(h, com.juicyslew.moonstation14.item.ModItems.WEAPON_SNIPER_MOSIN.get(), true, "mosin", .75f, false);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_cadence", timeoutTicks=100)
    public static void registeredInspectorRejectsHeldRepeatDuringCooldown(GameTestHelper h) {
        registeredCadence(h, com.juicyslew.moonstation14.item.ModItems.WEAPON_REVOLVER_INSPECTOR.get(), false, "inspector", 1.5f, false);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_cadence", timeoutTicks=100)
    public static void registeredInspectorFiresWhileProne(GameTestHelper h) {
        registeredCadence(h, com.juicyslew.moonstation14.item.ModItems.WEAPON_REVOLVER_INSPECTOR.get(), false, "inspector-prone", 1.5f, true);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_cadence", timeoutTicks=100)
    public static void registeredMosinFiresWhileProne(GameTestHelper h) {
        registeredCadence(h, com.juicyslew.moonstation14.item.ModItems.WEAPON_SNIPER_MOSIN.get(), true, "mosin-prone", .75f, true);
    }

    private static void registeredCadence(GameTestHelper h, net.minecraft.world.item.Item item, boolean requiresWield, String name, float expectedRate, boolean prone) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-"+name+"-cadence");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)); body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()); String hand=hands.activeHand();
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(item)));
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException(name+" pickup failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(requiresWield) {
                var wield=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_TOGGLE_WIELD,hand,active.epoch(),1,hands.revision(),null,hands.token(hand).orElseThrow()));
                if(!wield.accepted())throw new GameTestAssertException(name+" wield denied: "+wield.reason());
            }
            var mode=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(mode.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException(name+" combat mode denied");
            if(prone) {
                if(!com.juicyslew.moonstation14.ms14.standing.PostureSystem.voluntaryToggle(body)
                        ||!com.juicyslew.moonstation14.ms14.standing.PostureSystem.voluntaryRoll(body)
                        ||!com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(body))
                    throw new GameTestAssertException(name+" conscious body failed voluntary prone/face-up transition");
            }
            var selected=GunWeaponResolver.resolve(body).orElseThrow();
            if(Float.compare(selected.profile().gun().fireRate(),expectedRate)!=0)throw new GameTestAssertException(name+" resolved unexpected fire rate: "+selected.profile().gun().fireRate());
            var before=new HashSet<UUID>();for(var e:h.getLevel().getEntities().getAll())before.add(e.getUUID());
            var sounds=new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
            fixture.observeOutbound(packet->{if(packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound)sounds.add(sound);});
            var aim=body.getEyePosition().add(body.getLookAngle().scale(20));
            var first=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selected.handRevision(),hand,selected.itemToken(),aim));
            if(first.status()!=GunFireSystem.Status.FIRED||!first.consumedSource())throw new GameTestAssertException(name+" initial shot did not fire authoritatively");
            var initialStack=GunWeaponResolver.readOwned(body,GunWeaponResolver.resolve(body).orElseThrow()).orElseThrow().stack();
            long firstFireTick=h.getLevel().getGameTime();
            long expectedInterval=(long)Math.ceil(20d/selected.profile().gun().fireRate());
            long committedDeadline=initialStack.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL).nextFireTick();
            if(first.remainingTicks()!=expectedInterval||committedDeadline!=firstFireTick+expectedInterval)
                throw new GameTestAssertException(name+" committed cadence mismatch: replyRemaining="+first.remainingTicks()+", interval="+expectedInterval+", committedDeadline="+committedDeadline+", serverTick="+firstFireTick);
            var shotSet=new HashSet<UUID>(); int count=0;
            for(var e:h.getLevel().getEntities().getAll())if(e instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity p&&!before.contains(e.getUUID())){count++;shotSet.add(e.getUUID());owned.add(e);if(p.getOwner()!=body)throw new GameTestAssertException(name+" projectile owner mismatch");}
            if(count!=1)throw new GameTestAssertException(name+" expected one first-shot projectile, got "+count);
            // Exercise the production server-side stop service; a native server fixture cannot
            // send a serverbound packet through PacketDistributor.sendToServer.
            GunShotRequestService.stop(fixture.player(),new GunStopShootRequest(active.epoch(),1,hand,selected.itemToken()));
            selected=GunWeaponResolver.resolve(body).orElseThrow();
            var second=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),2,selected.handRevision(),hand,selected.itemToken(),aim));
            if(second.status()!=GunFireSystem.Status.COOLDOWN||second.consumedSource())throw new GameTestAssertException(name+" repeated held press was not rejected as unconsumed cooldown: "+second.status());
            var finalStack=GunWeaponResolver.readOwned(body,GunWeaponResolver.resolve(body).orElseThrow()).orElseThrow().stack();
            if(!net.minecraft.world.item.ItemStack.matches(initialStack,finalStack))throw new GameTestAssertException(name+" cooldown attempt changed gun components");
            int finalCount=0;for(var e:h.getLevel().getEntities().getAll())if(e instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity&&!before.contains(e.getUUID()))finalCount++;
            if(finalCount!=1)throw new GameTestAssertException(name+" cooldown attempt emitted another projectile");
            long now=h.getLevel().getGameTime();
            if(GunFirePolicy.nextFireTick(now-1,selected.profile().gun().fireRate()).orElseThrow()!=now-1+(long)Math.ceil(20d/selected.profile().gun().fireRate()))throw new GameTestAssertException(name+" fire policy cadence mismatch");
            var firedEvent=selected.profile().gun().gunshotSound().orElseThrow().event();
            if(sounds.stream().anyMatch(sound->sound.getSound().value().getLocation().equals(firedEvent)))throw new GameTestAssertException(name+" server echoed shot audio to initiating actor");
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_charger_use", timeoutTicks=100)
    public static void registeredBodyUsesChargerThroughOrdinaryBlockUse(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-body-charger-use");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        var chargerPos=new java.util.concurrent.atomic.AtomicReference<net.minecraft.core.BlockPos>();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();var pos=chargerPos.get();if(pos!=null&&h.getLevel().getBlockEntity(pos) instanceof WeaponCapacitorRechargerBlockEntity)h.getLevel().destroyBlock(pos,false);fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var level=h.getLevel(); var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,2,3));
            chargerPos.set(pos);
            level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            level.setBlock(pos,com.juicyslew.moonstation14.block.ModBlocks.WEAPON_CAPACITOR_RECHARGER.get().defaultBlockState(),3);
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()-1.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var machine=(WeaponCapacitorRechargerBlockEntity)level.getBlockEntity(pos);
            if(machine==null||machine.slot()==null)throw new GameTestAssertException("charger entity/slot was not constructed");
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()); String hand=hands.activeHand();
            var gun=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_ENERGY_SHOTGUN.get());
            gun.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(0));
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,body.getEyePosition().add(body.getLookAngle().scale(.7)),gun); loose.setNoGravity(true); owned.add(loose);
            if(!level.addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException("depleted shotgun pickup failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var held=hands.stackCopy(hand).orElseThrow(); var originalToken=hands.token(hand).orElseThrow();
            if(held.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge()!=0)throw new GameTestAssertException("test gun was not depleted");
            var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
            var insert=new BodyBlockUseRequest(active.epoch(),1,hands.revision(),hand,originalToken,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
            var result=com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess.dispatch(fixture.player(),insert);
            if(result!=BodyBlockUseResult.Outcome.PLACED)throw new GameTestAssertException("ordinary admitted USE did not insert depleted gun: "+result);
            var occupied=machine.slot().snapshot();
            if(occupied.token()==null||!occupied.token().value().equals(originalToken))throw new GameTestAssertException("charger replaced the inserted item identity");
            if(!occupied.stack().is(com.juicyslew.moonstation14.item.ModItems.WEAPON_ENERGY_SHOTGUN.get())||occupied.stack().get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge()!=0)throw new GameTestAssertException("charger did not preserve actual zero-charge item on insertion");
            var ledger=com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
            if(!ledger.locationOf(occupied.token()).orElseThrow().equals(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.MachineSlot(occupied.owner(),"charger_slot")))throw new GameTestAssertException("inserted identity was not claimed by charger slot");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(hands.stackCopy(hand).isPresent())throw new GameTestAssertException("successful insertion did not clear active hand");
            if(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess.dispatch(fixture.player(),insert)!=BodyBlockUseResult.Outcome.DENIED||machine.slot().snapshot().token()==null||!machine.slot().snapshot().token().equals(occupied.token()))throw new GameTestAssertException("stale repeated insertion duplicated or displaced machine-owned item");
            var eject=new BodyBlockUseRequest(active.epoch(),2,hands.revision(),hand,null,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
            result=com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess.dispatch(fixture.player(),eject);
            if(result!=BodyBlockUseResult.Outcome.HAND_CHANGED)throw new GameTestAssertException("empty-hand ordinary USE did not eject item: "+result);
            var returned=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow();
            var returnedToken=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).token(hand).orElseThrow();
            if(!returnedToken.equals(originalToken))throw new GameTestAssertException("ordinary ejection changed the item identity");
            if(!returned.is(com.juicyslew.moonstation14.item.ModItems.WEAPON_ENERGY_SHOTGUN.get())||returned.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge()!=0)throw new GameTestAssertException("ordinary eject failed to return exact depleted item");
            if(!machine.slot().snapshot().stack().isEmpty())throw new GameTestAssertException("charger slot remained occupied after eject");
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_mk58", timeoutTicks=100)
    public static void registeredRepeatedEmptyMk58RacksPublishEachSound(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-mk58-empty-racks");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)); body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()); String hand=hands.activeHand();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get()));
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun); loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException("authenticated Mk58 pickup failed");
            var cues=new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>(); fixture.observeOutbound(packet->{if(packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound)cues.add(sound);});
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var eject=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_MAGAZINE,hand,active.epoch(),1,hands.revision(),null,hands.token(hand).orElseThrow()));
            if(!eject.accepted())throw new GameTestAssertException("Mk58 magazine eject denied");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var close=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),2,hands.revision(),null,hands.token(hand).orElseThrow()));
            if(!close.accepted())throw new GameTestAssertException("Mk58 bolt close denied");
            var selected=GunWeaponResolver.resolve(body).orElseThrow(); var cue=selected.profile().magazineChamberProvider().orElseThrow().rackSound().orElseThrow();
            for(long sequence=3;sequence<=5;sequence++) {
                hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var result=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),sequence,hands.revision(),null,hands.token(hand).orElseThrow()));
                if(!result.accepted())throw new GameTestAssertException("empty Mk58 rack denied: "+result.reason());
            }
            var rackPackets=cues.stream().filter(p->p.getSound().value().getLocation().equals(cue.event())).toList();
            if(rackPackets.size()!=3)throw new GameTestAssertException("expected three controlled-player Mk58 rack cues, got "+rackPackets.size());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var denied=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),6,hands.revision(),null,"stale-mk58-token"));
            if(denied.accepted())throw new GameTestAssertException("stale Mk58 token accepted");
            if(cues.stream().filter(p->p.getSound().value().getLocation().equals(cue.event())).count()!=3)throw new GameTestAssertException("denied stale request published a rack cue");
            var combat=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(combat.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("combat-mode activation denied");
            var emptyMessages=new ArrayList<net.minecraft.network.protocol.game.ClientboundSystemChatPacket>();
            fixture.observeOutbound(packet->{if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat)emptyMessages.add(chat);});
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var emptyState=hands.stackCopy(hand).orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(emptyState==null||!emptyState.magazine().isEmpty()||!emptyState.chamber().isEmpty()||!emptyState.boltClosed())throw new GameTestAssertException("empty-shot prerequisite was not closed bolt with no magazine or chamber");
            var selection=GunWeaponResolver.resolve(body).orElseThrow();
            var emptyCue=selection.profile().gun().emptySound().orElseThrow();
            var shot=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selection.handRevision(),hand,selection.itemToken(),body.getEyePosition().add(0,0,10)));
            if(shot.status()!=GunFireSystem.Status.EMPTY||!shot.consumedSource())throw new GameTestAssertException("authenticated empty Mk58 press did not consume source normally: "+shot.status());
            if(!emptyMessages.isEmpty())throw new GameTestAssertException("server emitted actionbar/system-chat text for empty Mk58 shot");
            // The initiating client predicts this cue; the server deliberately excludes it from fanout.
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_mk58", timeoutTicks=100)
    public static void rawCreativeMk58MagazineInsertsThenRacksAndFires(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-mk58-raw-magazine");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)); body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String gunHand=hands.activeHand(), sourceHand=hands.handIds().stream().filter(id->!id.equals(gunHand)).findFirst().orElseThrow();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get()));
            var looseGun=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun); looseGun.setNoGravity(true); owned.add(looseGun);
            if(!h.getLevel().addFreshEntity(looseGun)||BodyHandItemTransfer.pickupSelected(fixture.player(),looseGun.getUUID(),gunHand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS) throw new GameTestAssertException("Mk58 pickup failed");
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var eject=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_MAGAZINE,gunHand,active.epoch(),1,hands.revision(),null,hands.token(gunHand).orElseThrow()));
            if(!eject.accepted())throw new GameTestAssertException("Mk58 starting magazine eject denied: "+eject.reason());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var oldMagazine=hands.stackCopy(sourceHand).orElseThrow(()->new GameTestAssertException("ejected magazine did not occupy free source hand"));
            if(!oldMagazine.is(com.juicyslew.moonstation14.item.ModItems.MAGAZINE_PISTOL.get()))throw new GameTestAssertException("ejected source hand did not contain the original magazine");
            var selectOld=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,sourceHand,active.epoch(),2,hands.revision(),null,null));
            if(!selectOld.accepted())throw new GameTestAssertException("ejected magazine selection denied: "+selectOld.reason());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var dropOld=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.DROP,sourceHand,active.epoch(),3,hands.revision(),null,hands.token(sourceHand).orElseThrow()));
            if(!dropOld.accepted())throw new GameTestAssertException("original magazine drop denied: "+dropOld.reason());
            var transition=dropOld.hostTransition();
            if(transition==null||transition.direction()!=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.HostTransition.Direction.DROP
                    ||!transition.hostToken().equals(hands.token(sourceHand).orElseThrow()))throw new GameTestAssertException("original magazine drop omitted its authenticated world-host transition");
            var worldEntity=h.getLevel().getEntity(transition.worldEntity());
            if(!(worldEntity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity oldMagazineWorld))throw new GameTestAssertException("drop transition UUID did not resolve to its loose magazine entity");
            owned.add(oldMagazineWorld);
            var droppedStack=oldMagazineWorld==null?null:oldMagazineWorld.getItem();
            boolean preserved=droppedStack!=null&&net.minecraft.world.item.ItemStack.isSameItem(oldMagazine,droppedStack)
                    &&oldMagazine.getCount()==droppedStack.getCount()
                    &&semanticStack(oldMagazine).equals(semanticStack(droppedStack));
            if(!preserved)throw new GameTestAssertException("dropped original magazine was not preserved in world");
            var droppedAmmo=droppedStack.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            var originalAmmo=oldMagazine.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(droppedAmmo==null||originalAmmo==null||droppedAmmo.rounds().size()!=10||originalAmmo.rounds().size()!=10)
                throw new GameTestAssertException("dropped original magazine lost its initialized ten-round contents");
            for(int i=0;i<10;i++)if(!semanticStack(originalAmmo.rounds().get(i)).equals(semanticStack(droppedAmmo.rounds().get(i))))
                throw new GameTestAssertException("dropped original magazine round identity/components changed at index "+i);
            // DROP is an ownership mutation and advances the live hand revision. Do not reuse
            // the pre-drop snapshot for the next authenticated pickup.
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(hands==null||hands.stackCopy(sourceHand).isPresent()||!sourceHand.equals(hands.activeHand())
                    ||hands.token(sourceHand).isPresent()||hands.token(gunHand).isEmpty())
                throw new GameTestAssertException("accepted original magazine drop left unexpected live hand state");
            var raw=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.MAGAZINE_PISTOL.get());
            raw.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("raw replacement"));
            if(raw.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get())!=null)throw new GameTestAssertException("creative magazine unexpectedly started initialized");
            var looseMag=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),raw); looseMag.setNoGravity(true); owned.add(looseMag);
            if(!h.getLevel().addFreshEntity(looseMag))throw new GameTestAssertException("raw replacement entity spawn failed");
            var pickup=BodyHandItemTransfer.pickupSelected(fixture.player(),looseMag.getUUID(),sourceHand,hands.revision(),ignored->{});
            if(pickup!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException("raw replacement pickup admission failed: "+pickup);
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var sourceToken=hands.token(sourceHand).orElseThrow(); var gunToken=hands.token(gunHand).orElseThrow();
            var rawHeld=hands.stackCopy(sourceHand).orElseThrow(()->new GameTestAssertException("raw replacement is not present in source hand"));
            if(rawHeld.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get())!=null)
                throw new GameTestAssertException("raw replacement initialized before read-only counter projection");
            var rawCounter=com.juicyslew.moonstation14.ms14.combat.GunAmmoCounterProjection.project(rawHeld,null,
                    com.juicyslew.moonstation14.ms14.combat.ModAmmoContainerProfiles.serverCatalog().asMap(),
                    com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.serverCatalog().asMap());
            if(!rawCounter.visible()||rawCounter.count()!=10||rawCounter.capacity()!=10
                    ||rawCounter.kind()!=com.juicyslew.moonstation14.ms14.combat.GunAmmoCounterProjection.Kind.MAGAZINE
                    ||!rawCounter.chambers().isEmpty())
                throw new GameTestAssertException("read-only standalone raw magazine projection was not 10/10 without chamber glyphs");
            var stillRaw=hands.stackCopy(sourceHand).orElseThrow();
            if(stillRaw.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get())!=null)
                throw new GameTestAssertException("read-only counter projection initialized the live raw magazine");
            var selectGun=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,gunHand,active.epoch(),4,hands.revision(),null,null));
            if(!selectGun.accepted())throw new GameTestAssertException("gun selection before explicit offhand drag denied: "+selectGun.reason());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var insert=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND,sourceHand,active.epoch(),5,hands.revision(),null,sourceToken,gunToken,-1,-1,-1,-1,-1,-1,null,null,gunHand,null));
            if(!insert.accepted())throw new GameTestAssertException("raw Mk58 magazine insertion denied: "+insert.reason());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(hands.stackCopy(sourceHand).isPresent())throw new GameTestAssertException("inserted raw source was not consumed");
            if(!gunHand.equals(hands.activeHand()))throw new GameTestAssertException("explicit offhand drag changed active gun selection");
            var insertedGun=hands.stackCopy(gunHand).orElseThrow(); var insertedState=insertedGun.get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            var nested=insertedState==null?null:insertedState.magazine(); var nestedAmmo=nested==null?null:nested.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(nested==null||!nested.is(com.juicyslew.moonstation14.item.ModItems.MAGAZINE_PISTOL.get())||nestedAmmo==null||nestedAmmo.rounds().size()!=10||!net.minecraft.network.chat.Component.literal("raw replacement").equals(nested.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)))throw new GameTestAssertException("inserted raw magazine did not materialize resolved 10-round state and preserve identity/name");
            var rack=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,gunHand,active.epoch(),6,hands.revision(),null,hands.token(gunHand).orElseThrow()));
            if(!rack.accepted())throw new GameTestAssertException("replacement magazine rack denied: "+rack.reason());
            hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var racked=hands.stackCopy(gunHand).orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(racked.magazine().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().size()!=9||racked.chamber().isEmpty()||!racked.boltClosed())throw new GameTestAssertException("rack did not debit one actual nested round");
            if(!net.minecraft.network.chat.Component.literal("raw replacement").equals(racked.magazine().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)))throw new GameTestAssertException("rack lost magazine custom name");
            var mode=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(mode.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("combat mode activation denied");
            var selected=GunWeaponResolver.resolve(body).orElseThrow(()->new GameTestAssertException("inserted Mk58 not selected"));
            var reply=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selected.handRevision(),gunHand,selected.itemToken(),body.getEyePosition().add(0,0,12)));
            if(reply.status()!=GunFireSystem.Status.FIRED||!reply.consumedSource())throw new GameTestAssertException("inserted-magazine Mk58 shot denied");
            var fired=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(gunHand).orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(fired.magazine().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().size()!=8||fired.chamber().isEmpty()||!fired.boltClosed())throw new GameTestAssertException("shot did not debit nested rounds or retain auto-cycled chamber");
            for(Entity entity:h.getLevel().getEntities().getAll())
                if(entity instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity projectile&&projectile.getOwner()==body)owned.add(entity);
            h.succeed();
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    private static String semanticStack(net.minecraft.world.item.ItemStack stack) {
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        return net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops,stack).getOrThrow().toString();
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_energy", timeoutTicks=100)
    public static void registeredEnergyShotgunCyclesBatteryModeAndPublishesFanout(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-energy-shotgun");
        var victimFixture=RegisteredServerPlayerFixture.register(h.getLevel(),"guns-victim");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();victimFixture.close();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var victimActive=RegisteredPlayerReadyDriver.readyAndQuery(victimFixture); var victim=victimActive.body(); owned.add(victim);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            victim.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5); victim.setDeltaMovement(Vec3.ZERO); if(victim instanceof net.minecraft.world.entity.Mob mobVictim) mobVictim.setNoAi(true); victim.tick();
            if(com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(victim).flatMap(c->c.component(com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent.class)).isEmpty())
                throw new GameTestAssertException("registered human shotgun victim has no resolved stamina capability");
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String hand=hands.activeHand(), support=hands.handIds().stream().filter(id->!id.equals(hand)).findFirst().orElseThrow();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_ENERGY_SHOTGUN.get()));
            var battery=gun.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            var runtime=gun.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
            if(battery==null||battery.charge()!=480||runtime.batteryModeIndex()!=0)throw new GameTestAssertException("energy shotgun factory state was not charge 480/mode 0");
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun); loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)throw new GameTestAssertException("authenticated energy shotgun pickup failed");
            var current=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()); String token=current.token(hand).orElseThrow();
            var wield=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),1,current.revision(),null,token));
            if(!wield.accepted())throw new GameTestAssertException("energy shotgun wield request denied: "+wield.reason());
            current=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(current.stackCopy(hand).isEmpty()||current.reservation(support).isEmpty())throw new GameTestAssertException("energy shotgun did not wield/reserve offhand");
            var mode=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,hand,active.epoch(),2,current.revision(),null,token));
            if(!mode.accepted())throw new GameTestAssertException("battery mode USE_IN_HAND denied: "+mode.reason());
            var selected=GunWeaponResolver.resolve(body).orElseThrow(()->new GameTestAssertException("shotgun selection unresolved"));
            var selectedState=GunWeaponResolver.readOwned(body,selected).orElseThrow().stack();
            runtime=selectedState.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
            if(runtime.batteryModeIndex()!=1)throw new GameTestAssertException("second USE_IN_HAND did not select battery mode 1");
            var combat=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(combat.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("authenticated energy shotgun combat-mode request failed: "+combat.status());
            var before=new HashSet<UUID>();for(var e:h.getLevel().getEntities().getAll())before.add(e.getUUID());
            var healthBefore=Map.copyOf(com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(victim));
            float staminaBefore=com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.state(victim).damage();
            var reply=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selected.handRevision(),hand,selected.itemToken(),body.getEyePosition().add(0,0,10)));
            if(reply.status()!=GunFireSystem.Status.FIRED||!reply.consumedSource())throw new GameTestAssertException("authenticated energy shotgun press did not fire: status="+reply.status()+", consumed="+reply.consumedSource()+", remaining="+reply.remainingTicks()+", handRevision="+reply.handRevision()+", selectedRevision="+selected.handRevision()+", mode="+runtime.batteryModeIndex()+", tick="+h.getLevel().getGameTime());
            var after=GunWeaponResolver.readOwned(body,GunWeaponResolver.resolve(body).orElseThrow()).orElseThrow().stack();
            battery=after.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            runtime=after.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
            if(battery==null||battery.charge()!=432||runtime.batteryModeIndex()!=1)throw new GameTestAssertException("shot did not consume exactly 48 charge or preserve mode 1");
            var shotProjectiles=new HashSet<UUID>(); int projectiles=0;for(var e:h.getLevel().getEntities().getAll())if(e instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity projectile&&!before.contains(e.getUUID())){projectiles++;owned.add(e);shotProjectiles.add(e.getUUID());if(projectile.getOwner()!=body)throw new GameTestAssertException("projectile owner was not controlled body");}
            if(projectiles!=3)throw new GameTestAssertException("expected exactly three published energy shotgun projectiles, got "+projectiles);
            h.runAfterDelay(4,()->{try {
                int stillPublished=0;
                for(var e:h.getLevel().getEntities().getAll()) if(e instanceof com.juicyslew.moonstation14.entities.TypedProjectileEntity p&&shotProjectiles.contains(e.getUUID())&&p.getOwner()==body) stillPublished++;
                if(stillPublished==3)throw new GameTestAssertException("all three owned shot projectiles remained published without resolving against the positioned victim");
                var healthAfter=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(victim);
                if(!healthAfter.equals(healthBefore))throw new GameTestAssertException("nonlethal shotgun changed victim health ledger: before="+healthBefore+" after="+healthAfter);
                float staminaAfter=com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.state(victim).damage();
                if(!(staminaAfter>staminaBefore))throw new GameTestAssertException("registered victim received no shotgun stamina impact: before="+staminaBefore+" after="+staminaAfter);
                h.succeed();
            }catch(RuntimeException|Error e){cleanup.run();throw e;}});
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); }
        catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_guns_svalinn", timeoutTicks=100)
    public static void registeredSvalinnFiresAndEjectsItsRealCell(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"registered-svalinn-cell");
        var owned=new ArrayList<Entity>(); var done=new AtomicBoolean();
        Runnable cleanup=()->{if(!done.compareAndSet(false,true))return;for(var e:owned)if(!e.isRemoved())e.discard();fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){cleanup.run();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        try { h.runAtTickTime(2,()->{try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body=active.body(); owned.add(body);
            var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
            body.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick();
            var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String hand=hands.activeHand();
            var gun=GunItemFactory.initializeCopy(new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_LASER_SVALINN.get()));
            var slot=gun.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get());
            if(slot==null||slot.cell().isEmpty())throw new GameTestAssertException("resolved Svalinn factory did not compose its starting cell");
            var initialCell=slot.cell().orElseThrow();
            var initialBattery=initialCell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            if(!initialCell.is(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get())||initialBattery==null||initialBattery.charge()!=360)
                throw new GameTestAssertException("resolved Svalinn starting small cell was not charged to 360");
            var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),gun);
            loose.setNoGravity(true); owned.add(loose);
            if(!h.getLevel().addFreshEntity(loose)||BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("authenticated Svalinn pickup failed");
            var current=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var token=current.token(hand).orElseThrow();
            var mode=CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            if(mode.status()!=CombatModeRequestResult.Status.ACCEPTED)throw new GameTestAssertException("authenticated combat-mode request failed");
            var selected=GunWeaponResolver.resolve(body).orElseThrow(()->new GameTestAssertException("Svalinn selection unresolved"));
            var reply=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),1,selected.handRevision(),hand,selected.itemToken(),body.getEyePosition().add(0,0,20)));
            if(reply.status()!=GunFireSystem.Status.FIRED||!reply.consumedSource())throw new GameTestAssertException("authenticated Svalinn press did not fire");
            var afterHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var firedGun=afterHands.stackCopy(hand).orElseThrow();
            if(firedGun.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get())!=null)
                throw new GameTestAssertException("Svalinn incorrectly stored integral battery state on gun");
            slot=firedGun.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get());
            var firedCell=slot==null?null:slot.cell().orElse(null);
            var firedBattery=firedCell==null?null:firedCell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            if(firedBattery==null||firedBattery.charge()!=310)throw new GameTestAssertException("Svalinn shot did not consume 50 charge from nested cell");
            var eject=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_CELL,hand,active.epoch(),2,afterHands.revision(),null,afterHands.token(hand).orElseThrow()));
            if(!eject.accepted())throw new GameTestAssertException("authenticated Svalinn cell eject denied: "+eject.reason());
            var ejectedHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var ejectedGun=ejectedHands.stackCopy(hand).orElseThrow();
            slot=ejectedGun.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get());
            if(slot==null||slot.cell().isPresent())throw new GameTestAssertException("eject did not leave Svalinn cell slot empty");
            String offhand=ejectedHands.handIds().stream().filter(id->!id.equals(hand)).findFirst().orElseThrow();
            var cell=ejectedHands.stackCopy(offhand).orElseThrow(()->new GameTestAssertException("ejected cell was not placed in free offhand"));
            var cellBattery=cell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            if(!cell.is(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get())||cellBattery==null||cellBattery.charge()!=310)
                throw new GameTestAssertException("free offhand did not receive the fired 310-charge small cell");
            var select=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,offhand,active.epoch(),3,ejectedHands.revision(),null,null));
            if(!select.accepted())throw new GameTestAssertException("could not select ejected cell hand: "+select.reason());
            var selectedHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var drop=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.DROP,offhand,active.epoch(),4,selectedHands.revision(),null,selectedHands.token(offhand).orElseThrow()));
            if(!drop.accepted())throw new GameTestAssertException("could not drop old Svalinn cell: "+drop.reason());
            com.juicyslew.moonstation14.entities.LooseItemEntity oldCellWorld=null;
            for(var entity:h.getLevel().getEntities().getAll())if(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item&&item.getItem().is(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get())){oldCellWorld=item;owned.add(entity);}
            if(oldCellWorld==null)throw new GameTestAssertException("dropped 310-charge cell was not published in the world");
            final var oldDroppedCell=oldCellWorld;
            var handsAfterDrop=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var spare=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get());
            spare.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(200));
            spare.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Spare"));
            var spareEntity=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),body.getEyePosition().add(body.getLookAngle().scale(.7)),spare);
            spareEntity.setNoGravity(true);owned.add(spareEntity);
            if(!h.getLevel().addFreshEntity(spareEntity)||BodyHandItemTransfer.pickupSelected(fixture.player(),spareEntity.getUUID(),offhand,handsAfterDrop.revision(),ignored->{})!=BodyHandItemTransfer.Result.SUCCESS)
                throw new GameTestAssertException("authenticated spare-cell pickup failed");
            var handsWithSpare=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var spareToken=handsWithSpare.token(offhand).orElseThrow(); var gunToken=handsWithSpare.token(hand).orElseThrow();
            var swap=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND,offhand,active.epoch(),5,handsWithSpare.revision(),null,spareToken,gunToken,-1,-1,-1,-1,-1,-1,null,null,hand,null));
            if(!swap.accepted())throw new GameTestAssertException("authenticated Svalinn cell insertion denied: "+swap.reason());
            var insertedHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if(insertedHands.stackCopy(offhand).isPresent())throw new GameTestAssertException("source hand was not emptied by cell insertion");
            var insertedGun=insertedHands.stackCopy(hand).orElseThrow();
            slot=insertedGun.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get());
            var insertedCell=slot==null?null:slot.cell().orElse(null);
            var insertedBattery=insertedCell==null?null:insertedCell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
            if(insertedBattery==null||insertedBattery.charge()!=200||!net.minecraft.network.chat.Component.literal("Spare").equals(insertedCell.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)))
                throw new GameTestAssertException("inserted Svalinn cell did not preserve spare charge and custom name");
            if(oldDroppedCell.isRemoved()||oldDroppedCell.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge()!=310)
                throw new GameTestAssertException("old dropped cell changed during Svalinn insertion");
            var activeGun=BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,hand,active.epoch(),6,insertedHands.revision(),null,null));
            if(!activeGun.accepted())throw new GameTestAssertException("could not reselect Svalinn after insertion");
            var selectedGunHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var runtime=selectedGunHands.stackCopy(hand).orElseThrow().getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
            long delay=Math.max(1,runtime.nextFireTick()-h.getLevel().getGameTime());
            h.runAfterDelay((int)delay,()->{try {
                var readyHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var secondSelected=GunWeaponResolver.resolve(body).orElseThrow(()->new GameTestAssertException("inserted Svalinn selection unresolved"));
                var secondReply=GunShotRequestService.handle(fixture.player(),new GunShotRequest(active.epoch(),2,secondSelected.handRevision(),hand,secondSelected.itemToken(),body.getEyePosition().add(0,0,20)));
                if(secondReply.status()!=GunFireSystem.Status.FIRED||!secondReply.consumedSource())throw new GameTestAssertException("authenticated second Svalinn press did not fire");
                var fired=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow();
                var finalSlot=fired.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get());
                var finalCell=finalSlot==null?null:finalSlot.cell().orElse(null);
                var finalBattery=finalCell==null?null:finalCell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get());
                if(finalBattery==null||finalBattery.charge()!=150||!net.minecraft.network.chat.Component.literal("Spare").equals(finalCell.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)))
                    throw new GameTestAssertException("second shot did not consume 50 charge from inserted Spare cell");
                if(fired.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get())!=null)throw new GameTestAssertException("Svalinn acquired integral battery state");
                if(oldDroppedCell.isRemoved()||oldDroppedCell.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge()!=310)
                    throw new GameTestAssertException("second shot changed the old dropped cell");
                h.succeed();
            }catch(RuntimeException|Error e){cleanup.run();throw e;}});
        }catch(RuntimeException|Error e){cleanup.run();throw e;}}); }
        catch(RuntimeException|Error e){cleanup.run();throw e;}
    }
}
