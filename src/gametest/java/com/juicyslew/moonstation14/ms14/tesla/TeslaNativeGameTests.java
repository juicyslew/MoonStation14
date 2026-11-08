package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.tesla.TeslaMachineBlockEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla"})
@PrefixGameTestTemplate(false)
public final class TeslaNativeGameTests {
    private TeslaNativeGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
    private static java.util.List<TeslaLightningSelection.Candidate<TeslaLightningSystem.Target>> candidates(
            net.minecraft.server.level.ServerLevel level,net.minecraft.world.phys.Vec3 source,TeslaBallProfile.Shooter shooter){
        return TeslaLightningSystem.targets(level,source,shooter.range()).stream().filter(t->t.position().distanceToSqr(source)<=shooter.range()*shooter.range())
                .map(t->new TeslaLightningSelection.Candidate<>(t,t.data().priority(),t.data().probability(),t.data().resistance())).toList();
    }
    private static long seedForVolley(TeslaBallProfile.Shooter shooter,java.util.List<TeslaLightningSelection.Candidate<TeslaLightningSystem.Target>> candidates,
            java.util.function.Predicate<java.util.List<TeslaLightningSelection.Candidate<TeslaLightningSystem.Target>>> accepted){
        for(long seed=0;seed<100_000;seed++){var random=new java.util.Random(seed);int bolts=TeslaLightningSelection.initialBoltCount(shooter.maxBolts(),random);
            if(bolts==3&&accepted.test(TeslaLightningSelection.chooseMany(candidates,bolts,random)))return seed;}
        throw new GameTestAssertException("no deterministic test-only seed met the requested three-bolt selection");
    }
    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_commissioning_phase",timeoutTicks=160)
    public static void productionFixtureThreeRodsCoverAllCornersAndAbsorbMaximumVolley(GameTestHelper h){
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var record=new com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.BuildRecord[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){var r=record[0];if(r==null)return;
            var ps=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());ps.addAll(r.bootstrap().devices());ps.addAll(r.export().devices());ps.addAll(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin));
            com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.forget(level,origin);com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.forget(level,origin);
            for(var c:r.bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());for(var c:r.export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
            r.bootstrap().nodes().forEach(c->ps.add(c.host()));r.export().nodes().forEach(c->ps.add(c.host()));ps.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.build(level,origin).startsWith("Built Tesla"),"build real production Tesla fixture");
            record[0]=com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(level,origin);var layout=record[0].placed();
            var main=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MAIN_ID);var rods=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin);
            var bounds=new net.minecraft.world.phys.AABB(origin.getX()+6.25,origin.getY()+4.25,origin.getZ()+8.25,origin.getX()+8.75,origin.getY()+6.75,origin.getZ()+10.75);
            var corners=new java.util.ArrayList<net.minecraft.world.phys.Vec3>();for(double x:new double[]{bounds.minX,bounds.maxX})for(double y:new double[]{bounds.minY,bounds.maxY})for(double z:new double[]{bounds.minZ,bounds.maxZ})corners.add(new net.minecraft.world.phys.Vec3(x,y,z));
            check(rods.size()==4,"production fixture has four registered rod identities, including original export-lane support");
            var supplemental=rods.stream().filter(p->!p.equals(layout.collectors().getFirst())).toList();check(supplemental.size()==3,"three supplemental production rods are distinct from preserved cable-support rod");
            for(var corner:corners){check(supplemental.stream().allMatch(p->corner.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(p))<=main.shooter().range()),"each supplemental rod individually covers compact source corner "+corner+" using resolved range "+main.shooter().range());check(rods.stream().filter(p->corner.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(p))<=main.shooter().range()).count()>=3,"at least three total production rods cover compact source corner "+corner);}
            var choices=layout.emitters().stream().map(p->(com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity)level.getBlockEntity(p)).filter(java.util.Objects::nonNull)
                    .flatMap(e->corners.stream().filter(c->c.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(e.getBlockPos()))<=main.shooter().range()).map(c->new Object[]{e,c})).findFirst().orElseThrow(()->new GameTestAssertException("no real fixture emitter is in resolved MAIN range of a canonical source corner"));
            var emitter=(com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity)choices[0];var source=(net.minecraft.world.phys.Vec3)choices[1];var target=TeslaLightningSystem.resolveBlockTarget(level,emitter);
            check(target!=null,"in-range real emitter resolves as an eligible direct target");
            var before=MachineryIntegrity.damage(emitter);var live=candidates(level,source,main.shooter());
            long protectedSeed=seedForVolley(main.shooter(),live,selected->selected.size()==3&&selected.stream().allMatch(c->c.value().blockEntity() instanceof TeslaMachineBlockEntity&&level.getBlockState(c.value().blockEntity().getBlockPos()).is(ModBlocks.TESLA_GROUNDING_ROD.get())));
            check(TeslaLightningSystem.shoot(level,source,main.shooter(),main.lightning(),new java.util.Random(protectedSeed))==3,"production rods absorb a controlled resolved MAIN maximum volley with all three real coils still present");
            check(MachineryIntegrity.damage(emitter)==before,"eligible production emitter takes no structural damage when three rods absorb all bolts");
            for(var c:record[0].export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
            for(var c:record[0].bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
            rods.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));for(BlockPos p:layout.collectors().subList(1,4))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            var unprotected=candidates(level,source,main.shooter());long leakSeed=seedForVolley(main.shooter(),unprotected,selected->selected.stream().anyMatch(c->c.value().blockEntity()==emitter));
            TeslaLightningSystem.shoot(level,source,main.shooter(),main.lightning(),new java.util.Random(leakSeed));
            check(MachineryIntegrity.damage(emitter)>before,"same real eligible emitter takes direct structural damage after all rods and coils are absent");
            h.succeed();
        },origin,origin.offset(26,11,18));
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=20)
    public static void actualThreeRodPriorityStopsMaximumMainVolleyAndUnprotectedEmitterTakesDamage(GameTestHelper h){
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();var source=h.absolutePos(new BlockPos(8,5,8));
        var rods=java.util.List.of(source.offset(-2,0,-1),source.offset(-1,0,2),source.offset(1,0,-2));var emitter=source.east(4);
        rods.forEach(p->level.setBlock(p,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3));level.setBlock(emitter,ModBlocks.EMITTER.get().defaultBlockState(),3);
        h.runAfterDelay(1,()->{
            var resolved=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MAIN_ID);
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(emitter).getBlock());
            var profile=ModTeslaProfiles.machines(level).get(ModTeslaProfiles.profileForBlock(block));
            long seed=java.util.stream.LongStream.range(0,10000).filter(s->TeslaLightningSelection.initialBoltCount(resolved.shooter().maxBolts(),new java.util.Random(s))==3).findFirst().orElseThrow();
            check(profile!=null&&profile.target().priority()<3,"resolved eligible emitter has lower priority than rods");
            var sourcePoint=net.minecraft.world.phys.Vec3.atCenterOf(source);
            check(TeslaLightningSystem.shoot(level,sourcePoint,resolved.shooter(),resolved.lightning(),new java.util.Random(seed))==3,"resolved MAIN shooter emits controlled maximum three-bolt volley");
            var emitterBE=level.getBlockEntity(emitter);check(emitterBE!=null&&MachineryIntegrity.damage(emitterBE)==0,"real priority-3 rods absorb all maximum-volley selections before emitter");
            rods.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
            TeslaLightningSystem.shoot(level,sourcePoint,resolved.shooter(),resolved.lightning(),new java.util.Random(seed));
            check(MachineryIntegrity.damage(emitterBE)>0,"same eligible emitter takes real structural damage when rods are absent");
            level.setBlock(emitter,Blocks.AIR.defaultBlockState(),3);h.succeed();
        });
    }
    private static double component(net.minecraft.world.phys.Vec3 v,int axis){return axis==0?v.x:axis==1?v.y:v.z;}
    private static net.minecraft.world.phys.Vec3 facePoint(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.Face face,double axisValue) {
        int axis=face.axis().ordinal(),u=(axis+1)%3,v=(axis+2)%3;double[] p=new double[3];
        p[axis]=axisValue;p[u]=(face.minU()+face.maxU()+1)*.5;p[v]=(face.minV()+face.maxV()+1)*.5;
        return new net.minecraft.world.phys.Vec3(p[0],p[1],p[2]);
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void registeredSourceUsesResolvedMainProfileAndActualPaFeed(GameTestHelper h) {
        BlockPos local=new BlockPos(4,4,4), pos=h.absolutePos(local);
        TeslaEnergyBallEntity source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"registered main profile resolves");
        source.moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);check(h.getLevel().addFreshEntity(source),"main source added");
        check(source.energy()==0&&!source.isMini(),"main starts at resolved zero energy");
        check(source.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents mainName
                &&mainName.getKey().equals("entity.moonstation14.tesla_energy_ball"),"main uses its dedicated translatable display name");
        h.runAfterDelay(10,()->{
            check(source.isAlive()&&source.energy()==0,"main source has no spontaneous energy drain/gain");
            var pa=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(h.getLevel()).get(com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.TYPE.typeId());
            source.receiveParticleEnergy(pa.strength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_1));
            check(source.energy()>0,"ordinary resolved PA particle feeds main source");
            TeslaEnergyBallEntity mini=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
            check(mini!=null&&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"registered inherited mini profile resolves");
            check(mini.isMini()&&mini.energy()==0&&mini.profileId().equals(ModTeslaProfiles.MINI_ID),"mini keeps its separate resolved identity and energy");
            check(mini.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents miniName
                    &&miniName.getKey().equals("entity.moonstation14.tesla_mini_energy_ball"),"mini uses its dedicated translatable display name");
            mini.setCustomName(net.minecraft.network.chat.Component.literal("Operator alias"));
            check(mini.getName().getString().equals("Operator alias"),"custom names override the default mini translation");
            source.discard();mini.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void registeredCoilAndRodAcceptActualLightningStrikes(GameTestHelper h) {
        BlockPos coilPos=new BlockPos(3,3,3),rodPos=new BlockPos(5,3,3);
        h.setBlock(coilPos,ModBlocks.TESLA_COIL.get());h.setBlock(rodPos,ModBlocks.TESLA_GROUNDING_ROD.get());
        var coil=h.getLevel().getBlockEntity(h.absolutePos(coilPos));var rod=h.getLevel().getBlockEntity(h.absolutePos(rodPos));
        check(coil instanceof TeslaMachineBlockEntity&&rod instanceof TeslaMachineBlockEntity,"registered machines create native BEs");
        var c=(TeslaMachineBlockEntity)coil;var r=(TeslaMachineBlockEntity)rod;
        check(c.receiveLightningStrike()&&r.receiveLightningStrike(),"both native conductors accept strike");
        check(c.energyJoules()==5_000_000&&r.energyJoules()==0,"coil charges finite resolved amount; rod stores none");
        check(c.powerSupplierOffer()!=null&&c.powerSupplierOffer().currentWatts()>0&&r.powerSupplierOffer()==null,"only charged coil offers real HV power");
        h.setBlock(coilPos,Blocks.AIR);h.setBlock(rodPos,Blocks.AIR);h.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainBallNativeContactConsumesAdmittedMobAndCreditsEnergy(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(4,4,4));
        TeslaEnergyBallEntity source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        var body=net.minecraft.world.entity.EntityType.VILLAGER.create(h.getLevel());
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID)&&body!=null,"resolved main and native mob create");
        source.moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        body.moveTo(pos.getX()+.55,pos.getY()+.5,pos.getZ()+.5);body.setNoAi(true);body.setNoGravity(true);
        check(h.getLevel().addFreshEntity(source)&&h.getLevel().addFreshEntity(body),"source and mob join live level");
        h.runAfterDelay(1,()->{
            check(!body.isAlive()&&source.energy()==2&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.LIVING
                    &&source.lastEnergyInputAmount()==2,"native admitted death consumes living contact once and credits attributed resolved yield");
            source.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void miniBallContactShocksWithoutConsumptionOrGrowth(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(4,4,4));
        var body=net.minecraft.world.entity.EntityType.VILLAGER.create(h.getLevel());
        check(body!=null,"native contact victim created");
        body.moveTo(pos.getX()+.55,pos.getY()+.5,pos.getZ()+.5);body.setNoAi(true);body.setNoGravity(true);
        check(h.getLevel().addFreshEntity(body),"native villager joins level before shock baseline");
        h.runAfterDelay(1,()->{
            float shockBefore=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            TeslaEnergyBallEntity mini=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
            check(mini!=null&&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"resolved mini profile initializes");
            mini.moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            check(h.getLevel().addFreshEntity(mini),"mini joins level after character reconciliation");
            h.runAfterDelay(1,()->{
                float shockAfter=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                        .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
                var actor=com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body);
                check(body.isAlive()&&Math.abs((shockAfter-shockBefore)-7f)<.001f,"mini applies exactly the resolved integer-truncated shock damage to typed shock ledger; delta="+(shockAfter-shockBefore)
                        +" attempts="+mini.miniContactAttempts()+" result="+mini.miniLastShockResult()+" victim="+mini.miniLastContact()
                        +" statusTrait="+(body instanceof com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait)+" actor="+actor.isPresent()
                        +" stunnable="+actor.flatMap(c->c.component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class)).isPresent()
                        +" noPhysics="+body.noPhysics+" spectator="+body.isSpectator()+" box="+body.getBoundingBox()+" mini="+mini.getBoundingBox());
                check(mini.isMini()&&mini.energy()==0&&h.getLevel().getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).size()==1,"mini remains zero-energy and cannot grow/spawn another ball");
                mini.discard();body.discard();h.succeed();
            });
        });
    }

    @GameTestTags({"tesla", "tesla-lightning-beam"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void liveBeamContactIsEntryOnlyAndUsesFixedSegment(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(4,4,4));
        var body=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        var offAxis=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        check(body!=null&&offAxis!=null,"native mobs constructed");
        body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+.5);body.setNoAi(true);body.setNoGravity(true);
        offAxis.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+2);offAxis.setNoAi(true);offAxis.setNoGravity(true);
        check(level.addFreshEntity(body)&&level.addFreshEntity(offAxis),"native mobs enter live level");
        h.runAfterDelay(1,()->{
            float before=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            float offAxisBefore=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(offAxis)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID).lightning();
            var from=new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+1,p.getZ()+.5);
            var to=new net.minecraft.world.phys.Vec3(p.getX()+5.5,p.getY()+1,p.getZ()+.5);
            TeslaLightningBeamHazard.create(level,from,to,profile,body.getUUID());
            check(com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.applySourceShock(body,7,1,5)
                    ==com.juicyslew.moonstation14.ms14.effect.EffectResult.APPLIED,"selected target receives immediate direct shock");
            TeslaLightningBeamHazard.tick(level);
            check(com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(offAxis)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f)==offAxisBefore,
                    "off-axis native mob is not shocked by the zero-width beam segment");
            float once=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            check(Math.abs((once-before)-7)<.01,"primary intersecting target is not shocked twice");
            TeslaLightningBeamHazard.tick(level);
            float repeated=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            check(Math.abs(repeated-once)<.01,"continued contact shocks once, not each tick");
            body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+4);
            TeslaLightningBeamHazard.tick(level);
            body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+.5);
            TeslaLightningBeamHazard.tick(level);
            float reentered=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body)
                    .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            check(Math.abs((reentered-once)-7)<.01,"re-entry after leaving refreshes contact shock");
            body.discard();offAxis.discard();h.succeed();
        });
    }

    @GameTestTags("tesla")
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=130)
    public static void mainBallTickEmitsAuthoredLightningIntoLiveRod(GameTestHelper h) {
        BlockPos local=new BlockPos(4,4,4), rodPos=local.east(4);
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var ownedShell=new java.util.ArrayList<BlockPos>();
        var sourceRef=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(sourceRef[0]!=null&&!sourceRef[0].isRemoved())sourceRef[0].discard();
                ownedShell.forEach(pos->level.setBlock(pos,Blocks.AIR.defaultBlockState(),3));level.setBlock(h.absolutePos(rodPos),Blocks.AIR.defaultBlockState(),3);}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        // This small non-consumable shell keeps the source from chasing its target into a physical
        // contact before the authored first-shot deadline; no source velocity/cadence is overridden.
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))==1){
            BlockPos localWall=local.offset(x,y,z);h.setBlock(localWall,Blocks.BEDROCK);ownedShell.add(h.absolutePos(localWall));
        }
        h.setBlock(rodPos,ModBlocks.TESLA_GROUNDING_ROD.get());
        var rod=(TeslaMachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(rodPos));
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        check(rod!=null&&source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"live rod and resolved main source create");
        sourceRef[0]=source;
        var world=h.absolutePos(local);source.moveTo(world.getX()+.5,world.getY()+.5,world.getZ()+.5);
        var worldRod=h.absolutePos(rodPos);
        check(worldRod.getX()-world.getX()==4&&worldRod.getX()+.5-(world.getX()+.5)>.65,
                "rod sits four blocks east and outside the resolved .65 block-contact radius");
        check(h.getLevel().addFreshEntity(source),"main source joins native level");
        h.runAfterDelay(105,()->{
            check(rod.hitActive(),"entity tick's authored first-shot cadence reaches live lightning target and flashes rod");
            check(h.getLevel().getBlockState(worldRod).is(ModBlocks.TESLA_GROUNDING_ROD.get())&&h.getLevel().getBlockEntity(worldRod)==rod,
                    "lightning target remains the same live rod rather than being consumed as physical contact");
            check(source.energy()==0&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.NONE,
                    "noncontact lightning target does not credit physical-consumption energy");
            h.succeed();
        });
    }

    // Per-level MV topology generations are shared; concurrent fixture destruction can reset the real PA phase.
    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_commissioning_phase",timeoutTicks=2200)
    public static void sharedFixtureNaturallyCommissionsTrackedTeslaSource(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var fixtureBounds=new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20);
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,fixtureBounds);
        var built=new com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.BuildRecord[1];
        var originalGeneratorRef=new com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){var r=built[0];if(r==null)return;
                com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin).forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
                var entity=originalGeneratorRef[0]==null?null:originalGeneratorRef[0].spawnedSourceIdentity();
                com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.forget(level,origin);
                com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.forget(level,origin);
                for(var c:r.bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
                for(var c:r.export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
                var positions=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());positions.addAll(r.bootstrap().devices());positions.addAll(r.export().devices());
                r.bootstrap().nodes().forEach(c->positions.add(c.host()));r.export().nodes().forEach(c->positions.add(c.host()));positions.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
                if(entity!=null&&level.getEntity(entity)!=null)level.getEntity(entity).discard();
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            var result=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.build(level,origin);
            check(result.startsWith("Built Tesla"),"Tesla fixture uses shared built infrastructure: "+result);
            built[0]=com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(level,origin);
            var layout=built[0].placed();
             var sourceBounds=new net.minecraft.world.phys.AABB(origin.getX()+6.25,origin.getY()+4.25,origin.getZ()+8.25,origin.getX()+8.75,origin.getY()+6.75,origin.getZ()+10.75);
              check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin).contains("rods=4/4"),"production fixture retains original collector rod plus three supplemental real rods");
            check(level.getBlockState(layout.startupGenerator()).is(com.juicyslew.moonstation14.block.ModBlocks.TESLA_GENERATOR.get()),"registered source is Tesla generator before startup");
            var originalGenerator=(com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity)level.getBlockEntity(layout.startupGenerator());originalGeneratorRef[0]=originalGenerator;
            var startupProfile=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(level).get(originalGenerator.profileId());
            check(startupProfile!=null&&startupProfile.startupThreshold()==16&&startupProfile.spawnProfile().equals(ModTeslaProfiles.MAIN_ID)
                    &&startupProfile.spawnEntity().equals(ModTeslaProfiles.id("tesla_energy_ball")),"live placed Tesla generator resolves canonical startup resource and Tesla source entity");
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.start(level,origin).startsWith("STARTING"),"normal startup explicitly requested");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,1500,()->"natural cage/PA startup failed: "+com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin),
                    ()->originalGenerator.spawnedSourceIdentity()!=null
                            &&level.getEntity(originalGenerator.spawnedSourceIdentity()) instanceof TeslaEnergyBallEntity
                            &&level.getBlockState(layout.startupGenerator()).isAir()
                            &&level.getBlockEntity(layout.acceleratorControl()) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa&&!pa.enabled(),
                    ()->{var gen=originalGenerator;
                        check(gen.startupInert()&&level.getEntity(gen.spawnedSourceIdentity()) instanceof TeslaEnergyBallEntity,"native tracked generator spawned exactly Tesla source");
                        check(level.getBlockState(layout.startupGenerator()).isAir()&&gen.isRemoved(),"ordinary Tesla horizon consumes its startup generator block");
                        check(gen.spawnedSourceIdentity().equals(level.getEntity(gen.spawnedSourceIdentity()).getUUID()),"removed original generator retains exact spawned-source identity proof");
                        check(level.getBlockEntity(layout.acceleratorControl()) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa&&!pa.enabled(),"one-shot PA stops after source creation");
                        check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin).contains("exportGraph=KNOWN"),"consumed generator leaves real export graph status available");
                         var source=(TeslaEnergyBallEntity)level.getEntity(gen.spawnedSourceIdentity());
                         var teslaProfile=com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.balls(level).get(source.profileId());
                           var supplementalRods=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin);
                            check(teslaProfile!=null&&teslaProfile.shooter().maxBolts()<=4&&supplementalRods.size()==4&&java.util.stream.Stream.of(
                                   new net.minecraft.world.phys.Vec3(sourceBounds.minX,sourceBounds.minY,sourceBounds.minZ),new net.minecraft.world.phys.Vec3(sourceBounds.minX,sourceBounds.minY,sourceBounds.maxZ),
                                   new net.minecraft.world.phys.Vec3(sourceBounds.minX,sourceBounds.maxY,sourceBounds.minZ),new net.minecraft.world.phys.Vec3(sourceBounds.minX,sourceBounds.maxY,sourceBounds.maxZ),
                                   new net.minecraft.world.phys.Vec3(sourceBounds.maxX,sourceBounds.minY,sourceBounds.minZ),new net.minecraft.world.phys.Vec3(sourceBounds.maxX,sourceBounds.minY,sourceBounds.maxZ),
                                   new net.minecraft.world.phys.Vec3(sourceBounds.maxX,sourceBounds.maxY,sourceBounds.minZ),new net.minecraft.world.phys.Vec3(sourceBounds.maxX,sourceBounds.maxY,sourceBounds.maxZ))
                                   .allMatch(corner->supplementalRods.stream().filter(p->corner.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(p))<=teslaProfile.shooter().range()).count()>=3),"at least three actual production rods cover every compact-source bounds corner within resolved MAIN range");
                        com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.refresh(level);
                        var faces=com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.currentFaces(level);
                         check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.closedVolumes(level).stream().anyMatch(v->
                                source.position().x>=v.min().getX()+.5&&source.position().x<=v.max().getX()+.5
                                &&source.position().y>=v.min().getY()+.5&&source.position().y<=v.max().getY()+.5
                                &&source.position().z>=v.min().getZ()+.5&&source.position().z<=v.max().getZ()+.5),"commissioned source is inside actual closed field volume");
                          var emitterIdentities=layout.acceleratorParts().stream().map(level::getBlockEntity)
                                  .filter(com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity.class::isInstance)
                                  .map(com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity.class::cast)
                                  .filter(part->java.util.Set.of("port","fore","starboard").contains(part.role()))
                                  .map(com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity::placementIdentity).collect(java.util.stream.Collectors.toSet());
                          var oldParticles=com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity.class;
                          // Westbound startup particles outside the entity-ticking lease are past the entire feeding corridor.
                          com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,75,
                                  ()->{var remaining=level.getEntitiesOfClass(oldParticles,fixtureBounds,p->emitterIdentities.contains(p.sourceEmitterIdentity()));
                                      return "incoming startup PA particles still in ticking fixture corridor: "+remaining.stream().limit(8)
                                              .map(p->p.getUUID()+"@"+p.position()+" ticking="+level.isPositionEntityTicking(p.blockPosition())+" age="+p.tickCount)
                                              .toList()+" source="+source.position();},
                                  ()->level.getEntitiesOfClass(oldParticles,fixtureBounds,p->emitterIdentities.contains(p.sourceEmitterIdentity())).isEmpty(),()->{
                              var control=(com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
                              check(control!=null&&control.assemblyValid()&&!control.enabled(),"commissioned fixture retains its assembled, stopped native PA control");
                              for(BlockPos emitterPos:layout.emitters()){
                                  var emitterBE=level.getBlockEntity(emitterPos);
                                  check(emitterBE instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity,"commissioned emitter identity remains present at "+emitterPos);
                                  check(com.juicyslew.moonstation14.ms14.tesla.MachineryIntegrity.damage(emitterBE)==0,"three production fixture rods prevent initial MAIN-volley emitter structural damage at "+emitterPos);
                              }
                             control.setStrength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_1);
                             control.setEnabled(true);
                             check(control.enabled()&&control.assemblyValid()&&control.firingEligible(),"normal PA control re-enables at resolved LEVEL_1 with live assembly and power");
                              long freshWaveEnabledAt=level.getGameTime();double priorEnergy=source.energy();long priorPaHits=source.paHitsReceived();double priorPaEnergy=source.paEnergyReceived();
                              com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,100,
                                   ()->"fresh PA eligibility phase not observed; source="+source.position()+" enabled="+control.enabled()+" assembly="+control.assemblyValid()+" firing="+control.firingEligible()+" schedule="+control.waveScheduleSnapshot()+" watts="+control.receivedWatts(),
                                   ()->{var schedule=control.waveScheduleSnapshot();return control.enabled()&&control.assemblyValid()&&control.firingEligible()&&schedule.eligibleSinceTick()>=freshWaveEnabledAt&&schedule.nextWaveTick()>=schedule.eligibleSinceTick();},()->{
                              var paProfile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(level).get(control.profileId());
                              check(paProfile!=null,"resolved PA control profile exists for fresh wave");
                              long scheduledNext=control.waveScheduleSnapshot().nextWaveTick();
                              int waveBudget=(int)Math.max(1,scheduledNext-level.getGameTime())+2; // one BE processing/poll phase allowance
                              java.util.Set<java.util.UUID> observedEmitters=new java.util.HashSet<>();long[] observedWave={-1};
                              com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,waveBudget,
                                   ()->{var live=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity.class,fixtureBounds,p->emitterIdentities.contains(p.sourceEmitterIdentity()));return "fresh PA wave emission absent; source="+source.position()+" enabled="+control.enabled()+" firing="+control.firingEligible()+" schedule="+control.waveScheduleSnapshot()+" expectedNext="+scheduledNext+" observedTick="+observedWave[0]+" emitterIds="+observedEmitters+" particles="+live.stream().limit(6).map(p->p.sourceEmitterIdentity()+"@"+p.position()).toList();},
                                   ()->{var live=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity.class,fixtureBounds,p->emitterIdentities.contains(p.sourceEmitterIdentity()));live.forEach(p->observedEmitters.add(p.sourceEmitterIdentity()));if(observedEmitters.size()==3&&observedWave[0]<0)observedWave[0]=level.getGameTime();return observedEmitters.size()==3;},()->{
                              var particleProfile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAcceleratorParticles.catalog(level).get(paProfile.particleProfile());
                              check(particleProfile!=null,"resolved PA particle lifetime profile exists");
                              com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,particleProfile.lifetimeTicks(),
                                   ()->"observed PA wave did not admit a fresh source hit within particle lifetime; source="+source.position()+" energy="+source.energy()+"/"+priorEnergy+" PA="+source.paHitsReceived()+" enabled="+control.enabled()+" firing="+control.firingEligible()+" schedule="+control.waveScheduleSnapshot()+" waveTick="+observedWave[0]+" emitters="+observedEmitters,
                                   ()->source.isAlive()&&source.paHitsReceived()>priorPaHits&&source.lastPaInputTick()>freshWaveEnabledAt,()->{
                                   // Capture the moving source and probe as one synchronous before/after pair.
                                   // Use a vertical cage face: a bottom-face probe can hit the ordinary floor,
                                   // which correctly blocks the production jump even after a field breach.
                                   var probeStart=source.position();
                                   var sideFaces=com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.currentFaces(level).stream()
                                           .filter(f->f.axis()!=net.minecraft.core.Direction.Axis.Y)
                                           .filter(f->f.corners().stream().allMatch(layout.fieldGenerators()::contains))
                                           .filter(f->Math.abs(component(probeStart,f.axis().ordinal())-(f.plane()+.5))>1)
                                           .sorted(java.util.Comparator.comparingDouble((com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.Face f)->-Math.abs(component(probeStart,f.axis().ordinal())-(f.plane()+.5))).thenComparing(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.Face::key)).toList();
                                   double radius=java.util.Optional.ofNullable(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.balls(level).get(source.profileId()).colliderRadius()).orElse(.35);
                                   var candidates=new java.util.ArrayList<String>();
                                   var probe=sideFaces.stream().map(f->{double p=f.plane()+.5;var center=facePoint(f,p);double normal=Math.abs(p-component(probeStart,f.axis().ordinal()));var proposed=probeStart.add(center.subtract(probeStart).scale(1+5/normal));var hit=com.juicyslew.moonstation14.entities.ProjectileLoadedSweep.clip(level,source,probeStart,proposed,radius);double centerDistance=probeStart.distanceTo(center);boolean clear=hit!=null&&hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||hit!=null&&probeStart.distanceTo(hit.getLocation())>centerDistance+1.0001;candidates.add(f.key()+" center="+center+" proposed="+proposed+" blockHit="+(hit==null?"unloaded/unknown":hit.getType()+"@"+hit.getLocation())+" clear="+clear);return clear?f:null;}).filter(java.util.Objects::nonNull).findFirst().orElseThrow(()->new GameTestAssertException("no owned vertical field face has a world-clear collinear center ray: "+candidates));
                                   var face=probe;
                                   int axis=face.axis().ordinal();double plane=face.plane()+.5,sourceSide=component(probeStart,axis)<plane?-1:1;
                                   var faceCenter=facePoint(face,plane);
                                   var proposed=probeStart.add(faceCenter.subtract(probeStart).scale(1+5/Math.abs(plane-component(probeStart,axis))));
                                   var contained=com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity.jumpDestination(level,source,probeStart,proposed,radius);
                                   check(contained!=null&&sourceSide*(component(contained,axis)-plane)>1.0,"production jump destination clamps before live vertical field face");
                                   check(source.lastEnergyInputAmount()==20,"fresh natural LEVEL_1 wave credits its resolved 20 energy input");
                                  check(source.paHitsReceived()>priorPaHits&&source.paEnergyReceived()-priorPaEnergy>=20,"fresh natural PA wave increases lifetime PA hit and energy counters");
                                 check(source.energy()!=priorEnergy,"fresh natural PA input changes the commissioned main's energy state");
                                 var corner=face.corners().getFirst();
                                 check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.breach(level,corner).startsWith("Removed"),"Tesla ownership permits controlled breach after legal generator consumption");
                                 com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.refresh(level);
                                  check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.firstFaceHit(level,probeStart,proposed,radius).isEmpty(),"breach invalidates the selected live face");
                                  var escaped=com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity.jumpDestination(level,source,probeStart,proposed,radius);
                                  check(escaped!=null&&sourceSide*(component(escaped,axis)-plane)<0,"same production jump destination crosses the missing face after breach; source="+source.position()+" proposed="+proposed+" result="+escaped+" axis="+axis+" plane="+plane+" sourceSide="+sourceSide+" colliderRadius="+radius+" remainingHit="+com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.firstFaceHit(level,source.position(),proposed,radius));
                                 com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,250,()->"commissioned Tesla remains at its generator center: "+source.position(),
                                         ()->source.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(layout.startupGenerator()))>.6,
                                         ()->{check(source.isAlive(),"naturally moving Tesla remains alive after leaving generator");h.succeed();});
                              });
                              });
                              });
                         });});
        },origin,origin.offset(26,11,18));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",timeoutTicks=120)
    public static void forgottenSharedRegistrationIsUnknownAndDoesNotTouchForeignBlocks(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var record=new com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.BuildRecord[1];
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.build(level,origin).startsWith("Built Tesla"),"Tesla fixture builds before stale-registration probe");
            record[0]=com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(level,origin);
            BlockPos foreign=record[0].placed().fieldGenerators().getFirst();var before=level.getBlockState(foreign);
            com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.forget(level,origin);
            String status=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin);
            check(status.contains("source=UNKNOWN")&&status.contains("exportGraph=UNKNOWN")&&status.contains("hardware:")
                    &&status.contains("failure="),"missing shared registration keeps unavailable source, power, and hardware diagnostics explicit without claiming readiness: "+status);
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.start(level,origin).startsWith("Refused"),"stale fixture cannot commission");
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.breach(level,foreign).startsWith("Refused"),"stale fixture cannot breach a foreign corner");
            check(level.getBlockState(foreign)==before,"stale queries/actions leave foreign block state unchanged");
            com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.forget(level,origin);
            for(var cable:record[0].bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,cable.host(),cable.face(),cable.tier());
            for(var cable:record[0].export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,cable.host(),cable.face(),cable.tier());
             var positions=new java.util.HashSet<BlockPos>(record[0].placed().occupiedPositions());positions.addAll(record[0].bootstrap().devices());positions.addAll(record[0].export().devices());
             positions.addAll(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin));
            record[0].bootstrap().nodes().forEach(cable->positions.add(cable.host()));record[0].export().nodes().forEach(cable->positions.add(cable.host()));positions.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
            h.succeed();
        },origin,origin.offset(26,11,18));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",timeoutTicks=120)
    public static void statusRemainsDiagnosticAfterRodAndCoilDamageAndRejectsReplacementEnergy(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var record=new com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.BuildRecord[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
             private void clean(){var r=record[0];if(r==null)return;var ps=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());ps.addAll(r.bootstrap().devices());ps.addAll(r.export().devices());ps.addAll(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin));com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.forget(level,origin);com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.forget(level,origin);for(var c:r.bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());for(var c:r.export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());r.bootstrap().nodes().forEach(c->ps.add(c.host()));r.export().nodes().forEach(c->ps.add(c.host()));ps.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.build(level,origin).startsWith("Built Tesla"),"fixture registered");record[0]=com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(level,origin);
            var positions=record[0].placed().collectors();var replacedPos=positions.get(1);level.setBlock(replacedPos,Blocks.AIR.defaultBlockState(),3);level.setBlock(positions.get(0),Blocks.AIR.defaultBlockState(),3);
            String damaged=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin);
              check(damaged.startsWith("Tesla ")&&damaged.contains("coils=2/3")&&damaged.contains("rods=3/4")&&damaged.contains("source=UNKNOWN"),"damaged hardware leaves pending source diagnostics visible: "+damaged);
            level.setBlock(replacedPos,ModBlocks.TESLA_COIL.get().defaultBlockState(),3);var replacement=(TeslaMachineBlockEntity)level.getBlockEntity(replacedPos);check(replacement.receiveLightningStrike(),"replacement coil can be charged for identity check");
            String replaced=com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.status(level,origin);
            check(replaced.contains("coils=2/3")&&replaced.contains("chargedJ=0.0"),"replacement coil is excluded by placement identity, not counted or read for energy: "+replaced);
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.start(level,origin).startsWith("Refused"),"strict ownership admission remains for startup");h.succeed();
        },origin,origin.offset(26,11,18));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",timeoutTicks=240)
    public static void chargedCoilExportsOnlyFiniteEnergyThroughRealHvAllocation(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var record=new com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.BuildRecord[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
             private void clean(){var r=record[0];if(r==null)return;
                 var positions=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());positions.addAll(r.bootstrap().devices());positions.addAll(r.export().devices());positions.addAll(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.groundingRodPositions(level,origin));
                 com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.forget(level,origin);
                com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.forget(level,origin);
                for(var c:r.bootstrap().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
                for(var c:r.export().nodes())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,c.host(),c.face(),c.tier());
                r.bootstrap().nodes().forEach(c->positions.add(c.host()));r.export().nodes().forEach(c->positions.add(c.host()));positions.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(com.juicyslew.moonstation14.ms14.tesla.fixture.TeslaFixtureService.build(level,origin).startsWith("Built Tesla"),"normal inactive Tesla export fixture builds");
            record[0]=com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(level,origin);
            var placed=record[0].placed();var coilPositions=placed.collectors().subList(1,4);
            var coils=coilPositions.stream().map(level::getBlockEntity).filter(TeslaMachineBlockEntity.class::isInstance).map(TeslaMachineBlockEntity.class::cast).toList();
            check(coils.size()==3&&coils.stream().allMatch(c->c.energyJoules()==0&&c.lastSuppliedWatts()==0),"all export coils begin empty with no bootstrap/HV energy");
            var rod=(TeslaMachineBlockEntity)level.getBlockEntity(placed.collectors().getFirst());
            check(rod.energyJoules()==0&&rod.powerSupplierOffer()==null,"grounding rod stores and supplies no power");
            check(record[0].export().devices().stream().noneMatch(p->level.getBlockState(p).getBlock() instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock b&&b.kind()==com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.HV_SOURCE),"isolated Tesla export branch contains no independent HV source");
            var exportNodes=record[0].export().nodes().toArray(com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode[]::new);
            com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.await(h,level,80,
                    ()->com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.topologyReadyWithKnownPorts(level,exportNodes),
                    ()->com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level),()->{
                check(coils.getFirst().receiveLightningStrike(),"one genuine strike charges one Tesla coil");
                double charged=coils.getFirst().energyJoules();long chargedAt=level.getGameTime();
                check(charged==5_000_000&&coils.subList(1,3).stream().allMatch(c->c.energyJoules()==0),"one strike supplies finite energy to only its struck coil");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,100,()->"real Tesla HV allocation absent; coil="+coils.getFirst().energyJoules()+"W="+coils.getFirst().lastSuppliedWatts()+" graph="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level),
                        ()->coils.getFirst().lastSuppliedWatts()>0&&coils.getFirst().lastSupplyElapsedTicks()>0,
                        ()->{var coil=coils.getFirst();double used=charged-coil.energyJoules();double expected=coil.lastSuppliedWatts()*coil.lastSupplyElapsedTicks()/20.0;
                            check(used>0&&coil.energyJoules()>=0&&Math.abs(used-expected)<1.0,"battery settlement equals actual delivered watts times native elapsed interval; used="+used+" expected="+expected);
                            check(level.getBlockState(record[0].export().devices().get(2)).hasProperty(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                                    &&level.getBlockState(record[0].export().devices().get(2)).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT),"actual exported HV allocation powers the real fixture lamp");
                            check(level.getGameTime()>chargedAt&&coils.stream().mapToDouble(TeslaMachineBlockEntity::energyJoules).sum()<charged,"finite coil charge decreases under real load without hidden recharge");
                            h.succeed();});
            });
        },origin,origin.offset(26,11,18));
    }
}
