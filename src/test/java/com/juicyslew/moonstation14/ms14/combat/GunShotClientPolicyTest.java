package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.combat.client.ClientGunShotPrediction;
import static org.junit.jupiter.api.Assertions.*;

class GunShotClientPolicyTest {
    @Test void inspectorSixRoundProductionPlanAndSourceRevisionBarrierSurviveReordering() {
        var resources = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources("gun_profile", "base_weapon_revolver", "weapon_revolver_inspector");
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager(); manager.register(ModGunProfiles.TYPE);
        Map<ResourceLocation, GunProfile> guns = new HashMap<>();
        manager.stage(Map.of(ModGunProfiles.TYPE, resources), catalogs -> catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key, raw) -> guns.put(key, GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        var gun = guns.get(id("weapon_revolver_inspector"));
        var projectiles = Map.of(id("bullet_magnum"), new ProjectileProfile(Map.of(), false, true, 10));
        var cartridges = Map.of(id("cartridge_magnum"), new CartridgeProfile(id("bullet_magnum"), false, Set.of()));
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var state = RevolverProviderKernel.initialize(gun);
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), state);
        int readyDecisions=0, emptyDecisions=0;
        for (int n=0;n<6;n++) {
            var result = ClientGunShotPrediction.takeOne(stack, gun, GunRuntimeState.INITIAL, cartridges, projectiles, Map.of());
            assertEquals(GunAmmoProviderKernel.Status.READY, result.status());
            readyDecisions++;
            state = result.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), state);
        }
        var seventh = ClientGunShotPrediction.takeOne(stack, gun, GunRuntimeState.INITIAL, cartridges, projectiles, Map.of());
        // Virtual rounds become deferred spent casings, so the empty cylinder classifies EMPTY.
        assertEquals(GunAmmoProviderKernel.Status.EMPTY, seventh.status());
        emptyDecisions++;
        assertEquals(6,readyDecisions); assertEquals(1,emptyDecisions);
        var body=UUID.randomUUID();
        for (long latency : new long[]{0,2,20}) {
            var policy=new GunShotClientPolicy(); policy.bind(body,1);
            var binding=new GunShotClientPolicy.Binding(10,"right","inspector");
            var request=policy.submit(binding,100,13,net.minecraft.world.phys.Vec3.ZERO);
            var authoritative = seventh.gun();
            long revision=11;
            assertTrue(policy.acknowledge(new GunShotReply(body,1,request.sequence(),GunFireSystem.Status.FIRED,true,revision,"inspector",0),100+latency));
            assertFalse(policy.snapshotFresh(body,1,"right","inspector",10));
            assertTrue(policy.snapshotFresh(body,1,"right","inspector",revision));
            assertFalse(policy.snapshotFresh(body,1,"left","inspector",10)); // same-token hand move does not bypass debit barrier
            assertTrue(policy.snapshotFresh(body,1,"left","inspector",11));
            // The authoritative snapshot is exactly the kernel's returned stack, not a test-built state.
            assertEquals(seventh.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()),
                    authoritative.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()));
            assertFalse(policy.acknowledge(new GunShotReply(body,1,request.sequence(),GunFireSystem.Status.FIRED,true,revision,"inspector",0),100+latency));
            assertTrue(policy.snapshotFresh(body,1,"right","replacement",0)); // reload/item replacement clears barrier
            policy.bind(body,2); assertTrue(policy.snapshotFresh(body,2,"right","inspector",0)); // epoch switch
        }
    }

    @Test void inspectorCylinderDebitRunsBothReplySnapshotOrdersAtAllRequestedLatencies() {
        var resources = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources("gun_profile", "base_weapon_revolver", "weapon_revolver_inspector");
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager(); manager.register(ModGunProfiles.TYPE);
        Map<ResourceLocation, GunProfile> guns = new HashMap<>();
        manager.stage(Map.of(ModGunProfiles.TYPE, resources), catalogs -> catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key, raw) -> guns.put(key, GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        var gun = guns.get(id("weapon_revolver_inspector"));
        var projectiles = Map.of(id("bullet_magnum"), new ProjectileProfile(Map.of(), false, true, 10));
        var cartridges = Map.of(id("cartridge_magnum"), new CartridgeProfile(id("bullet_magnum"), false, Set.of()));
        for (long latency : new long[]{0,2,20}) for (boolean replyFirst : new boolean[]{true,false}) {
            var server = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
            server.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.initialize(gun));
            var client = server.copy(); var body=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,1);
            long revision=0;
            int gunshotCues=0;
            for (int shot=0;shot<6;shot++) {
                var plan=ClientGunShotPrediction.takeOne(client,gun,GunRuntimeState.INITIAL,cartridges,projectiles,Map.of());
                assertEquals(GunAmmoProviderKernel.Status.READY,plan.status());
                var selectedCue=ClientGunShotPrediction.feedbackCue(gun,plan.status()).orElseThrow();
                assertEquals(gun.gun().gunshotSound().orElseThrow().event(),selectedCue.event()); gunshotCues++;
                var authoritative=ClientGunShotPrediction.takeOne(server,gun,GunRuntimeState.INITIAL,cartridges,projectiles,Map.of());
                assertEquals(GunAmmoProviderKernel.Status.READY,authoritative.status());
                server=authoritative.gun();
                var request=policy.submit(new GunShotClientPolicy.Binding(revision,"right","inspector"),100+shot*30,10,Vec3.ZERO);
                assertNotNull(request);
                long confirmed=revision+1;
                var reply=new GunShotReply(body,1,request.sequence(),GunFireSystem.Status.FIRED,true,confirmed,"inspector",0);
                if(replyFirst){
                    assertTrue(policy.acknowledge(reply,100+shot*30+latency));
                    assertFalse(policy.snapshotFresh(body,1,"right","inspector",revision));
                    assertTrue(policy.snapshotFresh(body,1,"right","inspector",confirmed));
                } else {
                    // Snapshot delivery updates the client-owned stack before the matching reply arrives.
                    client=server.copy(); revision=confirmed;
                    assertTrue(policy.snapshotFresh(body,1,"right","inspector",revision));
                    assertTrue(policy.acknowledge(reply,100+shot*30+latency));
                }
                if(replyFirst){client=server.copy();revision=confirmed;}
                assertTrue(policy.snapshotFresh(body,1,"right","inspector",revision));
            }
            var finalPlan=ClientGunShotPrediction.takeOne(client,gun,GunRuntimeState.INITIAL,cartridges,projectiles,Map.of());
            assertEquals(GunAmmoProviderKernel.Status.EMPTY,finalPlan.status());
            var emptyCue=ClientGunShotPrediction.feedbackCue(gun,finalPlan.status()).orElseThrow();
            assertEquals(gun.gun().emptySound().orElseThrow().event(),emptyCue.event());
            assertEquals(6,gunshotCues);
            assertNotEquals(gun.gun().gunshotSound().orElseThrow().event(),emptyCue.event());
            assertTrue(ClientGunShotPrediction.feedbackCue(gun,GunAmmoProviderKernel.Status.INVALID).isEmpty());
            com.google.gson.JsonObject sounds;
            try(var input=GunShotClientPolicyTest.class.getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
                assertNotNull(input,"assets/moonstation14/sounds.json");
                sounds=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch(java.io.IOException exception){throw new AssertionError(exception);}
            assertEquals("moonstation14:ss14/weapons/guns/empty/empty",sounds.getAsJsonObject("ss14/weapons/guns/empty").getAsJsonArray("sounds").get(0).getAsString());
            assertEquals("moonstation14:ss14/weapons/guns/gunshots/revolver",sounds.getAsJsonObject("ss14/weapons/guns/gunshots/revolver").getAsJsonArray("sounds").get(0).getAsString());
        }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    @Test void correlatedDebitRequiresFreshSourceSnapshotBeforeNextReadyPlan() {
        UUID body=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,1);
        var binding=new GunShotClientPolicy.Binding(10,"right","inspector");
        var request=policy.submit(binding,100,13,Vec3.ZERO,1,true);
        assertTrue(policy.acknowledge(new GunShotReply(body,1,request.sequence(),GunFireSystem.Status.FIRED,
                true,11,"inspector",0),101));
        assertFalse(policy.snapshotFresh(body,1,"right","inspector",10));
        assertTrue(policy.snapshotFresh(body,1,"right","inspector",11));
        assertFalse(policy.snapshotFresh(body,1,"left","inspector",10)); // hand move alone cannot clear the barrier
        assertTrue(policy.snapshotFresh(body,1,"left","inspector",11));
    }
    @Test void bodyEpochLifecycleClearsBarrierAndLateOldReplyCannotRestoreIt() {
        UUID body=UUID.randomUUID(), next=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,1);
        var old=policy.submit(new GunShotClientPolicy.Binding(4,"right","inspector"),100,1,Vec3.ZERO);
        assertTrue(policy.acknowledge(new GunShotReply(body,1,old.sequence(),GunFireSystem.Status.FIRED,true,90,"inspector",0),101));
        policy.bind(next,1);
        assertTrue(policy.snapshotFresh(next,1,"right","inspector",0));
        assertFalse(policy.acknowledge(new GunShotReply(body,1,old.sequence(),GunFireSystem.Status.FIRED,true,90,"inspector",0),102));
        assertTrue(policy.snapshotFresh(next,1,"right","inspector",0));
    }
    @Test void rapidTapCannotMoveProjectedCooldownBackwardsAcrossReplies() {
        for (long latency : new long[]{0, 2, 10}) {
            for (GunFireSystem.Status status : new GunFireSystem.Status[]{GunFireSystem.Status.FIRED, GunFireSystem.Status.REJECTED}) {
                long duration=GunFirePolicy.nextFireTick(100, status == GunFireSystem.Status.FIRED ? .75f : 1.5f).orElseThrow()-100;
                UUID body=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,1);
                var binding=new GunShotClientPolicy.Binding(1,"right","mosin");
                var request=policy.submit(binding,100,duration,Vec3.ZERO,1,true);
                assertNotNull(request);
                // Release/repress during the pending interval cannot submit a second shot.
                assertNull(policy.submit(binding,100+latency,duration,Vec3.ZERO,2,true));
                assertTrue(policy.acknowledge(new GunShotReply(body,1,request.sequence(),status,false,0,"mosin",1),100+latency));
                assertEquals(100+duration,policy.projectedDeadline());
                assertNull(policy.submit(binding,100+latency,duration,Vec3.ZERO,2,true));
            }
        }
    }

    @Test void gunPreservesProjectionWhileMeleeUsesAuthoritativeReplyDeadline() {
        UUID body=UUID.randomUUID();
        var gun=new GunShotClientPolicy(); gun.bind(body,1);
        var shot=gun.submit(new GunShotClientPolicy.Binding(1,"right","gun"),10,27,Vec3.ZERO);
        assertTrue(gun.acknowledge(new GunShotReply(body,1,shot.sequence(),GunFireSystem.Status.FIRED,false,0,"gun",1),11));
        assertEquals(37,gun.projectedDeadline());

        var melee=new MeleeAttackClientPolicy(); melee.bind(body,1);
        var attack=melee.submit(new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED,0,"body",""),10,20,null);
        assertTrue(melee.acknowledge(new MeleeAttackReply(body,1,attack.sequence(),MeleeAttackReply.Status.MISS,true,null,0,null,7),11));
        assertEquals(18,melee.projectedDeadline());
    }
    @Test void sourceGateAcknowledgementTimeoutAndEpochBinding() {
        UUID body=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,4);
        var first=policy.submit(new GunShotClientPolicy.Binding(2,"right","gun-A"),100,8,Vec3.ZERO);
        assertNotNull(first); assertTrue(policy.pending());
        assertNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-A"),101,8,Vec3.ZERO));
        assertFalse(policy.acknowledge(new GunShotReply(UUID.randomUUID(),4,first.sequence(),GunFireSystem.Status.REJECTED,false,2,"gun-A",3),102));
        assertTrue(policy.acknowledge(new GunShotReply(body,4,first.sequence(),GunFireSystem.Status.REJECTED,false,2,"gun-A",3),102));
        assertNotNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-B"),103,8,Vec3.ZERO));
        assertTrue(policy.timeout(112,9)); assertFalse(policy.pending());
        assertNotNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-A"),113,8,Vec3.ZERO));
        policy.bind(body,5); assertFalse(policy.pending()); assertEquals(0,policy.projectedDeadline());
    }
}
