package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.concurrent.atomic.AtomicBoolean;

/** Narrow native runtime checks for the arranged W/X yield and potency profiles. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics"})
public final class BotanyYieldPotencyPresetGameTests {
    private BotanyYieldPotencyPresetGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-yield-potency", "botany-w-yield"})
    public static void wYieldPresetHarvestsAndExtractsExpectedGenetics(GameTestHelper helper) {
        run(helper, "W", 7, 5);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-yield-potency", "botany-x-potency"})
    public static void xPotencyPresetHarvestsAndExtractsExpectedGenetics(GameTestHelper helper) {
        run(helper, "X", 3, 50);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics", "botany-sampling"})
    public static void acEndurancePresetAuthenticatedSamplePublishesHealthWarningContrast(GameTestHelper helper) {
        var species=ModSeedProfiles.seedId("wheat");
        var baseline=new GeneticValue(species,ModSeedProfiles.serverLookup(species));
        var ac=BotanyFixtureScenarios.wheatTraitPresets(baseline).endurance();
        var seedPacket=new net.minecraft.world.item.ItemStack(ModItems.WHEAT_SEEDS.get());
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper,seedPacket,"AC-endurance-sample",
                (h,fixture,epoch,hand,tray,pos,level,finished)->{
            try {
                var plant=tray.plantState();
                samplePreset(h,fixture,epoch,hand,tray,pos,level,finished,plant,ac,50,10,20);
                require((tray.warningBits()&2)!=0,"server-published AC tray appearance exposes low-health warning after real sampling");
                tray.commitPlant(baseline,level.getGameTime());
                plant=tray.plantState(); BotanyFixtureScenarios.prepareStageHolder(plant,baseline); tray.commitMutation();
                samplePreset(h,fixture,epoch,hand,tray,pos,level,finished,plant,baseline,100,60,70);
                require((tray.warningBits()&2)==0,"server-published V control appearance has no low-health warning after real sampling");
                finish(h,fixture,finished,null);
            } catch(Throwable failure) { finish(h,fixture,finished,failure); }
        });
    }

    private static void samplePreset(GameTestHelper h, RegisteredServerPlayerFixture fixture, long epoch, String hand,
            HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean finished,
            com.juicyslew.moonstation14.ms14.botany.PlantInstance plant, GeneticValue preset, float endurance,
            float minimum, float maximum) {
        var body=com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
        BotanyFixtureScenarios.prepareStageHolder(plant,preset);
        require(plant.age()==2 && plant.health()==endurance && plant.water()==80 && plant.nutrition()==80,
                "matched full-health age-2, 80/80 soil sample setup");
        var reference=plant.seedReference();
        var cutter=LooseItemEntity.create(level,body.position().add(0,.1,.8),new net.minecraft.world.item.ItemStack(ModItems.WIRECUTTER.get()));
        cutter.setNoGravity(true); require(level.addFreshEntity(cutter),"real wirecutter published");
        body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,cutter.getBoundingBox().getCenter());
        var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransfer.pickupSelected(fixture.player(),cutter.getUUID(),hand,hands.revision(),ignored->{})
                ==BodyHandItemTransfer.Result.SUCCESS,"real wirecutter normally acquired in active hand");
        var query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(epoch,3));
        var held=query.hands().stream().filter(value->value.id().equals(hand)).findFirst().orElseThrow();
        var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos);
        require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(epoch,2,query.revision(),hand,
                held.token(),pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE)
                )==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,
                "real authenticated wirecutter USE samples preset plant");
        require(plant.health()>=minimum && plant.health()<=maximum && !plant.dead(),"ordinary sample commits expected nonlethal health range");
        require(!reference.unique(),"first sample shares source reference through production commit");
        var packets=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8),
                e->!e.getItem().isEmpty() && e.getItem().is(ModItems.WHEAT_SEEDS.get())
                        && e.getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get())==reference);
        require(packets.size()==1,"real sample publishes exactly one matching seed packet");
        var override=packets.getFirst().getItem().get(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get());
        require(override!=null && override.health()==plant.health(),"published packet health matches committed source health");
        require((tray.warningBits()&2)==(plant.health()<=endurance/2?2:0),"tray appearance projection is published from actual sampled state");
    }

    private static void run(GameTestHelper helper, String station, int yield, int potency) {
        var base = ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat"));
        var presets = BotanyFixtureScenarios.wheatTraitPresets(new GeneticValue(ModSeedProfiles.seedId("wheat"), base));
        var expected = station.equals("W") ? presets.yield() : presets.potency();
        var seeds = new net.minecraft.world.item.ItemStack(ModItems.WHEAT_SEEDS.get());
        GeneticCarriers.attach(seeds, new PlantSeedReference(expected, true));
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, seeds, "preset-" + station,
                (h, fixture, epoch, hand, tray, pos, level, finished) -> harvest(h, fixture, epoch, hand, tray, pos,
                        level, finished, expected, yield, potency));
    }

    private static void harvest(GameTestHelper h, RegisteredServerPlayerFixture fixture, long epoch, String hand,
            HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean done,
            GeneticValue expected, int yield, int potency) {
        try {
            var plant = tray.plantState();
            require(plant.ready() && expected.equals(plant.genetics()), "domain setup retained scenario-factory profile");
            require(plant.effectiveProfile().lifecycle().harvest() == SeedProfile.Harvest.NO_REPEAT,
                    "preset uses ordinary no-repeat wheat lifecycle");
            var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
            var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty())
                    .stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
            var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
            require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                    null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE))
                    == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED, "ordinary authenticated harvest");
            var harvested = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                    e -> !e.getItem().isEmpty() && !before.contains(e.getUUID()));
            require(harvested.size() == yield, "preset controls actual harvest output count");
            var source = harvested.getFirst(); var reference = source.getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get());
            require(reference != null, "harvest output carries source reference");
            var target = source.getBoundingBox().getCenter();
            body.setPos(target.x + 1.5, target.y, target.z);
            aimAt(body, target);
            var selected = BodyItemTargetProbe.probeSelected(level, body, source.getUUID(), BodyItemTargetProbe.MAX_REACH);
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(selected != null, "harvest output not selected by native body eye-ray: source=" + source.getUUID()
                    + " pos=" + source.position() + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle()
                    + " pickupDelay=" + source.hasPickUpDelay() + " authority="
                    + com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()));
            require(BodyHandItemTransfer.pickupSelected(fixture.player(), source.getUUID(), hand, hands.revision(), ignored -> {})
                    == BodyHandItemTransfer.Result.SUCCESS, "normal pickup of harvested produce");
            var power = AirAlarmPowerFixture.place(h); BlockPos machinePos = h.absolutePos(new BlockPos(5, 12, 8));
            level.setBlock(machinePos, ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
            PowerFixtureReadiness.await(h, level, 80, () -> level.getBlockEntity(machinePos) instanceof SeedExtractorBlockEntity machine
                    && machine.isPowered() && com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine).deliveredWatts() == 1000,
                    () -> "extractor power allocation", () -> extract(h, fixture, epoch, hand, body, machinePos, reference, expected, potency, done));
        } catch (Throwable failure) { finish(h, fixture, done, failure); }
    }
    private static void aimAt(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.phys.Vec3 point) {
        var delta = point.subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }

    private static void extract(GameTestHelper h, RegisteredServerPlayerFixture fixture, long epoch, String hand,
            net.minecraft.world.entity.LivingEntity body, BlockPos pos, com.juicyslew.moonstation14.ms14.botany.PlantSeedReference reference,
            GeneticValue expected, int potency, AtomicBoolean done) {
        try {
            var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 4));
            var held = query.hands().stream().filter(x -> x.id().equals(hand)).findFirst().orElseThrow();
            var sourceToken = held.token();
            var sourceStack = held.presentation().stack().copy();
            body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
            var hit = RegisteredPlayerReadyDriver.aimAt(body, (ServerLevel) h.getLevel(), pos);
            require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 3, query.revision(), hand,
                    held.token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE))
                    == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED, "real extractor USE commits");
            var after = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 5));
            var sourceHand = after.hands().stream().filter(x -> x.id().equals(hand)).findFirst().orElseThrow();
            var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var remainingSource = liveHands.stackCopy(hand);
            require(!java.util.Objects.equals(sourceToken, sourceHand.token())
                    && remainingSource.map(stack -> !net.minecraft.world.item.ItemStack.isSameItemSameComponents(sourceStack, stack)).orElse(true),
                    "successful extraction consumes exactly the held source produce from its hand ledger");
            var packetStacks = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
            for (var outputHand : liveHands.handIds()) {
                var optionalStack = liveHands.stackCopy(outputHand);
                if (optionalStack.isEmpty()) continue;
                var stack = optionalStack.orElseThrow();
                if (stack.is(ModItems.WHEAT_SEEDS.get())) packetStacks.add(stack);
            }
            for (var packet : ((ServerLevel) h.getLevel()).getEntitiesOfClass(LooseItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(8),
                    e -> !e.getItem().isEmpty() && e.getItem().is(ModItems.WHEAT_SEEDS.get()))) {
                packetStacks.add(packet.getItem());
            }
            var extractorProfile = com.juicyslew.moonstation14.ms14.botany.ModBotanyMachineProfiles.serverCatalog().get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "seed_extractor"));
            require(packetStacks.size() >= extractorProfile.minimumPacketYield()
                    && packetStacks.size() <= extractorProfile.maximumPacketYield(),
                    "extractor publishes its configured packet yield across hands and world owners");
            for (var packet : packetStacks) {
                var packetReference = packet.get(ModDataComponents.PLANT_SEED_REFERENCE.get());
                var packetGenetics = packet.get(ModDataComponents.GENETIC_VALUE.get());
                require(packetReference != null, "published packet is missing its source reference");
                require(packetGenetics != null, "published packet is missing its genetic profile");
                require(packetReference.id().equals(reference.id()) && expected.equals(packetGenetics)
                        && packetGenetics.profile().lifecycle().potency() == potency,
                        "published packet carries source reference and expected genetics");
            }
            require(ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat")).lifecycle().yield() == 3
                    && ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat")).lifecycle().potency() == 5,
                    "resolved baseline wheat remains 3/5");
            finish(h, fixture, done, null);
        } catch (Throwable failure) { finish(h, fixture, done, failure); }
    }
    private static void finish(GameTestHelper h, RegisteredServerPlayerFixture fixture, AtomicBoolean done, Throwable failure) {
        if (!done.compareAndSet(false, true)) return;
        try { fixture.close(); } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        if (failure == null) h.succeed(); else h.testInfo.fail(failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
