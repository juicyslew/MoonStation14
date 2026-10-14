package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasCanisters;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Optional, isolated furnishing pass for a successfully built singularity fixture. */
public final class SingularityFixtureOperatorKit {
    private SingularityFixtureOperatorKit() { }

    public record Placed(List<BlockPos> accessBlocks, List<UUID> provisionedEntities) {
        public Placed { accessBlocks = List.copyOf(accessBlocks); provisionedEntities = List.copyOf(provisionedEntities); }
    }
    public record Result(Placed kit, String refusal) {
        public boolean successful() { return kit != null; }
    }

    public static Result install(ServerLevel level, SingularityFixtureLayout.Placed fixture) {
        BlockPos o = fixture.origin();
        int minX=fixture.fieldGenerators().stream().mapToInt(p->p.getX()).min().orElseThrow()-o.getX();
        int maxX=fixture.fieldGenerators().stream().mapToInt(p->p.getX()).max().orElseThrow()-o.getX();
        int maxY=fixture.fieldGenerators().stream().mapToInt(p->p.getY()).max().orElseThrow()-o.getY();
        int minZ=fixture.fieldGenerators().stream().mapToInt(p->p.getZ()).min().orElseThrow()-o.getZ();
        int maxZ=fixture.fieldGenerators().stream().mapToInt(p->p.getZ()).max().orElseThrow()-o.getZ();
        Set<BlockPos> floors = new LinkedHashSet<>(), stairs = new LinkedHashSet<>();
        for (int x=0;x<=26;x++) for(int z=0;z<=18;z++) floors.add(o.offset(x,0,z));
        for(int x=16;x<=22;x++) for(int z=6;z<=12;z++) floors.add(o.offset(x,4,z));
        for(int x=2;x<=4;x++) for(int z=6;z<=12;z++) floors.add(o.offset(x,6,z));
        for(int x=10;x<=15;x++) for(int z=6;z<=12;z++) floors.add(o.offset(x,6,z));
        // Keep both enclosure sizes' X/Z field-face interiors clear through the deck.
        for(int x:new int[]{minX,maxX}) for(int z=minZ;z<=maxZ;z++) floors.remove(o.offset(x,6,z));
        for(int z:new int[]{minZ,maxZ}) for(int x=minX;x<=maxX;x++) floors.remove(o.offset(x,6,z));
        // Outside cage: west and east access stairs, plus the PA approach stair.
        // Stop at the top-deck support height: the next cell is the upper emitter
        // lane for compact geometry (y=maxY, z=minZ=maxY), and stairs have collision.
        // The deck itself reaches the upper generators without this final blocker.
        for(int y=1;y<maxY;y++) stairs.add(o.offset(minX-1,y,y));
        for(int y=1;y<maxY;y++) stairs.add(o.offset(maxX+1,y,y));
        for(int y=1;y<=4;y++) stairs.add(o.offset(23,y,y+3));
        // Upper access walkways approach all four Y=9 corner generators from outside
        // the six-face shell; the east route passes around the clear end of the glass wall.
        for(int z=minZ;z<=maxZ;z++) floors.add(o.offset(minX-1,maxY-1,z));
        for(int z=minZ;z<=maxZ;z++) floors.add(o.offset(maxX+1,maxY-1,z));
        for(int z=maxZ-1;z<=maxZ+1;z++) floors.add(o.offset(15,maxY-1,z));
        for(int x=maxX+1;x<=15;x++) floors.add(o.offset(x,maxY-1,maxZ+1));
        floors.removeAll(stairs); // stairs take the deck overlap only where the original world is clear.

        Set<BlockPos> occupied = new LinkedHashSet<>(fixture.occupiedPositions());
        for(BlockPos p:floors) if(occupied.contains(p)) return new Result(null,"floor would intersect fixture device at "+p);
        for(BlockPos p:stairs) if(occupied.contains(p)) return new Result(null,"stair would intersect fixture device at "+p);
        for(BlockPos p:floors) if(!clearOrExistingStone(level,p)) return new Result(null,"floor placement collision at "+p);
        for(BlockPos p:stairs) if(!level.getBlockState(p).isAir() || level.getBlockEntity(p)!=null) return new Result(null,"stair placement collision at "+p);

        // Block placements are now fully preflighted; keep cable-host blocks untouched (STONE remains STONE).
        for(BlockPos p:floors) if(level.getBlockState(p).isAir()) level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
        var stairState=Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.SOUTH)
                .setValue(StairBlock.HALF,Half.BOTTOM).setValue(StairBlock.SHAPE,StairsShape.STRAIGHT);
        for(BlockPos p:stairs) level.setBlock(p,stairState,3);

        List<UUID> entities=new ArrayList<>();
        var equipment=ModEquipmentItems.catalog(level);
        ModEquipmentItems.require(equipment,ModInventoryTemplates.id("suit_rad"));
        ModEquipmentItems.require(equipment,ModInventoryTemplates.id("hood_rad"));
        spawn(level,o.offset(23,1,1),new net.minecraft.world.item.ItemStack(ModItems.WRENCH.get()),entities);
        spawn(level,o.offset(24,1,1),new net.minecraft.world.item.ItemStack(ModItems.WELDER.get()),entities);
        spawn(level,o.offset(23,1,3),new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.SUIT_RAD.get()),entities);
        spawn(level,o.offset(24,1,3),new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.HOOD_RAD.get()),entities);
        spawn(level,o.offset(25,1,1),GasTankItem.createStack(level,ModGasTanks.PLASMA_FILLED_ID),entities);
        spawn(level,o.offset(24,1,2),GasTankItem.createStack(level,ModGasTanks.PLASMA_FILLED_ID),entities);
        GasCanisterEntity canister=GasCanisterEntity.create(level,new Vec3(o.getX()+23.5,o.getY()+1,o.getZ()+2.5),ModGasCanisters.PLASMA_ID);
        level.addFreshEntity(canister); entities.add(canister.getUUID());
        return new Result(new Placed(new ArrayList<>(floors) {{ addAll(stairs); }},entities),null);
    }

    private static boolean clearOrExistingStone(ServerLevel level,BlockPos p) {
        if (level.getBlockEntity(p)!=null) return false;
        if (level.getBlockState(p).isAir() || level.getBlockState(p).is(Blocks.STONE)) return true;
        for(Direction face:Direction.values()) if(com.juicyslew.moonstation14.ms14.power.cable.CableStorage.eligible(level,p,face)) return true;
        return false;
    }
    private static void spawn(ServerLevel level,BlockPos p,net.minecraft.world.item.ItemStack stack,List<UUID> ids) {
        LooseItemEntity entity=LooseItemEntity.create(level,new Vec3(p.getX()+.5,p.getY()+.08,p.getZ()+.5),stack);
        level.addFreshEntity(entity); ids.add(entity.getUUID());
    }
}
