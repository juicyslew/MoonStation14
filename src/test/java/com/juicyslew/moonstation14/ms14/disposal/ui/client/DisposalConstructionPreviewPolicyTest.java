package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalConstructionPreviewPolicyTest {
    @Test void resolvedMaterialAndCountAreIndependent() {
        var four=new ItemStack(ModItems.STEEL.get(),4);
        assertTrue(DisposalConstructionPreview.materialMatches(four,"moonstation14:steel"));
        assertFalse(DisposalConstructionPreview.enoughMaterial(four,"moonstation14:steel",5));
        assertTrue(DisposalConstructionPreview.enoughMaterial(new ItemStack(ModItems.STEEL.get(),5),"moonstation14:steel",5));
        assertFalse(DisposalConstructionPreview.materialMatches(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),"moonstation14:steel"));
    }

    @Test void sealedTargetUsesClickedCellAndOrdinaryTargetUsesAdjacentCell() {
        var hit=new BlockHitResult(Vec3.ZERO,Direction.EAST,new BlockPos(2,3,4),false);
        assertEquals(new BlockPos(2,3,4),DisposalConstructionPreview.target(hit,true));
        assertEquals(new BlockPos(3,3,4),DisposalConstructionPreview.target(hit,false));
    }

    @Test void interactiveSupportsAreRejectedButOrdinaryAirIsNot() {
        assertTrue(DisposalConstructionPreview.interactive(Blocks.OAK_DOOR.defaultBlockState()));
        assertFalse(DisposalConstructionPreview.interactive(Blocks.AIR.defaultBlockState()));
    }
}
