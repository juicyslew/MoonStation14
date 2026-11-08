package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.ModEquipmentItems;
import com.juicyslew.moonstation14.entities.AnimalMob;
import com.juicyslew.moonstation14.entities.AnimalMobRegistration;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestTags({"hands", "storage", "equipment", "equipment-transfer", "equipment-quick-equip"})
@PrefixGameTestTemplate(false)
public final class BodyEquipmentTransferGameTests {
    private BodyEquipmentTransferGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags("equipment-quick-equip")
    public static void quickEquipEmptySlotAndRejectsStaleOrWrongItems(GameTestHelper helper) {
        Villager body=helper.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); helper.runAfterDelay(1,()->{
            LiveHands state=LiveHands.initialize(body).orElseThrow();
            put(body,"left","cap",new ItemStack(ModEquipmentItems.GREY_CAP.get()));
            state=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.quickEquipOwned(body,"left","cap",state.revision(),()->true)==BodyEquipmentTransfer.Result.SUCCESS,
                    "quick clothing fills resolved empty slot");
            state=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(state.stackCopy("left").isEmpty() && LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .anyMatch(s->s.stack().filter(x->x.is(ModEquipmentItems.GREY_CAP.get())).isPresent()),"cap ownership transferred");
            put(body,"right","wrong",new ItemStack(Items.DIAMOND));
            state=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.quickEquipOwned(body,"right","wrong",state.revision(),()->true)==BodyEquipmentTransfer.Result.DENIED,
                    "nonclothing denied");
            require(BodyEquipmentTransfer.quickEquipOwned(body,"left","cap",state.revision(),()->true)==BodyEquipmentTransfer.Result.DENIED,
                    "stale revision and token denied"); helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    @GameTestTags("equipment-quick-equip")
    public static void quickReplaceUniformDropsDependentsAndReturnsOldGarment(GameTestHelper helper) {
        Villager body=helper.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); helper.runAfterDelay(1,()->{
            LiveHands.initialize(body).orElseThrow();
            equipFixture(body,"left","old-uniform",new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()),"jumpsuit");
            equipFixture(body,"left","pocket",new ItemStack(ModItems.GAUZE.get(),10),"pocket1");
            equipFixture(body,"left","id",new ItemStack(ModEquipmentItems.BLANK_ID_CARD.get()),"id");
            put(body,"right","new-uniform",new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()));
            LiveHands before=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.quickEquipOwned(body,"right","new-uniform",before.revision(),()->true)==BodyEquipmentTransfer.Result.SUCCESS,
                    "uniform replacement succeeds");
            LiveHands after=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after.token("right").filter("old-uniform"::equals).isPresent()
                    && after.stackCopy("right").filter(s->s.is(ModEquipmentItems.GREY_JUMPSUIT.get())).isPresent()
                    && after.token("left").isEmpty(),"old uniform returns nominated hand in one transition");
            var slots=LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            require(slots.stream().filter(s->s.id().equals("pocket1")||s.id().equals("id"))
                    .allMatch(s->s.stack().isEmpty()),"pocket and ID slots recursively cleared");
            var drops=helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,body.getBoundingBox().inflate(8));
            require(drops.stream().anyMatch(e->e.getItem().is(ModItems.GAUZE.get())&&e.getItem().getCount()==10),"pocket full stack dropped");
            require(drops.stream().anyMatch(e->e.getItem().is(ModEquipmentItems.BLANK_ID_CARD.get())),"ID dropped");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"equipment", "equipment-quick-equip"})
    public static void quickEquipPrefersIdOverEmptyPockets(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands.initialize(body).orElseThrow();
            equipFixture(body, "left", "uniform", new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()), "jumpsuit");

            put(body, "left", "id-card", new ItemStack(ModEquipmentItems.BLANK_ID_CARD.get()));
            LiveHands held = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.quickEquipOwned(body, "left", "id-card", held.revision(), () -> true)
                            == BodyEquipmentTransfer.Result.SUCCESS,
                    "Z quick-equip puts ID card in its preferred ID slot");
            var slots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            require(slots.stream().filter(slot -> slot.id().equals("id"))
                            .anyMatch(slot -> slot.token().filter("id-card"::equals).isPresent()
                                    && slot.stack().filter(stack -> stack.is(ModEquipmentItems.BLANK_ID_CARD.get())).isPresent()),
                    "ID card token and stack are conserved in ID slot");
            require(slots.stream().filter(slot -> slot.id().equals("pocket1") || slot.id().equals("pocket2"))
                            .allMatch(slot -> slot.stack().isEmpty()), "Z leaves both pockets empty");
            LiveHands afterQuickEquip = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(afterQuickEquip.token("left").isEmpty(), "source hand is cleared");

            put(body, "right", "manual-card", new ItemStack(ModEquipmentItems.BLANK_ID_CARD.get()));
            LiveHands manual = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "right", "pocket1", "manual-card",
                            manual.revision(), true, () -> true) == BodyEquipmentTransfer.Result.SUCCESS,
                    "explicit manual equip remains allowed to put an ID card in a pocket");
            require(LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                            .filter(slot -> slot.id().equals("pocket1"))
                            .anyMatch(slot -> slot.token().filter("manual-card"::equals).isPresent()),
                    "manual pocket transfer preserves ownership");
            helper.succeed();
        });
    }

    private static void put(LivingEntity body,String hand,String token,ItemStack stack) {
        LiveHands before=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands next=before.putWhole(before.revision(),hand,new ItemToken(token),stack).state();
        require(LiveHandsCommitGateway.commitTrusted(body,before,next),"fixture ownership commit");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags("equipment-regression")
    public static void legacyHandEquipAndRemoveFilledBeltAfterSave(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack belt = PouchContents.put(new ItemStack(ModItems.BELT.get()), "child",
                    new ItemStack(Items.DIAMOND, 11)).orElseThrow();
            LiveHands occupied = initial.putWhole(0, "left", new ItemToken("belt"), belt).state()
                    .putWhole(1, "right", new ItemToken("other"), new ItemStack(Items.APPLE, 3)).state();
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
            CompoundTag oldTag = (CompoundTag) LiveHands.CODEC.encodeStart(ops, occupied).getOrThrow();
            oldTag.remove("equipment");
            LiveHands old = LiveHands.CODEC.parse(ops, oldTag).getOrThrow();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), old);
            require(!LiveHands.equipmentSnapshot(body).orElseThrow().persisted(), "old save has no equipment");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> false)
                    == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == old,
                    "lost authority preserves old snapshot");
            require(BodyEquipmentTransfer.equip(null, "left", "belt", "belt", 2)
                    == BodyEquipmentTransfer.Result.DENIED, "no active controller cannot equip");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "legacy equip succeeds");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(equipped.revision() == 3 && equipped.stackCopy("left").isEmpty()
                    && equipped.token("right").orElseThrow().equals("other")
                    && LiveHands.equipmentSnapshot(body).orElseThrow().persisted(), "hands preserved and layout materialized");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == equipped, "stale revision denied unchanged");
            require(BodyEquipmentTransfer.transferOwned(body, "right", "belt", "other", 3, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "occupied equipment denied");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "bad", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "wrong token denied");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "other", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "token already in other hand cannot move");
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "restored villager created");
            restored.load(body.saveWithoutId(new CompoundTag()));
            LiveHands saved = restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(saved != null && saved.revision() == 3 && saved.token("right").orElseThrow().equals("other")
                    && PouchContents.read(LiveHands.equipmentSnapshot(restored).orElseThrow().slots().stream()
                    .filter(slot -> slot.id().equals("belt")).findFirst().orElseThrow()
                    .stack().orElseThrow()).orElseThrow().child().orElseThrow().stack().getCount() == 11,
                    "save/load retains hands and belt child");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "unequip into empty hand");
            LiveHands removed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(removed.revision() == 4 && removed.token("left").orElseThrow().equals("belt")
                    && PouchContents.read(removed.stackCopy("left").orElseThrow()).orElseThrow()
                    .child().orElseThrow().stack().getCount() == 11
                     && LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                     .filter(slot -> slot.id().equals("belt")).findFirst().orElseThrow().stack().isEmpty(),
                    "filled belt moves intact without spilling child");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void onlyRegisteredEquipmentAndValidContents(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(body).orElseThrow();
            for (ItemStack candidate : List.of(new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(ModItems.BAG.get()))) {
                LiveHands held = empty.putWhole(0, "left", new ItemToken("test"), candidate).state();
                body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
                require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "test", 1, true, () -> true)
                        == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                        "foreign belt item rejected");
            }
            ItemStack hostile = new ItemStack(ModItems.BELT.get());
            hostile.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
            LiveHands held = empty.putWhole(0, "left", new ItemToken("hostile"), hostile).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "hostile", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                    "native container cannot enter equipment");
            ItemStack dual = PouchContents.put(new ItemStack(ModItems.BELT.get()), "child",
                    new ItemStack(Items.APPLE)).orElseThrow();
            dual.set(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
            LiveHands dualHeld = empty.putWhole(0, "left", new ItemToken("dual"), dual).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), dualHeld);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "dual", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == dualHeld,
                    "dual storage components cannot enter equipment");
            ItemStack gridBag = new ItemStack(ModItems.BAG.get());
            gridBag.set(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
            LiveHands gridHeld = empty.putWhole(0, "left", new ItemToken("grid"), gridBag).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), gridHeld);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "grid", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "valid grid bag accepted in back");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "grid", 2, false, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS
                    && GridContents.read(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                    .stackCopy("left").orElseThrow()).isPresent(), "grid bag unequips intact");
            LiveHands bag = empty.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), bag);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "registered bag accepted in back");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(CharacterControlSystem.applyStun(body, 40), "bound body accepts stun");
            require(BodyEquipmentTransfer.transferOwned(body, "right", "back", "bag", 2, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == equipped,
                    "stun blocks equipment action");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void wrongSlotFlagLeavesBodyUnchanged(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            require(LiveHands.equipmentSnapshot(body).orElseThrow().slots().size() == 15,
                    "body resolves every declared humanoid equipment slot");
            LiveHands held = initial.putWhole(0, "left", new ItemToken("wrong-slot"),
                    new ItemStack(ModEquipmentItems.GREY_CAP.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "shoes", "wrong-slot", held.revision(), true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                    "wrong clothing flag denies without changing body state");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void removingPrerequisiteDropsDependentsAndPreservesUnrelatedEquipment(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(LiveHands.initialize(body).isPresent()
                    && LiveHands.equipmentSnapshot(body).orElseThrow().slots().size() == 15,
                    "human inventory with all equipment slots initialized");
            equipFixture(body, "left", "jump", new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()), "jumpsuit");
            equipFixture(body, "left", "small-med", new ItemStack(ModItems.BRUTEPACK.get(), 10), "pocket1");
            equipFixture(body, "left", "card", new ItemStack(ModEquipmentItems.BLANK_ID_CARD.get()), "id");
            equipFixture(body, "left", "gloves", new ItemStack(ModEquipmentItems.BLACK_GLOVES.get()), "gloves");
            equipFixture(body, "left", "coat", new ItemStack(ModEquipmentItems.SIMPLE_COAT.get()), "outerClothing");

            ItemStack bag = new ItemStack(ModItems.BAG.get());
            bag.set(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get(),
                    GridContents.of(List.of(new GridContents.Entry("bag-child", new ItemStack(Items.DIAMOND, 3),
                            0, 0, com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0))));
            equipFixture(body, "right", "bag", bag, "back");

            LiveHands beforeCoatRemoval = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "right", "outerClothing", "coat",
                    beforeCoatRemoval.revision(), false, () -> true) == BodyEquipmentTransfer.Result.SUCCESS,
                    "remove outer clothing commits");
            var afterCoatSlots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            require(afterCoatSlots.stream().filter(s -> s.id().equals("suitstorage")).findFirst().orElseThrow()
                    .stack().isEmpty(), "suit storage remains empty with outer clothing removal");

            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(before.stackCopy("left").isEmpty(), "nominated hand is empty before prerequisite removal");
            String activeHandBefore = before.activeHand();
            require(BodyEquipmentTransfer.transferOwned(body, "left", "jumpsuit", "jump", before.revision(), false,
                    () -> true) == BodyEquipmentTransfer.Result.SUCCESS, "remove jumpsuit commits with dependent drops");
            LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var slots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            require(after.revision() == before.revision() + 1 && activeHandBefore.equals(after.activeHand())
                    && after.token("left").filter("jump"::equals).isPresent(), "prerequisite preserved in selected empty hand");
            for (String removed : List.of("jumpsuit", "pocket1", "id"))
                require(slots.stream().filter(s -> s.id().equals(removed)).findFirst().orElseThrow().stack().isEmpty(),
                        "dependency cleared " + removed);
            require(slots.stream().filter(s -> s.id().equals("gloves")).findFirst().orElseThrow().token().isPresent(),
                    "unrelated gloves remain worn");
            var back = slots.stream().filter(s -> s.id().equals("back")).findFirst().orElseThrow().stack().orElseThrow();
            require(GridContents.read(back).orElseThrow().entries().getFirst().stack().getCount() == 3,
                    "unrelated bag grid content remains unchanged");
            var dropped = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                    body.getBoundingBox().inflate(8));
            require(dropped.stream().anyMatch(e -> e.getItem().is(ModItems.BRUTEPACK.get()) && e.getItem().getCount() == 10
                            && e.position().distanceToSqr(body.position()) < 16),
                    "whole medical stack dropped");
            require(dropped.stream().anyMatch(e -> e.getItem().is(ModEquipmentItems.BLANK_ID_CARD.get())
                            && e.position().distanceToSqr(body.position()) < 16),
                    "dependent ID dropped");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"equipment-transfer", "equipment-regression"})
    public static void simpleCoatDeniesSuitStorageAndPreservesCase(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands.initialize(body).orElseThrow();
            equipFixture(body, "left", "coat", new ItemStack(ModEquipmentItems.SIMPLE_COAT.get()), "outerClothing");
            put(body, "left", "case", new ItemStack(ModEquipmentItems.UTILITY_CASE.get()));
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "left", "suitstorage", "case",
                    before.revision(), true, () -> true) == BodyEquipmentTransfer.Result.DENIED,
                    "simple coat does not permit suit storage");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left")
                            .filter("case"::equals).isPresent()
                    && LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                            .filter(s -> s.id().equals("suitstorage")).findFirst().orElseThrow().stack().isEmpty(),
                    "denied suit storage preserves case ownership and empty slot");
            LiveHands beforeRemoval = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "right", "outerClothing", "coat",
                    beforeRemoval.revision(), false, () -> true) == BodyEquipmentTransfer.Result.SUCCESS,
                    "coat removal succeeds without disturbing denied case");
            LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after.token("left").filter("case"::equals).isPresent()
                            && after.stackCopy("left").filter(stack -> stack.is(ModEquipmentItems.UTILITY_CASE.get())).isPresent()
                            && after.token("right").filter("coat"::equals).isPresent(),
                    "case and removed coat remain owned by their respective hands");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"equipment-transfer", "equipment-regression"})
    public static void ginormousUtilityCaseCannotEnterPocket(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands.initialize(body).orElseThrow();
            equipFixture(body, "left", "uniform", new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()), "jumpsuit");
            equipFixture(body, "left", "small-med", new ItemStack(ModItems.BRUTEPACK.get(), 10), "pocket1");
            put(body, "right", "case", new ItemStack(ModEquipmentItems.UTILITY_CASE.get()));
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(body, "right", "pocket1", "case",
                    before.revision(), true, () -> true) == BodyEquipmentTransfer.Result.DENIED,
                    "ginormous case denied despite satisfied pocket dependency");
            LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after.token("right").filter("case"::equals).isPresent()
                            && after.stackCopy("right").filter(stack -> stack.is(ModEquipmentItems.UTILITY_CASE.get())
                            && stack.getCount() == 1).isPresent(), "denial preserves case source stack");
            require(LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(slot -> slot.id().equals("pocket1"))
                    .anyMatch(slot -> slot.stack().filter(stack -> stack.is(ModItems.BRUTEPACK.get())
                            && stack.getCount() == 10).isPresent()), "existing small medical stack remains intact");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"animals", "character", "hands", "equipment"})
    public static void animalEquipmentTemplatesUseTypedAdmissionAndRespectHandCapability(GameTestHelper helper) {
        var kobold = spawnAnimal(helper, "kobold", new BlockPos(1, 1, 1));
        var pet = spawnAnimal(helper, "fox", new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            CharacterIdentitySystem.enrollSupportedActor(kobold, helper.getLevel());
            CharacterIdentitySystem.enrollSupportedActor(pet, helper.getLevel());
            LiveHands hands = LiveHands.initialize(kobold).orElseThrow(() -> new GameTestAssertException(
                    "kobold one-hand capability initializes"));
            require(hands.handIds().equals(List.of("left")), "resolved kobold retains exactly its configured single hand");
            var koboldTemplate = BodyEquipmentTransfer.templateForTransfer(kobold);
            require(koboldTemplate != null && koboldTemplate.slot("head").isPresent(),
                    "kobold resolves the shared monkey equipment template");
            put(kobold, "left", "kobold-cap", new ItemStack(ModEquipmentItems.GREY_CAP.get()));
            LiveHands before = kobold.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var fitSnapshot = LiveHands.equipmentSnapshot(kobold).orElse(null);
            var headSlot = koboldTemplate.slot("head").orElse(null);
            var sourceStack = before.stackCopy("left").orElseThrow();
            var itemData = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems
                    .catalog(helper.getLevel()).get(sourceStack.getItem().builtInRegistryHolder().key().location());
            var occupied = fitSnapshot == null ? java.util.Set.<String>of() : fitSnapshot.slots().stream()
                    .filter(candidate -> candidate.stack().isPresent())
                    .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(kobold),
                    "kobold is admitted to act before equipment transfer");
            require(before.compatible(kobold), "kobold hand attachment is compatible before equipment transfer");
            require(kobold.isAlive() && !kobold.isRemoved(), "kobold is live before equipment transfer");
            require(fitSnapshot != null && headSlot != null && fitSnapshot.slots().stream()
                            .anyMatch(candidate -> candidate.id().equals("head") && candidate.stack().isEmpty()
                                    && candidate.token().isEmpty()),
                    "resolved head slot exists and is empty; snapshot=" + fitSnapshot + ", slot=" + headSlot);
            require(itemData != null && itemData.clothing().isPresent(),
                    "grey cap resolves clothing equipment data; data=" + itemData);
            require(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(
                            helper.getLevel(), koboldTemplate, "head", sourceStack, occupied),
                    "grey cap fits resolved kobold head slot; data=" + itemData + ", slot=" + headSlot
                            + ", occupied=" + occupied);
            var equipResult = BodyEquipmentTransfer.transferOwned(kobold, "left", "head", "kobold-cap",
                    before.revision(), true, () -> true);
            require(equipResult == BodyEquipmentTransfer.Result.SUCCESS,
                    "one-handed kobold equips through shared equipment transfer; result=" + equipResult
                            + ", hand=" + before.handIds() + ", source=" + before.stackCopy("left")
                            + ", resolvedTemplateSlots=" + koboldTemplate.slots().stream()
                            .map(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.Slot::id).toList()
                            + ", representableBefore=" + com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService
                            .representable(before, kobold));
            var equipped = LiveHands.equipmentSnapshot(kobold).orElseThrow();
            require(equipped.slots().stream().anyMatch(slot -> slot.id().equals("head")
                    && slot.stack().filter(stack -> stack.is(ModEquipmentItems.GREY_CAP.get())).isPresent()),
                    "equipped item is materialized in the resolved head slot");
            LiveHands afterEquip = kobold.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquipmentTransfer.transferOwned(kobold, "left", "head", "kobold-cap",
                            afterEquip.revision(), false, () -> true) == BodyEquipmentTransfer.Result.SUCCESS
                            && kobold.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).stackCopy("left")
                            .filter(stack -> stack.is(ModEquipmentItems.GREY_CAP.get())).isPresent(),
                    "same one-hand actor removes/equips back into its only hand for ordinary item use");
            require(CharacterIdentitySystem.resolveForActor(pet).orElseThrow()
                    .component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty(),
                    "pet fixture is handless by resolved prototype");
            var petTemplate = BodyEquipmentTransfer.templateForTransfer(pet);
            require(petTemplate != null, "handless pet still resolves its inventory layout");
            var tank = GasTankItem.createStack(helper.getLevel(), ModGasTanks.PLASMA_ID);
            require(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(
                    helper.getLevel(), petTemplate, "suitstorage", tank, java.util.Set.of()),
                    "typed GasTank item is admitted by pet suit-storage whitelist without a fake tag");
            require(!com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(
                    helper.getLevel(), petTemplate, "suitstorage", new ItemStack(Items.DIAMOND), java.util.Set.of()),
                    "untyped item is rejected by the pet suit-storage component whitelist");
            require(!com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(
                    helper.getLevel(), petTemplate, "mask", new ItemStack(ModEquipmentItems.BLACK_BANDANA.get()), java.util.Set.of()),
                    "pet mask whitelist rejects unsupported bandana absent PetWearable provenance");
            helper.succeed();
        });
    }

    private static AnimalMob spawnAnimal(GameTestHelper helper, String model, BlockPos relative) {
        var holder = AnimalMobRegistration.types().get(model);
        require(holder != null, "registered animal host exists: " + model);
        AnimalMob body = holder.get().create(helper.getLevel());
        require(body != null, "animal host entity created: " + model);
        body.moveTo(helper.absolutePos(relative).getX() + .5, helper.absolutePos(relative).getY(),
                helper.absolutePos(relative).getZ() + .5, 0, 0);
        require(helper.getLevel().addFreshEntity(body), "animal host enters live server level: " + model);
        return body;
    }

    private static void equipFixture(Villager body, String hand, String token, ItemStack stack, String slot) {
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands held = current.putWhole(current.revision(), hand, new ItemToken(token), stack).state();
        require(LiveHandsCommitGateway.commitTrusted(body, current, held), "fixture stack committed " + token);
        current = held;
        require(BodyEquipmentTransfer.transferOwned(body, hand, slot, token, current.revision(), true, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS
                && com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.representable(
                        body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), body),
                "fixture item equipped and snapshot-representable " + slot);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
