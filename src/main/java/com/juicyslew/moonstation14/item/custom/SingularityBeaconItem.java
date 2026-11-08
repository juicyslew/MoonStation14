package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.singularity.SingularityFood;
import com.juicyslew.moonstation14.ms14.singularity.beacon.ModSingularityBeacons;
import com.juicyslew.moonstation14.ms14.singularity.beacon.SingularityBeaconProfile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Portable and placed beacons share the resolved SinguloFood values. */
public final class SingularityBeaconItem extends BlockItem implements SingularityFood {
    public SingularityBeaconItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override public double singularityFoodEnergy() {
        SingularityBeaconProfile profile = ModSingularityBeacons.serverDefaultProfile();
        return profile == null ? Double.NaN : profile.foodEnergy();
    }

    @Override public double singularityFoodFactor() {
        SingularityBeaconProfile profile = ModSingularityBeacons.serverDefaultProfile();
        return profile == null ? Double.NaN : profile.foodFactor();
    }
}
