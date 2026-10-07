package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;


/** Live-world coverage for profile effects and fuse behavior of loose entities. */
public final class LooseObjectEffectsGameTests {
    private LooseObjectEffectsGameTests() { }

    public static void jugEffects(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        // Collision sweeps deliberately refuse to inspect unloaded chunks; the fixture owns this neighborhood.
        for (int cx = net.minecraft.world.level.ChunkPos.getX(floor.getX() - 2); cx <= net.minecraft.world.level.ChunkPos.getX(floor.getX() + 2); cx++)
            for (int cz = net.minecraft.world.level.ChunkPos.getZ(floor.getZ() - 2); cz <= net.minecraft.world.level.ChunkPos.getZ(floor.getZ() + 2); cz++)
                level.getChunk(cx, cz);
        ItemStack emptyJug = new ItemStack(ModItems.JUG.get());
        LooseItemEntity airborne = add(h, new Vec3(floor.getX()+.5, floor.getY()+2, floor.getZ()+.5), emptyJug);
        airborne.launch(new Vec3(0, -.5, 0), 0);
        airborne.tick();
        require(!airborne.isRemoved(), "sub-threshold airborne motion does not break jug");
        LooseItemEntity ordinary = add(h, new Vec3(floor.getX()+.5, floor.getY()+1, floor.getZ()+.5), new ItemStack(Blocks.STONE.asItem()));
        ordinary.launch(new Vec3(0, -1, 0), 0);
        ordinary.tick();
        require(!ordinary.isRemoved(), "ordinary item has no jug impact effect");

        LooseItemEntity emptyHit = add(h, new Vec3(floor.getX()+.5, floor.getY()+2, floor.getZ()+.5), emptyJug);
        double impactStartY = emptyHit.getY();
        emptyHit.launch(new Vec3(0, -5, 0), 0);
        emptyHit.tick();
        require(emptyHit.getY() < impactStartY, "empty jug moved through the loaded sweep into floor contact");
        require(emptyHit.isRemoved(), "above-threshold empty jug contact breaks it");
        var water = ModReagents.createKey("water");
        ItemStack fullJug = new ItemStack(ModItems.JUG.get());
        fullJug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(java.util.Map.of(water, 5f)));
        LooseItemEntity fullHit = add(h, new Vec3(floor.getX()+.5, floor.getY()+2, floor.getZ()+.5), fullJug);
        fullHit.launch(new Vec3(0, -5, 0), 0);
        fullHit.tick();
        BlockPos puddlePos = floor.above();
        require(fullHit.isRemoved(), "filled jug impacting floor is removed after spilling");
        require(level.getBlockState(puddlePos).is(ModBlocks.PUDDLE.get()), "filled jug creates puddle above support cell");
        require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity, "spill puddle has reagent storage");
        var puddleContents = MS14Provider.getDetached(level.getBlockEntity(puddlePos), MS14Bridges.REAGENT);
        require(puddleContents.getMap().size() == 1 && Math.abs(puddleContents.getMap().getOrDefault(water, 0f) - 5f) < .001f,
                "puddle conserves exactly five water units");
        airborne.discard(); ordinary.discard();
        h.succeed();
    }

    public static void grenadeFuseSurvivesLooseEntity(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        long deadline = level.getGameTime() + 35;
        ItemStack stack = new ItemStack(ModItems.GRENADE.get());
        stack.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED, deadline, false, false));
        LooseItemEntity grenade = add(h, new Vec3(floor.getX()+.5, floor.getY()+1.2, floor.getZ()+.5), stack);
        grenade.setDeltaMovement(Vec3.ZERO);
        CompoundTag saved = grenade.saveWithoutId(new CompoundTag());
        LooseItemEntity loaded = ModEntities.LOOSE_ITEM.get().create(level);
        require(loaded != null, "loose entity factory available");
        loaded.load(saved);
        loaded.setPos(grenade.position());
        GrenadeFuseData restored = loaded.getItem().get(ModDataComponents.GRENADE_FUSE.get());
        require(restored != null && !restored.beepedAtPrime() && !restored.beepedAtForty(), "NBT immediately preserves pre-beep flags");
        grenade.discard();
        h.runAfterDelay(1, () -> {
            require(level.addFreshEntity(loaded), "reloaded grenade added");
            loaded.tick();
            GrenadeFuseData state = loaded.getItem().get(ModDataComponents.GRENADE_FUSE.get());
            require(state != null && state.phase() == GrenadeFuse.Phase.ARMED && state.deadline() == deadline,
                    "NBT reload preserves armed fuse deadline");
            require(state.beepedAtPrime() && !state.beepedAtForty(), "initial evaluation primes without forty-tick beep");
            h.runAfterDelay(16, () -> {
            loaded.tick();
            GrenadeFuseData forty = loaded.getItem().get(ModDataComponents.GRENADE_FUSE.get());
            require(forty != null && forty.beepedAtForty() && forty.deadline() == deadline,
                    "forty-tick milestone preserves original deadline");
            h.runAfterDelay(20, () -> {
            loaded.tick();
            require(loaded.isRemoved(), "expired loose grenade removed");
            // A second direct tick cannot repeat the detonation after removal.
            loaded.tick();
            h.succeed();
            });
            });
        });
    }

    public static void horizontalFloorContactBreaksAndSpills(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        for (int x=0;x<5;x++) for (int z=0;z<5;z++) level.setBlock(floor.offset(x,0,z), Blocks.STONE.defaultBlockState(), 3);
        ItemStack jug = waterJug();
        LooseItemEntity entity = add(h, new Vec3(floor.getX()+2.5, floor.getY()+1.001, floor.getZ()+2.5), jug);
        entity.launch(new Vec3(.75,-.02,0),0);
        for (int i=0;i<3 && !entity.isRemoved();i++) entity.tick();
        require(entity.isRemoved(), "horizontal travel contacting floor breaks jug despite low vertical speed");
        BlockPos impactCenter=BlockPos.containing(entity.getBoundingBox().getCenter());
        require(Math.abs(impactCenter.getX()-(floor.getX()+2))<=1 && Math.abs(impactCenter.getZ()-(floor.getZ()+2))<=1,
                "jug contact remains within fixture floor footprint");
        assertPuddle(h, new BlockPos(impactCenter.getX(), floor.getY()+1, impactCenter.getZ()));
        h.succeed();
    }

    public static void horizontalWallContactSpillsAtNearSideFloor(GameTestHelper h) {
        var level=h.getLevel(); BlockPos base=h.absolutePos(new BlockPos(2,0,2));
        for(int x=0;x<5;x++) for(int z=0;z<5;z++) level.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        BlockPos wall=base.offset(3,1,2);
        for(int y=1;y<=4;y++) level.setBlock(wall.offset(0,y-1,0),Blocks.STONE.defaultBlockState(),3);
        LooseItemEntity entity=add(h,new Vec3(base.getX()+2.4,base.getY()+3,base.getZ()+2.5),waterJug());
        entity.launch(new Vec3(.75,0,0),0);
        for(int i=0;i<3&&!entity.isRemoved();i++) entity.tick();
        require(entity.isRemoved(),"horizontal wall contact breaks jug");
        assertPuddle(h,base.offset(2,1,2));
        require(!level.getBlockState(base.offset(4,1,2)).is(ModBlocks.PUDDLE.get()),"spill remains on near side, not beyond wall");
        h.succeed();
    }

    public static void fastAirborneJugDoesNotBreak(GameTestHelper h) {
        LooseItemEntity entity=add(h,h.absoluteVec(new Vec3(2.5,5,2.5)),waterJug());
        entity.launch(new Vec3(.75,0,0),0);
        entity.tick();
        require(!entity.isRemoved() && entity.getItem().get(ModDataComponents.REAGENT.get()).centContents().values().iterator().next()==1000L,
                "fast airborne travel without contact preserves jug and contents");
        entity.discard(); h.succeed();
    }

    private static ItemStack waterJug() {
        ItemStack jug=new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(),new ReagentComponent(java.util.Map.of(ModReagents.createKey("water"),10f)));
        return jug;
    }
    private static void assertPuddle(GameTestHelper h, BlockPos pos) {
        require(h.getLevel().getBlockState(pos).is(ModBlocks.PUDDLE.get()),"impact creates puddle on supporting floor");
        var contents=MS14Provider.getDetached(h.getLevel().getBlockEntity(pos),MS14Bridges.REAGENT);
        require(Math.abs(contents.getMap().values().stream().mapToDouble(Float::doubleValue).sum()-10f)<.001,"impact conserves 1000 cents");
    }

    private static LooseItemEntity add(GameTestHelper h, Vec3 pos, ItemStack stack) {
        LooseItemEntity entity = LooseItemEntity.create(h.getLevel(), pos, stack);
        require(h.getLevel().addFreshEntity(entity), "loose entity added");
        return entity;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
