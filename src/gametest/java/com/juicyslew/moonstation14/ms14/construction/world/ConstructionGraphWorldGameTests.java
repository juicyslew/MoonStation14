package com.juicyslew.moonstation14.ms14.construction.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"shared-construction", "construction", "atmosphere", "disposals", "timed-interaction"})
@PrefixGameTestTemplate(false)
public final class ConstructionGraphWorldGameTests {
    private ConstructionGraphWorldGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 20)
    public static void resolvesSteelBranchAndPreservesIndependentPlasticGoal(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8, 2, 8));
        var catalog = PrototypeRuntime.serverConstructionCatalog();
        var recipeId = ModConstruction.id("plastic_wall"); var recipe = catalog.recipe(recipeId);
        var graph = catalog.graph(ModConstruction.id(recipe.graph()));
        level.setBlock(pos, ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        require(level.getBlockState(pos).isCollisionShapeFullBlock(level, pos), "girder collision remains unchanged and full-cell");
        require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, pos),
                "resolved Girder composition remains gas-passable despite full collision");
        var start = new ConstructionWorldData.Record(pos, UUID.randomUUID(), "moonstation14:steel_wall_girder_block",
                recipeId.toString(), "plastic_wall", "girder", 0, 0);
        var data = ConstructionWorldData.forMutation(level);
        require(data.put(level, start), "girder foundation record installed");
        var steel = graph.eligibleEdges("girder", true).stream().filter(e -> e.to().equals("wall")).findFirst().orElseThrow();
        require(graph.eligibleEdges("girder", false).stream().anyMatch(e -> e.to().equals("start")),
                "unanchored graph state unlocks screwing dismantle");
        var unanchored = new ConstructionWorldData.Record(pos, start.hostId(), start.hostBlock(), start.recipeId(),
                start.goal(), start.current(), start.edgeIndex(), start.stepIndex(), false);
        require(data.replace(level, start, unanchored), "wrench policy state is persisted independently from held tool");
        require(!graph.eligibleEdges("girder", data.get(pos).orElseThrow().anchored()).stream().anyMatch(e -> e.to().equals("wall")),
                "unanchored state blocks the wall edge");
        require(data.replace(level, unanchored, start), "anchored girder can select either wall branch");
        require(((ConstructionGraph.MaterialStep)steel.steps().getFirst()).material() == ConstructionGraph.MaterialId.Steel,
                "resolved graph exposes the steel wall branch");
        ConstructionWorldData.setBlockInternal(level, pos, ModBlocks.PLASTIC_WALL.get().defaultBlockState(), Block.UPDATE_ALL);
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, pos),
                "resolved plastic wall composition seals gas");
        ConstructionWorldData.setBlockInternal(level, pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, pos),
                "resolved steel wall composition seals gas");
        var wall = new ConstructionWorldData.Record(pos, start.hostId(), "moonstation14:steel_wall_block", start.recipeId(),
                start.goal(), "wall", 0, 0);
        require(data.replace(level, start, wall), "progression preserves foundation identity and remembered goal");
        require(level.getBlockState(pos).is(ModBlocks.STEEL_WALL_BLOCK.get()), "world host follows selected graph branch");
        require(data.get(pos).orElseThrow().goal().equals("plastic_wall"), "remembered goal does not lock steel branch");
        var wallState = level.getBlockState(pos);
        level.setBlock(pos, wallState, Block.UPDATE_ALL);
        require(data.get(pos).isEmpty(), "external same-state block write invalidates the old host incarnation");
        helper.succeed();
    }
    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 20)
    public static void failedTimedPlacementRunsReservationRefundOnce(GameTestHelper helper) {
        var body=helper.spawn(EntityType.ZOMBIE,new BlockPos(2,2,2));
        var refunds=new java.util.concurrent.atomic.AtomicInteger();
        var target=helper.absolutePos(new BlockPos(7,2,7));
        for(int i=0;i<2;i++) {
            String key=com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldService.reservationSourceKey(
                    target.asLong()+"",new UUID(5,i+1));
            var start=com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.start(body,null,0,
                    "construction_foundation_test",key,1,true,true,()->true,
                    ()->com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.Completion.DENIED,
                    java.util.Optional.of(target),refunds::incrementAndGet);
            require(start==com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                    "same candidate with a distinct reservation key admits an independent timed job");
        }
        helper.runAfterDelay(3,()->{
            require(refunds.get()==2,"each denied same-candidate timed reservation is refunded once");
            helper.succeed();
        });
    }
    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 20)
    public static void everySupportedDisposalRecipeResolvesTwoSteelOneSecondInitialStage(GameTestHelper helper) {
        var catalog=PrototypeRuntime.serverConstructionCatalog();
        for(String recipeName:java.util.List.of("disposal_straight","disposal_bend","disposal_trunk","disposal_junction",
                "disposal_junction_flipped","disposal_y_junction","disposal_x_junction","disposal_router","disposal_router_flipped")) {
            var stage=catalog.initialStage(ModConstruction.id(recipeName)).orElseThrow();
            require(stage.durationSeconds()==1.0,"canonical graph duration is one second: "+recipeName);
            require(stage.steps().size()==1&&stage.steps().getFirst() instanceof ConstructionGraph.MaterialStep material
                    &&material.material()==ConstructionGraph.MaterialId.Steel&&material.amount()==2,"canonical graph reserves two steel: "+recipeName);
            require(PrototypeRuntime.serverDisposals().get(ModConstruction.entityId(stage.entity()))!=null,
                    "typed disposal adapter resolves first physical entity: "+recipeName);
        }
        helper.succeed();
    }
    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 20)
    public static void actualWorldGuidanceKeepsGirderUnfinishedAndSuppressesCompletedWall(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(8,2,8));var catalog=PrototypeRuntime.serverConstructionCatalog();
        var data=ConstructionWorldData.forMutation(level);var host=UUID.randomUUID();
        var girder=new ConstructionWorldData.Record(pos,host,"moonstation14:steel_wall_girder_block","moonstation14:steel_wall","wall","girder",0,0);
        level.setBlock(pos,ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState(),Block.UPDATE_ALL);
        require(data.put(level,girder),"actual girder foundation record saved");
        var text=com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceText.content(catalog,data.get(pos).orElseThrow());
        require(text!=null&&text.goal().equals("Steel Wall")&&text.requirement().equals("2 Steel"),
                "server content producer guides from the unfinished girder foundation");
        var steel=new ConstructionWorldData.Record(pos,host,"moonstation14:steel_wall_block",girder.recipeId(),girder.goal(),"wall",0,0);
        require(data.replace(level,girder,steel),"steel wall completion persisted");
        ConstructionWorldData.setBlockInternal(level,pos,ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),Block.UPDATE_ALL);
        require(com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceText.content(catalog,data.get(pos).orElseThrow())==null,
                "completed steel wall has no unfinished guidance");
        var plastic=new ConstructionWorldData.Record(pos,host,"moonstation14:plastic_wall",girder.recipeId(),girder.goal(),"plastic_wall",0,0);
        require(data.replace(level,steel,plastic),"alternate plastic wall completion persisted");
        ConstructionWorldData.setBlockInternal(level,pos,ModBlocks.PLASTIC_WALL.get().defaultBlockState(),Block.UPDATE_ALL);
        require(com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceText.content(catalog,data.get(pos).orElseThrow())==null,
                "completed alternate plastic branch is also finished");
        var ready=new ConstructionWorldData.Record(pos,host,girder.hostBlock(),"moonstation14:girder","girder","girder",0,0);
        require(data.replace(level,plastic,ready),"selected foundation goal persisted");
        ConstructionWorldData.setBlockInternal(level,pos,ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState(),Block.UPDATE_ALL);
        text=com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceText.content(catalog,data.get(pos).orElseThrow());
        require(text!=null&&text.goal().equals("Girder")&&!text.requirement().isBlank()&&!text.requirement().contains("start"),
                "bare girder guidance returned goal="+(text==null?"<null>":text.goal())+", requirement="+(text==null?"<null>":text.requirement())+
                        "; record current="+ready.current()+", goal="+ready.goal()+", edge="+ready.edgeIndex()+", step="+ready.stepIndex()+", anchored="+ready.anchored());
        helper.succeed();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
