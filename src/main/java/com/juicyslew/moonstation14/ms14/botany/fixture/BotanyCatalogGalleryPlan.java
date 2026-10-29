package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pure one-room placement plan for a gallery of supported seed catalog entries. */
public final class BotanyCatalogGalleryPlan {
    public static final int WIDTH=23, HEIGHT=5, COLUMNS=10;
    public record Entry(ResourceLocation species, SeedProfile profile, BlockPos trayPosition,
                        BlockPos labelPosition) { }
    private final BlockPos origin;
    private final int depth;
    private final List<Entry> entries;
    private final List<BlockPos> footprint;
    private final List<BlockPos> lighting;

    public static BotanyCatalogGalleryPlan create(BlockPos origin, PrototypeCatalog<SeedProfile> seeds,
            PrototypeCatalog<ProduceProfile> produce, Predicate<ResourceLocation> available) {
        var descriptors=BotanyFixtureSupplies.catalogDescriptors(seeds,produce,available).stream()
                .filter(d->d.kind()==BotanyFixtureSupplies.Kind.SEED).toList();
        var keys=descriptors.stream().map(d->com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId(d.value())).toList();
        if(new java.util.HashSet<>(keys).size()!=keys.size() || keys.stream().anyMatch(id->seeds.get(id)==null))
            throw new IllegalStateException("catalog descriptors contain duplicate or unresolved species");
        return new BotanyCatalogGalleryPlan(origin,keys,seeds);
    }
    private BotanyCatalogGalleryPlan(BlockPos origin,List<ResourceLocation> keys,PrototypeCatalog<SeedProfile> seeds) {
        this.origin=origin;
        int rows=(keys.size()+COLUMNS-1)/COLUMNS;
        depth=Math.max(29,5+3*rows+3);
        var placed=new ArrayList<Entry>();
        for(int i=0;i<keys.size();i++) {
            int row=i/COLUMNS,col=i%COLUMNS;
            BlockPos tray=origin.offset(2+2*col,1,5+3*row);
            placed.add(new Entry(keys.get(i),seeds.get(keys.get(i)),tray,tray.south()));
        }
        entries=List.copyOf(placed);
        var cells=new java.util.LinkedHashSet<>(SmallBotanyFixtureLayout.roomFootprint(origin,WIDTH,depth,HEIGHT));
        for(int x=5;x<=9;x++) for(int y=0;y<=3;y++) for(int z=-4;z<=-1;z++) cells.add(origin.offset(x,y,z));
        footprint=List.copyOf(cells);
        var lamps=new ArrayList<BlockPos>();
        for(int x=2;x<WIDTH-1;x+=4) for(int z=2;z<depth-1;z+=4) lamps.add(origin.offset(x,HEIGHT-1,z));
        for(var entry:entries) lamps.add(entry.labelPosition().below());
        lighting=List.copyOf(lamps);
    }
    public BlockPos origin(){return origin;}
    public int width(){return WIDTH;}
    public int depth(){return depth;}
    public int height(){return HEIGHT;}
    public Vec3 entryPosition(){return SmallBotanyFixtureLayout.entry(origin);}
    public List<Entry> entries(){return entries;}
    public List<BlockPos> plannedFootprint(){return footprint;}
    public List<BlockPos> lightingPositions(){return lighting;}
}
