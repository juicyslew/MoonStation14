package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics"})
public final class LigneousHarvestGameTests {
    private LigneousHarvestGameTests() { }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=4000)
    @GameTestTags({"botany-genetics","botany-harvest"})
    public static void ligneousHarvestRequiresResolvedSharpCapability(GameTestHelper helper) {
        var wheatId=ModSeedProfiles.seedId("wheat");
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper,new ItemStack(ModItems.WHEAT_SEEDS.get()),
                "ligneous-sharp-harvest",(h,fixture,epoch,hand,tray,pos,level,finished)->{
            try {
                var body=HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var plant=tray.plantState(); var baseline=plant.effectiveProfile();
                require(plant.ready() && !baseline.lifecycle().ligneous(),"resolved baseline wheat is harvest-ready and nonligneous");
                var beforeBaseline=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e->!e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                var query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(epoch,3));
                var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(epoch,2,query.revision(),hand,null,pos,
                        hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.PLACED,
                        "ordinary authenticated empty-hand harvest remains valid for baseline wheat");
                var baselineOutputs=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e->!e.getItem().isEmpty()&&!beforeBaseline.contains(e.getUUID()));
                require(baselineOutputs.size()==baseline.lifecycle().yield(),"baseline harvest publishes ordinary resolved yield");

                var ligneous=BotanyFixtureScenarios.ligneousPreset(new GeneticValue(wheatId,baseline));
                var ligneousProfile=ligneous.profile();
                tray.commitPlant(ligneous,level.getGameTime()); plant=tray.plantState();
                plant.state(8,ligneousProfile.biology().endurance(),0,0,true,false); tray.commitMutation();
                var beforeLigneous=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e->!e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(epoch,4));
                hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(epoch,3,query.revision(),hand,null,pos,
                        hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.DENIED,
                        "empty hand cannot harvest a ligneous living plant");
                require(plant.seed()!=null && plant.ready() && plant.health()==100
                                && level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e->!e.getItem().isEmpty()&&!beforeLigneous.contains(e.getUUID())).isEmpty(),
                        "empty-hand denial leaves ligneous plant, health and outputs unchanged");

                var knife=LooseItemEntity.create(level,body.position().add(0,.1,.8),new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife),"production kitchen knife published for ordinary ownership transfer");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,knife.getBoundingBox().getCenter());
                require(BodyHandItemTransfer.pickupSelected(fixture.player(),knife.getUUID(),hand,
                        body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).revision(),ignored->{})
                        ==BodyHandItemTransfer.Result.SUCCESS,"registered production kitchen knife acquired through normal hand transfer");
                var hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); var token=hands.token(hand).orElseThrow();
                query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(epoch,5));
                hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
                query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(epoch,6));
                hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(epoch,5,query.revision(),hand,token,pos,
                        hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.PLACED,
                        "registered production KitchenKnife Sharp profile admits authenticated ligneous harvest");
                var outputs=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e->!e.getItem().isEmpty()&&!beforeLigneous.contains(e.getUUID()));
                require(outputs.size()==ligneousProfile.lifecycle().yield(),"sharp harvest publishes resolved yield");
                for(var output:outputs) require(ligneous.equals(output.getItem().get(ModDataComponents.GENETIC_VALUE.get())),
                        "sharp harvest outputs retain acquired ligneous genetics");
                require(plant.seed()==null,"nonrepeat ligneous harvest clears source only after output publication");
                require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token(hand).filter(token::equals).isPresent(),
                        "sharp tool remains owned in active hand");
                finish(h,fixture,finished,null);
            } catch(Throwable failure) { finish(h,fixture,finished,failure); }
        });
    }

    private static void finish(GameTestHelper helper,com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
            AtomicBoolean done,Throwable failure){
        if(!done.compareAndSet(false,true))return;
        try{fixture.close();}catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        if(failure==null)helper.succeed();else helper.testInfo.fail(failure);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
