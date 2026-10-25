package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.hands.live.PuddleTargetProbe;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestTags({"timed-interaction", "ingestion", "handless-ingestion", "mouse-ghost-roles"})
@PrefixGameTestTemplate(false)
public final class HandlessIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> NUTRIMENT =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "nutriment"));

    private HandlessIngestionGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 80)
    public static void handlessAimedBeakerUsesResolvedDrinkProfile(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse created");
        mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(mouse) && CharacterIdentitySystem.enroll(mouse, level,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse")) && BodySystem.reconcile(mouse),
                "handless mouth-capable body initialized");
        Vec3 target = new Vec3(pos.getX() + 2.2, pos.getY(), pos.getZ() + .5);
        level.setBlock(BlockPos.containing(target).below(), Blocks.STONE.defaultBlockState(), 3);
        var drink = LooseItemEntity.create(level, target, new ItemStack(ModItems.BEAKER.get()));
        drink.getItem().set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 6f)));
        require(level.addFreshEntity(drink), "beaker source spawned");
        face(mouse, drink);
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(level, mouse,
                        drink.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH) != null,
                "beaker is selected by the same authoritative item ray required by the world oral request");
        var admission = TimedIngestionAccess.admitWorld(mouse, drink.getUUID(), null, 0, () -> true);
        require(admission.isPresent(), "handless world drink admission resolves; noHands="
                + (mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == null) + " delay=" + drink.hasPickUpDelay()
                + " source=" + drink.getItem().get(ModDataComponents.REAGENT.get()));
        var start = BodyTimedIngestionService.startWorldOwned(mouse, drink.getUUID(), null, 0, 120, () -> true);
        require(start == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                "handless loose-liquor oral request is admitted; reason=" + start + " resolvedDrink="
                        + ((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) drink.getItem().getItem())
                        .drinkSolution(drink.getItem(), level));
        var operation = mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(op -> op.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
        require(operation.durationTicks() == 20 && drink.getItem().get(ModDataComponents.REAGENT.get()) != null
                        && ReagentUnits.fromFloat(drink.getItem().get(ModDataComponents.REAGENT.get()).contents().get(WATER)) == 600,
                "world-item drink resolves the beaker's 5u/20-tick policy without synthetic hands");
        h.runAfterDelay(22, () -> {
            require(ReagentUnits.fromFloat(drink.getItem().get(ModDataComponents.REAGENT.get()).contents().get(WATER)) == 100,
                    "timed handless drink consumes the resolved 5u serving");
            require(mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == null,
                    "handless oral path never fabricates hands");
            h.succeed();
        });
    }

    private static void setThirst(net.minecraft.world.entity.Entity entity, float value) {
        var bridge = com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST;
        var before = com.juicyslew.moonstation14.ms14.MS14Provider.get(entity, bridge).thirst();
        com.juicyslew.moonstation14.ms14.MS14Provider.updateIfChanged(entity, bridge,
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(before),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(value)));
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void mouseEatsPrototypeInitializedCheeseSlice(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,1,3));
        require(ModItems.CHEESE_SLICE.get().getDefaultMaxStackSize() == 1,
                "each cheese slice is a non-stackable portion");
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var mouse=ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"mouse created"); mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPersistenceRequired();
        mouse.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(mouse)&&CharacterIdentitySystem.enroll(mouse,level,
                ResourceLocation.fromNamespaceAndPath("moonstation14","mouse"))&&BodySystem.reconcile(mouse),"mouse enrolled");
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        setThirst(mouse, 35f);
        require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                "Mouse uses its resolved 35-unit hydration cap");
        Vec3 at=new Vec3(pos.getX()+2.2,pos.getY(),pos.getZ()+.5);
        level.setBlock(BlockPos.containing(at).below(),Blocks.STONE.defaultBlockState(),3);
        var item=LooseItemEntity.create(level,at,new ItemStack(ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(item),"prototype cheese spawned"); face(mouse,item);
        require(item.getItem().get(ModDataComponents.REAGENT.get())==null,"fresh cheese begins without hardcoded reagent data");
        var acceptedCents = new java.util.concurrent.atomic.AtomicLong(-1);
        AutoCloseable receiptSubscription = IngestionReceiptObserver.observe(receipt -> {
            if (receipt.bodyId().equals(mouse.getUUID()) && receipt.sourceKey().equals("world:" + item.getUUID()))
                acceptedCents.set(receipt.acceptedCents());
        });
        waitForNonMetabolismCompletionTick(h, mouse, () -> {
            require(BodyTimedIngestionService.startWorldOwned(mouse,item.getUUID(),null,0,101,()->true).name().equals("OK"),
                    "resolved FOOD remains edible at maximum hydration");
            require(ReagentUnits.fromFloat(item.getItem().get(ModDataComponents.REAGENT.get()).contents().get(NUTRIMENT))==375,
                    "resolved cheese prototype initializes 3.75 units exactly once");
            h.runAfterDelay(21,()->{
                require(item.isRemoved(),"fully consumed cheese is destroyed");
                require(acceptedCents.get()==375,
                        "timed mouse ingestion transferred the full 3.75-unit source");
                try { receiptSubscription.close(); } catch (Exception e) { throw new GameTestAssertException("receipt observer cleanup failed: " + e); }
                long stomachNutriment = ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider.get(mouse,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(NUTRIMENT,0f));
                require(stomachNutriment==375,
                        "timed mouse ingestion transferred the full 3.75-unit source before metabolism; stomach nutriment="
                                + stomachNutriment + ", source removed=" + item.isRemoved());
                h.succeed();
            });
        });
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void mouseEatsPumpkinPieAtMaximumHydrationAndLeavesWorldRemainder(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,1,3));
        require(ModItems.PUMPKIN_PIE_SLICE.get().getDefaultMaxStackSize()==1,"each pie slice is an independent portion");
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var mouse=ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"mouse created"); mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPersistenceRequired();
        mouse.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(mouse)&&CharacterIdentitySystem.enroll(mouse,level,
                ResourceLocation.fromNamespaceAndPath("moonstation14","mouse"))&&BodySystem.reconcile(mouse),"mouse enrolled");
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        setThirst(mouse,35f);
        Vec3 at=new Vec3(pos.getX()+2.2,pos.getY(),pos.getZ()+.5);
        level.setBlock(BlockPos.containing(at).below(),Blocks.STONE.defaultBlockState(),3);
        var item=LooseItemEntity.create(level,at,new ItemStack(ModItems.PUMPKIN_PIE_SLICE.get()));
        require(level.addFreshEntity(item),"pie slice spawned"); face(mouse,item);
        require(BodyTimedIngestionService.startWorldOwned(mouse,item.getUUID(),null,0,109,()->true).name().equals("OK"),
                "resolved FOOD is admitted at maximum hydration without synthetic hands");
        require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                "food admission succeeds while the Mouse is at hydration cap");
        require(item.getItem().getCount()==1,"world source is a separate nonstackable serving");
        var initial=item.getItem().get(ModDataComponents.REAGENT.get());
        require(initial!=null,"bundled resolved profile initializes source on admission");
        require(ReagentUnits.fromFloat(initial.contents().get(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14","nutriment"))))==750
                &&ReagentUnits.fromFloat(initial.contents().get(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14","vitamin"))))==350
                &&ReagentUnits.fromFloat(initial.contents().get(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14","sugar"))))==125,"initial source is 7.5u Nutriment, 3.5u Vitamin, 1.25u Sugar");
        h.runAfterDelay(21,()->{
            require(item.isAlive()&&item.getItem().getCount()==1,"portion remains as the original world item after partial dose");
            var remaining=item.getItem().get(ModDataComponents.REAGENT.get());
            require(remaining.contents().values().stream().mapToLong(ReagentUnits::fromFloat).sum()==725
                    &&remaining.contents().size()==3,
                    "one 5u bite removes 500 cents proportionally from mixed food; remaining source=" + remaining.contents());
            require(mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())==null,"world eating never creates hands");
            h.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void mouseConsumesOneOfSixteenIndependentCheesePortions(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,1,3));
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var mouse=ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"mouse created"); mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPersistenceRequired();
        mouse.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(mouse)&&CharacterIdentitySystem.enroll(mouse,level,
                ResourceLocation.fromNamespaceAndPath("moonstation14","mouse"))&&BodySystem.reconcile(mouse),"mouse enrolled");

        var portions=new java.util.ArrayList<LooseItemEntity>(16);
        for (int i=0;i<16;i++) {
            ItemStack stack=new ItemStack(ModItems.CHEESE_SLICE.get());
            require(stack.getCount()==1 && stack.getMaxStackSize()==1,"native cheese stacks contain one portion");
            var at=new Vec3(pos.getX()+2.2,pos.getY(),pos.getZ()+.5);
            level.setBlock(BlockPos.containing(at).below(),Blocks.STONE.defaultBlockState(),3);
            var portion=LooseItemEntity.create(level,at,stack);
            require(level.addFreshEntity(portion),"one of sixteen cheese portions spawned");
            portions.add(portion);
            com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(portion.getItem(), level);
            require(ReagentUnits.fromFloat(portion.getItem().get(ModDataComponents.REAGENT.get()).contents().get(NUTRIMENT))==375,
                    "each fresh item stack receives its own 3.75-unit prototype source");
        }

        var first=portions.get(0); face(mouse,first);
        require(BodyTimedIngestionService.startWorldOwned(mouse,first.getUUID(),null,0,101,()->true).name().equals("OK"),
                "timed ingestion starts for exactly the selected portion");
        h.runAfterDelay(21,()->{
            require(first.isRemoved(),"selected portion is consumed");
            for(int i=1;i<portions.size();i++) {
                var untouched=portions.get(i);
                require(untouched.isAlive(),"other portion entity remains: "+i);
                require(untouched.getItem().getCount()==1,"other portion remains a single item: "+i);
                require(ReagentUnits.fromFloat(untouched.getItem().get(ModDataComponents.REAGENT.get()).contents().get(NUTRIMENT))==375,
                        "other portion retains its complete independent nutriment source: "+i);
            }
            h.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void mouseWithoutHandsCompletesWorldIngestionButHumanMissingHandsIsRejected(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(3,1,3));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(),3);
        var mouse=ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"mouse created"); mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPersistenceRequired(); mouse.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(mouse)&&CharacterIdentitySystem.enroll(mouse,level,
                ResourceLocation.fromNamespaceAndPath("moonstation14","mouse")),"mouse enrolled from resolved prototype");
        require(BodySystem.reconcile(mouse)&&OralAccess.handless(mouse),"resolved Mouse has oral body/stomach and no Hands capability");
        require(mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())==null,"no phantom live hands initialized");
        var probeItem=source(level,pos);
        face(mouse,probeItem);
        var admitted=TimedIngestionAccess.admitWorld(mouse,probeItem.getUUID(),null,0,()->true).orElseThrow();
        require(TimedIngestionAccess.valid(mouse,admitted,()->true),"handless admission validates its exact live source");
        mouse.setYRot(90); mouse.setYHeadRot(90); mouse.setXRot(0);
        require(TimedIngestionAccess.valid(mouse,admitted,()->true),
                "an admitted bite continues when the body looks away; gaze is only an admission-time selection rule");
        face(mouse,probeItem);
        BlockPos door = pos.offset(1,0,0);
        var closedDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false);
        level.setBlock(door, closedDoor.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), closedDoor.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),
                "a closed door still invalidates the loaded collider-reach predicate");
        level.setBlock(door, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), 3);
        probeItem.setTarget(java.util.UUID.randomUUID());
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),"a newly claimed source invalidates continuation");
        probeItem.setTarget(null);
        Vec3 original=probeItem.position();
        probeItem.setPos(original.x+8,original.y,original.z);
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),"handless validation still rejects an out-of-reach source");
        probeItem.discard();
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),"removed source cannot retain a handless admission");
        var item=source(level,pos);
        face(mouse,item);
        require(BodyTimedIngestionService.startWorldOwned(mouse,item.getUUID(),null,0,1,()->true).name().equals("OK"),
                "handless body starts ordinary timed world ingestion");
        mouse.setYRot(90); mouse.setYHeadRot(90); mouse.setXRot(0);
        require(water(item)==1000&&stomach(mouse)==0,"start does not consume source or dose stomach");
        h.runAfterDelay(21,()->{
            require(water(item)==500,"existing timed ingestion transaction consumes exactly the resolved source dose; stomach="
                    +stomach(mouse)+", operation="+mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations());
            require(mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())==null,"completion never creates hands");
            LivingEntity human=EntityType.VILLAGER.create(level);
            require(human!=null,"human created"); human.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            require(level.addFreshEntity(human)&&CharacterIdentitySystem.enroll(human,level,ModCharacters.HUMAN_ID)
                    &&BodySystem.reconcile(human),"human enrolled");
            require(!OralAccess.handless(human),"resolved Human hands capability remains mandatory");
            require(TimedIngestionAccess.admitWorld(human,item.getUUID(),null,0,()->true).isEmpty(),
                    "missing live attachment never makes handed Human handless");
            h.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void standalonePigWithoutOralAnatomyIsRejected(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,1,3));
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var pig=EntityType.PIG.create(level);
        require(pig!=null,"pig created"); pig.setNoAi(true); pig.setNoGravity(true); pig.setPersistenceRequired(); pig.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(pig)&&CharacterIdentitySystem.enroll(pig,level,ResourceLocation.fromNamespaceAndPath("moonstation14","pig")),"pig enrolled");
        require(!OralAccess.handless(pig),"standalone Pig does not inherit oral supported-animal anatomy");
        var item=source(level,pos);
        require(TimedIngestionAccess.admitWorld(pig,item.getUUID(),null,0,()->true).isEmpty(),
                "Pig without Mouth/Stomach fails closed");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    public static void handlessMouseCompletesTimedPuddleDrink(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,1,3));
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var mouse=ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"mouse created"); mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPersistenceRequired(); mouse.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        require(level.addFreshEntity(mouse)&&CharacterIdentitySystem.enroll(mouse,level,ResourceLocation.fromNamespaceAndPath("moonstation14","mouse"))
                &&BodySystem.reconcile(mouse),"mouth/stomach mouse enrolled without hands");
        var mouseThirstPolicy = CharacterIdentitySystem.resolveForActor(mouse)
                .flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent.class))
                .orElseThrow();
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        setThirst(mouse, 35f);
        BlockPos puddlePos=pos.offset(1,0,0); level.setBlock(puddlePos.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(puddlePos,ModBlocks.PUDDLE.get().defaultBlockState(),3);
        require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity,"puddle placed");
        var puddle=(PuddleBlockEntity)level.getBlockEntity(puddlePos);
        com.juicyslew.moonstation14.ms14.MS14Provider.update(puddle,com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT,
                new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(Map.of(WATER,10f)));
        facePuddle(mouse,puddlePos);
        require(TimedIngestionAccess.admitPuddle(mouse,puddlePos,null,0,()->true).isEmpty(),
                "handless Mouse cannot begin drinking at its resolved maximum hydration");
        setThirst(mouse, 25f);
        require(PuddleTargetProbe.selected(level,mouse,puddlePos),"mouse can select adjacent puddle: eye="+mouse.getEyePosition()+" look="+mouse.getLookAngle());
        var admitted=TimedIngestionAccess.admitPuddle(mouse,puddlePos,null,0,()->true).orElseThrow();
        require(TimedIngestionAccess.valid(mouse,admitted,()->true),"handless puddle admission validates source");
        level.setBlock(puddlePos,Blocks.AIR.defaultBlockState(),3);
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),"handless puddle validator rejects removed exact block entity");
        level.setBlock(puddlePos,ModBlocks.PUDDLE.get().defaultBlockState(),3);
        require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity,"replacement puddle created");
        puddle=(PuddleBlockEntity)level.getBlockEntity(puddlePos);
        com.juicyslew.moonstation14.ms14.MS14Provider.update(puddle,com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT,
                new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(Map.of(WATER,10f)));
        require(!TimedIngestionAccess.valid(mouse,admitted,()->true),"replacement puddle cannot reuse old admission identity");
        facePuddle(mouse,puddlePos);
        require(BodyTimedIngestionService.startPuddleOwned(mouse,puddlePos,null,0,3,()->true).name().equals("OK"),
                "handless puddle ingestion starts through existing timed consumer");
        require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.satiate(mouse, level, 10f, 1f)
                        == com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.Outcome.APPLIED
                        && com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                "canonical thirst effect reaches Mouse's resolved cap during the pending action");
        var finalPuddle=puddle;
        int repeatDelay = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.serverLookup(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "puddle")).delayTicks();
        // Observe cancellation before the timed-operation journal's terminal entry ages out.
        // Five ticks span multiple 3-tick repeat intervals without crossing thirst decay.
        h.runAfterDelay(5,()->{
            long remaining=ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider.get(finalPuddle,
                    com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).getMap().getOrDefault(WATER,0f));
            require(remaining==1000,"reaching resolved hydration cap cancels pending drink before source transfer");
            require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                            .anyMatch(o -> o.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED),
                    "pending handless drink reaches terminal cancelled state, with no repeat");
            require(mouse.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())==null,"puddle completion creates no live hands");
            h.runAfterDelay(repeatDelay * 2 + 5, () -> {
                float thirst = com.juicyslew.moonstation14.ms14.MS14Provider.get(mouse,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST).thirst();
                require(thirst < mouseThirstPolicy.overHydrated()
                                && !com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                        "production Mouse thirst decay naturally drops below its resolved cap");
                long afterDecay = ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider.get(finalPuddle,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).getMap().getOrDefault(WATER,0f));
                require(afterDecay == remaining,
                        "no unintended drink or repeat occurs after natural thirst decay below maximum");
                require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                                .noneMatch(o -> o.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE),
                        "cancelled drink remains idle without issuing another oral request");
                h.succeed();
            });
        });
    }

    private static LooseItemEntity source(net.minecraft.server.level.ServerLevel level,BlockPos p) {
        ItemStack stack=new ItemStack(ModItems.BOTTLE.get());
        stack.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(WATER,10f)));
        Vec3 at=new Vec3(p.getX()+2.2,p.getY(),p.getZ()+.5);
        level.setBlock(BlockPos.containing(at).below(),Blocks.STONE.defaultBlockState(),3);
        var item=LooseItemEntity.create(level,at,stack); require(level.addFreshEntity(item),"reagent bottle spawned"); return item;
    }
    private static void face(LivingEntity body,LooseItemEntity item) {
        body.setYRot(-90); body.setYHeadRot(-90);
        body.setXRot((float)Math.toDegrees(Math.atan2(body.getEyeY()-item.getBoundingBox().getCenter().y,
                Math.sqrt(Math.pow(body.getX()-item.getX(),2)+Math.pow(body.getZ()-item.getZ(),2)))));
    }
    private static void waitForNonMetabolismCompletionTick(GameTestHelper helper, LivingEntity body, Runnable action) {
        long start = helper.getLevel().getGameTime();
        var metabolism = com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM;
        if (!metabolism.isDue(start + 20, body.getId())
                && !metabolism.isDue(start + 21, body.getId()))
            action.run();
        else helper.runAfterDelay(1, () -> waitForNonMetabolismCompletionTick(helper, body, action));
    }
    private static void facePuddle(LivingEntity body,BlockPos pos) {
        var bounds=body.level().getBlockState(pos).getShape(body.level(),pos,net.minecraft.world.phys.shapes.CollisionContext.of(body)).bounds();
        Vec3 target=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,pos.getY()+(bounds.minY+bounds.maxY)/2,
                pos.getZ()+(bounds.minZ+bounds.maxZ)/2);
        Vec3 delta=target.subtract(body.getEyePosition());
        body.setYRot((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
        body.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        body.setYHeadRot(body.getYRot()); body.setYBodyRot(body.getYRot());
    }
    private static long water(LooseItemEntity item) {
        var c=item.getItem().get(ModDataComponents.REAGENT.get()); return c==null?0:ReagentUnits.fromFloat(c.contents().getOrDefault(WATER,0f));
    }
    private static long stomach(LivingEntity body) {
        return ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider.get(body,
                com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(WATER,0f));
    }
    private static void require(boolean condition,String message) { if(!condition) throw new GameTestAssertException(message); }
}
