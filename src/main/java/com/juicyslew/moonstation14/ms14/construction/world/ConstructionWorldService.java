package com.juicyslew.moonstation14.ms14.construction.world;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraphProgress;
import com.juicyslew.moonstation14.ms14.construction.tool.*;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.network.BodyUseItemContext;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;

import java.util.UUID;
import java.util.Map;

/** Server authority for selected-plan foundation placement. Call only after authenticated body-hit admission. */
public final class ConstructionWorldService {
    private static final String WORK = "construction_foundation";
    private static final Map<UUID, ReservationSettlement> PENDING_RESERVATIONS = new java.util.concurrent.ConcurrentHashMap<>();
    @FunctionalInterface public interface ActiveRecipeProvider {
        String selectedRecipe(net.minecraft.server.level.ServerPlayer actor, LivingEntity controlledBody);
    }
    private static volatile ActiveRecipeProvider activeRecipeProvider = (actor, body) ->
            com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanMode.selectedRecipe(actor, body).orElse(null);
    public static void setActiveRecipeProvider(ActiveRecipeProvider provider) {
        activeRecipeProvider = java.util.Objects.requireNonNull(provider);
    }
    public static String selectedRecipe(net.minecraft.server.level.ServerPlayer actor, LivingEntity body) {
        return activeRecipeProvider.selectedRecipe(actor, body);
    }
    private ConstructionWorldService() { }
    private record StackSnapshot(String token, ItemStack stack, UUID reservation) {
        private StackSnapshot { stack = stack == null ? null : stack.copy(); }
        @Override public ItemStack stack() { return stack == null ? null : stack.copy(); }
    }
    private record HandsSnapshot(long revision, String active, java.util.List<String> handIds,
                                 java.util.Map<String, StackSnapshot> hands,
                                 java.util.Optional<LiveHands.EquipmentSnapshot> equipment) { }
    private static HandsSnapshot captureHands(BodyUseItemContext c) {
        var map = new java.util.LinkedHashMap<String, StackSnapshot>();
        for (String id : c.hands().handIds()) map.put(id, new StackSnapshot(c.hands().token(id).orElse(null),
                c.hands().stackCopy(id).orElse(null), c.hands().reservation(id).orElse(null)));
        return new HandsSnapshot(c.hands().revision(), c.hands().activeHand(), c.hands().handIds(), Map.copyOf(map),
                LiveHands.equipmentSnapshot(c.authority().body()));
    }

    /** Existing graph hosts own the interaction; unsupported held items are deliberately denied. */
    public static java.util.Optional<com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome> interact(BodyUseItemContext c) {
        if (c == null || !(c.authority().body().level() instanceof ServerLevel level)) return java.util.Optional.empty();
        // A matching selected-plan foundation is an explicit placement intent. Resolve it
        // before an existing graph host can claim the click; a blocked placement must not
        // silently turn into material consumption on that host.
        String selected = selectedRecipe(c.actor(), c.authority().body());
        if (selected != null) {
            var catalog = PrototypeRuntime.serverConstructionCatalog();
            var stage = catalog.initialStage(parse(selected)).orElse(null);
            if (routeInitialMaterial(stage, c.stack()) == InitialMaterialRoute.PLACEMENT)
                return java.util.Optional.of(beginFoundation(c, selected)
                        ? com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.WORK_STARTED
                        : com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED);
        }
        var data = ConstructionWorldData.existing(level).orElse(null);
        var record = data == null ? null : data.get(c.pos()).orElse(null);
        if (record == null) return java.util.Optional.empty();
        return java.util.Optional.of(beginEdge(c, level, record));
    }
    enum InitialMaterialRoute { PLACEMENT, HOST_INTERACTION }
    /** Production dispatch decision: only the resolved initial material of a selected structure claims placement intent. */
    static InitialMaterialRoute routeInitialMaterial(
            com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog.InitialStage stage,
            ItemStack stack) {
        return stage != null && !stage.steps().isEmpty()
                && stage.steps().getFirst() instanceof ConstructionGraph.MaterialStep
                && matchesStep(stage.steps().getFirst(), stack)
                ? InitialMaterialRoute.PLACEMENT : InitialMaterialRoute.HOST_INTERACTION;
    }
    public static boolean hasConstructionHost(ServerLevel level, BlockPos pos) {
        return ConstructionWorldData.existing(level).flatMap(data -> data.get(pos)).isPresent();
    }

    private static com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome beginEdge(
            BodyUseItemContext c, ServerLevel level, ConstructionWorldData.Record record) {
        var catalog = PrototypeRuntime.serverConstructionCatalog();
        var recipe = catalog.recipe(parse(record.recipeId()));
        if (recipe == null) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        var graph = catalog.graph(parse(recipe.graph()));
        if (graph == null || !graph.nodes().containsKey(record.current())) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        Block actual = BuiltInRegistries.BLOCK.get(parse(record.hostBlock()));
        if (actual == null || actual == net.minecraft.world.level.block.Blocks.AIR || level.getBlockState(record.pos()).getBlock() != actual
                || !level.hasChunkAt(record.pos())) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        var currentNode = graph.nodes().get(record.current());
        var handsSnapshot = captureHands(c);
        var currentEntity = currentNode.entity().orElse(null);
        var currentPolicy = currentEntity == null ? null : entityPolicy(currentEntity);
        var heldToolId = toolId(c.stack());
        var heldTool = heldToolId == null ? null : PrototypeRuntime.serverTools().get(heldToolId);
        if (heldTool != null && heldTool.qualities().contains(ToolPrototypeData.Quality.Anchoring)) {
            if (currentPolicy == null || (record.anchored() ? !currentPolicy.unanchorable() : !currentPolicy.anchorable()))
                return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
            return beginAnchorToggle(c, level, record, level.getBlockState(record.pos()), heldTool, currentPolicy, handsSnapshot);
        }
        boolean anchored = record.anchored();
        if (record.edgeIndex() >= currentNode.edges().size()) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        var edge = record.stepIndex() > 0 ? currentNode.edges().get(record.edgeIndex())
                : graph.eligibleEdges(record.current(), anchored).stream().filter(e -> !e.steps().isEmpty() && matchesStep(e.steps().getFirst(), c.stack())).findFirst().orElse(null);
        if (edge == null || record.stepIndex() >= edge.steps().size()) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        int index = currentNode.edges().indexOf(edge);
        var step = edge.steps().get(record.stepIndex());
        var before = c.stack(); var after = before.copy();
        double speed = 1.0;
        if (step instanceof ConstructionGraph.MaterialStep m) {
            if (before.getCount() < m.amount()) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
            after.shrink(m.amount());
        } else if (step instanceof ConstructionGraph.ToolStep t) {
            var id = toolId(before);
            var tool = id == null ? null : PrototypeRuntime.serverTools().get(id);
            if (tool == null || !tool.qualities().contains(t.quality())) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
            speed = tool.speedModifier();
            if (t.quality() == ToolPrototypeData.Quality.Welding) {
                if (!ModTools.hasQuality(PrototypeRuntime.serverTools(), toolId(before), t.quality(), fuel(before))) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
                after = spendFuel(before, tool, t.fuel(), level.getServer().overworld().getGameTime());
                if (after == null) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
            }
        }
        if (BodyHeldItemMutation.preview(c.actor(), c.authority(), c.hands(), c.handId(), c.token(), c.revision(), before, after).isEmpty())
            return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        final ItemStack plannedAfter = after.copy();
        long ticks = Math.max(1L, (long)Math.ceil(step.seconds() * 20.0 / speed));
        if (ticks > Integer.MAX_VALUE) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        var expectedState = level.getBlockState(record.pos());
        boolean breakOnDamage = !(step instanceof ConstructionGraph.MaterialStep);
        var start = TimedInteractionSystem.start(c.authority().body(), c.actor(), 0, "construction_graph",
                record.pos().asLong() + ":" + record.hostId() + ":" + record.current() + ":" + record.edgeIndex() + ":" + record.stepIndex(),
                 (int)ticks, breakOnDamage, true,
                 () -> validEdge(c, level, record, expectedState, before, handsSnapshot, step),
                 () -> completeEdge(c, level, data(level), record, graph, edge, index, anchored, step, before, plannedAfter, expectedState, handsSnapshot),
                 java.util.Optional.of(record.pos()), () -> { });
        return start == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED
                ? com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED
                : com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE;
    }

    private static boolean matchesStep(ConstructionGraph.Step step, ItemStack stack) {
        if (step instanceof ConstructionGraph.MaterialStep m)
            return m.material() == ConstructionGraph.MaterialId.Steel ? stack.is(ModItems.STEEL) : stack.is(ModItems.PLASTIC);
        var t = (ConstructionGraph.ToolStep)step;
        var catalog = PrototypeRuntime.serverTools(); var id = toolId(stack);
        return id != null && ModTools.hasQuality(catalog, id, t.quality(), fuel(stack));
    }
    private static com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome beginAnchorToggle(
            BodyUseItemContext c, ServerLevel level, ConstructionWorldData.Record record, BlockState state,
            ToolPrototypeData tool, com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData policy,
            HandsSnapshot handsSnapshot) {
        if (!tool.qualities().contains(ToolPrototypeData.Quality.Anchoring) || policy == null
                || (record.anchored() ? !policy.unanchorable() : !policy.anchorable()))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        long ticks = Math.max(1L, (long)Math.ceil(policy.anchoringSeconds() * 20.0 / tool.speedModifier()));
        if (ticks > Integer.MAX_VALUE) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        ItemStack before = c.stack();
        if (!validHost(c, level, record, state, before, handsSnapshot)) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED;
        var result = TimedInteractionSystem.start(c.authority().body(), c.actor(), 0, "construction_anchor",
                record.pos().asLong() + ":" + record.hostId() + ":" + record.anchored(), (int)ticks, true, true,
                 () -> validHost(c, level, record, state, before, handsSnapshot), () -> {
                    if (!validHost(c, level, record, state, before, handsSnapshot)) return TimedInteractionSystem.Completion.DENIED;
                    var next = new ConstructionWorldData.Record(record.pos(), record.hostId(), record.hostBlock(), record.recipeId(),
                            record.goal(), record.current(), record.edgeIndex(), record.stepIndex(), !record.anchored());
                    if (!ConstructionWorldData.forMutation(level).replace(level, record, next)) return TimedInteractionSystem.Completion.DENIED;
                    com.juicyslew.moonstation14.ms14.construction.tool.ConstructionToolAudio.playSuccessful(level, c.authority().body(), before);
                     return TimedInteractionSystem.Completion.STOP;
                 }, java.util.Optional.of(record.pos()), () -> { });
        return result == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED
                ? com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED
                : com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE;
    }
    private static net.minecraft.resources.ResourceLocation toolId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).orElse(null);
        return key == null || !com.juicyslew.moonstation14.MoonStation14.MOD_ID.equals(key.location().getNamespace())
                ? null : ModTools.id(key.location().getPath());
    }
    private static WelderFuelData fuel(ItemStack stack) {
        return stack.get(ModDataComponents.WELDER_FUEL.get());
    }
    private static ItemStack spendFuel(ItemStack before, ToolPrototypeData tool, int amount, long now) {
        var state = fuel(before);
        if (state == null || !state.lit()) return null;
        var welder = tool.welder().orElse(null);
        if (welder == null) return null;
        var current = state.tick(welder, now);
        if (!current.lit() || current.fuel() < amount) return null;
        var after = before.copy(); after.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(current.fuel()-amount, true, now));
        return after;
    }
    private static ConstructionWorldData data(ServerLevel level) { return ConstructionWorldData.existing(level).orElse(null); }
    private static boolean validHost(BodyUseItemContext c, ServerLevel level, ConstructionWorldData.Record record,
                                     BlockState state, ItemStack before, HandsSnapshot handsSnapshot) {
        return data(level) != null && data(level).get(record.pos()).filter(record::equals).isPresent()
                && level.getChunkSource().hasChunk(record.pos().getX() >> 4, record.pos().getZ() >> 4)
                && level.getWorldBorder().isWithinBounds(record.pos()) && level.mayInteract(c.actor(), record.pos())
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(record.pos()).getBlock()).toString().equals(record.hostBlock())
                && level.getBlockState(record.pos()) == state && HandActorAuthority.revalidate(c.actor(), c.authority())
                && c.authority().body().isAlive()
                && heldSnapshotValid(c, before, handsSnapshot);
    }
    private static boolean validEdge(BodyUseItemContext c, ServerLevel level, ConstructionWorldData.Record record,
            BlockState state, ItemStack before, HandsSnapshot snapshot, ConstructionGraph.Step step) {
        if (!(step instanceof ConstructionGraph.MaterialStep material))
            return validHost(c,level,record,state,before,snapshot);
        if (data(level) == null || data(level).get(record.pos()).filter(record::equals).isEmpty()
                || !level.hasChunkAt(record.pos()) || level.getBlockState(record.pos()) != state
                || !level.mayInteract(c.actor(),record.pos()) || !HandActorAuthority.revalidate(c.actor(),c.authority())
                || !c.authority().body().isAlive()) return false;
        LiveHands live=c.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (live==null||!live.compatible(c.authority().body())||!live.handIds().equals(snapshot.handIds())
                ||!c.handId().equals(live.activeHand())||!live.token(c.handId()).filter(c.token()::equals).isPresent()) return false;
        ItemStack current=live.stackCopy(c.handId()).orElse(null);
        return materialSourceContinuous(c.token(), live.token(c.handId()).orElse(null), before, current, material.amount());
    }
    static boolean materialSourceContinuous(String expectedToken, String currentToken, ItemStack before,
                                            ItemStack current, int required) {
        return expectedToken != null && expectedToken.equals(currentToken) && before != null && current != null
                && required > 0 && current.getCount() >= required
                && ItemStack.isSameItemSameComponents(before, current);
    }
    private static boolean heldSnapshotValid(BodyUseItemContext c, ItemStack expected, HandsSnapshot snapshot) {
        LiveHands live = c.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (live == null || !live.compatible(c.authority().body()) || !snapshot.handIds().equals(live.handIds())
                || !snapshot.active().equals(live.activeHand()) || !c.handId().equals(live.activeHand())
                || !live.token(c.handId()).filter(c.token()::equals).isPresent()) return false;
        boolean fuelChanged = false;
        for (String hand : snapshot.handIds()) {
            var old = snapshot.hands().get(hand);
            if (!java.util.Objects.equals(old.token(), live.token(hand).orElse(null))
                    || !java.util.Objects.equals(old.reservation(), live.reservation(hand).orElse(null))) return false;
            ItemStack prior = old.stack(), current = live.stackCopy(hand).orElse(null);
            if (!sameStackOrPassiveFuel(prior, current)) return false;
            fuelChanged |= passiveFuelChanged(prior, current);
        }
        var equipment = LiveHands.equipmentSnapshot(c.authority().body());
        if (!sameEquipment(snapshot.equipment(), equipment)) return false;
        fuelChanged |= passiveEquipmentFuelChanged(snapshot.equipment(), equipment);
        if (!revisionContinuity(snapshot.revision(), live.revision(), fuelChanged)) return false;
        return sameStackOrPassiveFuel(expected, live.stackCopy(c.handId()).orElse(null));
    }
    private static boolean sameEquipment(java.util.Optional<LiveHands.EquipmentSnapshot> before,
                                         java.util.Optional<LiveHands.EquipmentSnapshot> after) {
        if (before.isEmpty() || after.isEmpty()) return before.equals(after);
        var a = before.orElseThrow(); var b = after.orElseThrow();
        if (a.persisted() != b.persisted() || a.slots().size() != b.slots().size()) return false;
        for (int i = 0; i < a.slots().size(); i++) {
            var x = a.slots().get(i); var y = b.slots().get(i);
            if (!x.id().equals(y.id()) || !x.token().equals(y.token())
                    || !sameStackOrPassiveFuel(x.stack().orElse(null), y.stack().orElse(null))) return false;
        }
        return true;
    }
    private static boolean passiveEquipmentFuelChanged(java.util.Optional<LiveHands.EquipmentSnapshot> before,
                                                        java.util.Optional<LiveHands.EquipmentSnapshot> after) {
        if (before.isEmpty() || after.isEmpty()) return false;
        for (int i = 0; i < before.orElseThrow().slots().size(); i++)
            if (passiveFuelChanged(before.orElseThrow().slots().get(i).stack().orElse(null),
                    after.orElseThrow().slots().get(i).stack().orElse(null))) return true;
        return false;
    }
    static boolean revisionContinuity(long initial, long current, boolean passiveFuelChanged) {
        return current >= initial && (current == initial || passiveFuelChanged);
    }
    static boolean sameStackOrPassiveFuel(ItemStack before, ItemStack after) {
        if (before == null || after == null) return before == after;
        if (!ItemStack.isSameItem(before, after) || before.getCount() != after.getCount()) return false;
        if (ItemStack.isSameItemSameComponents(before, after)) return true;
        if (!before.is(ModItems.WELDER)) return false;
        var oldFuel = fuel(before); var newFuel = fuel(after);
        if (oldFuel == null || newFuel == null || newFuel.fuel() > oldFuel.fuel()
                || (newFuel.lastBurnTick() < oldFuel.lastBurnTick()
                && !(newFuel.fuel() == 0 && !newFuel.lit()))) return false;
        ItemStack a = before.copy(), b = after.copy();
        a.remove(ModDataComponents.WELDER_FUEL.get()); b.remove(ModDataComponents.WELDER_FUEL.get());
        return ItemStack.isSameItemSameComponents(a, b);
    }
    static boolean passiveFuelChanged(ItemStack before, ItemStack after) {
        return before != null && after != null && before.is(ModItems.WELDER) && !java.util.Objects.equals(fuel(before), fuel(after));
    }
    private static TimedInteractionSystem.Completion completeEdge(BodyUseItemContext c, ServerLevel level, ConstructionWorldData data,
            ConstructionWorldData.Record record, ConstructionGraph graph, ConstructionGraph.Edge edge, int edgeIndex,
            boolean anchored, ConstructionGraph.Step step, ItemStack before, ItemStack after, BlockState oldState,
            HandsSnapshot handsSnapshot) {
        if (data == null || !validEdge(c, level, record, oldState, before, handsSnapshot, step)) return TimedInteractionSystem.Completion.DENIED;
        var result = new ConstructionGraphProgress(record.current(), edgeIndex, record.stepIndex()).completeStep(graph, anchored);
        String nextNode = result.progress().node();
        String outputEntity = graph.nodes().get(nextNode).entity().orElse(null);
        var outputPolicy = outputEntity == null ? null : entityPolicy(outputEntity);
        Block nextBlock = outputPolicy == null ? null : mappedBlock(outputPolicy);
        if (outputEntity != null && (outputPolicy == null || nextBlock == null)) return TimedInteractionSystem.Completion.DENIED;
        BlockState nextState = nextBlock == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : nextBlock.defaultBlockState();
        java.util.List<ItemEntity> returns = stageReturns(level, c.authority().body(), result.returns());
        if (returns == null) return TimedInteractionSystem.Completion.DENIED;
        if (!ConstructionWorldData.setBlockInternal(level, record.pos(), nextState, Block.UPDATE_ALL)) { returns.forEach(ItemEntity::discard); return TimedInteractionSystem.Completion.DENIED; }
        String nextBlockId = nextBlock == null ? record.hostBlock() : BuiltInRegistries.BLOCK.getKey(nextBlock).toString();
        boolean nextAnchored = outputPolicy == null ? record.anchored() : outputPolicy.defaultAnchored();
        var replacement = new ConstructionWorldData.Record(record.pos(), record.hostId(), nextBlockId, record.recipeId(), record.goal(),
                nextNode, nextNode.equals(record.current()) ? edgeIndex : result.progress().edgeIndex(), result.progress().stepIndex(),
                nextNode.equals(record.current()) ? record.anchored() : nextAnchored);
        boolean removed = nextBlock == null && nextNode.equals(graph.start());
        boolean stored = removed ? data.remove(level, record) : data.replace(level, record, replacement);
        if (!stored) {
            returns.forEach(ItemEntity::discard);
            if (!ConstructionWorldData.setBlockInternal(level, record.pos(), oldState, Block.UPDATE_ALL)) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
            return TimedInteractionSystem.Completion.DENIED;
        }
        LiveHands live = c.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack current = live == null ? null : live.stackCopy(c.handId()).orElse(null);
        ItemStack consumeAfter = after;
        if (step instanceof ConstructionGraph.MaterialStep material) {
            current = live == null ? null : live.stackCopy(c.handId()).orElse(null);
            consumeAfter = current == null || current.getCount() < material.amount() ? null : current.copy();
            if (consumeAfter != null) consumeAfter.shrink(material.amount());
        }
        if (step instanceof ConstructionGraph.ToolStep toolStep && toolStep.quality() == ToolPrototypeData.Quality.Welding) {
            var tool = PrototypeRuntime.serverTools().get(ModTools.WELDER);
            consumeAfter = current == null || tool == null ? null : spendFuel(current, tool, toolStep.fuel(), level.getServer().overworld().getGameTime());
        }
        if (live == null || current == null || consumeAfter == null) {
            boolean stateRollback = ConstructionWorldData.setBlockInternal(level, record.pos(), oldState, Block.UPDATE_ALL);
            boolean dataRollback = removed ? data.put(level, record) : data.replace(level, replacement, record);
            returns.forEach(ItemEntity::discard);
            return stateRollback && dataRollback ? TimedInteractionSystem.Completion.DENIED : TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
        }
        var committed = BodyHeldItemMutation.commit(c.actor(), c.authority(), live, c.handId(), c.token(), live.revision(), current, consumeAfter);
        if (committed != BodyHeldItemMutation.Result.SUCCESS) {
            boolean stateRollback = ConstructionWorldData.setBlockInternal(level, record.pos(), oldState, Block.UPDATE_ALL);
            boolean dataRollback = removed ? data.put(level, record) : data.replace(level, replacement, record);
            returns.forEach(ItemEntity::discard);
            if (!stateRollback || !dataRollback || committed == BodyHeldItemMutation.Result.RECOVERY_REQUIRED) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
            return TimedInteractionSystem.Completion.DENIED;
        }
        if (step instanceof ConstructionGraph.ToolStep)
            com.juicyslew.moonstation14.ms14.construction.tool.ConstructionToolAudio.playSuccessful(level, c.authority().body(), consumeAfter);
        AtmosphereService.INSTANCE.topologyChanged(level, record.pos());
        return TimedInteractionSystem.Completion.STOP;
    }
    private static java.util.List<ItemEntity> stageReturns(ServerLevel level, LivingEntity body, java.util.List<ConstructionGraph.MaterialReturn> returns) {
        java.util.List<ItemEntity> spawned = new java.util.ArrayList<>();
        try {
            for (var r : returns) {
                var stack = new ItemStack(r.material() == ConstructionGraph.MaterialId.Steel ? ModItems.STEEL.get() : ModItems.PLASTIC.get(), r.amount());
                var entity = new ItemEntity(level, body.getX(), body.getY()+.25, body.getZ(), stack); entity.setDefaultPickUpDelay();
                if (!level.addFreshEntity(entity) || level.getEntity(entity.getUUID()) != entity) throw new IllegalStateException();
                spawned.add(entity);
            }
            return spawned;
        } catch (RuntimeException failure) {
            spawned.forEach(ItemEntity::discard); return null;
        }
    }

    /** Starts resolved recipe work; the selected recipe ID is supplied by the plan-state owner, never inferred from steel. */
    public static boolean beginFoundation(BodyUseItemContext c, String recipeId) {
        if (c == null || recipeId == null || !(c.authority().body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !c.stack().is(ModItems.STEEL)) return false;
        var catalog = PrototypeRuntime.serverConstructionCatalog();
        var id = parse(recipeId);
         var recipe = catalog.recipe(id);
         if (recipe == null || recipe.outputKind() != ConstructionRecipeData.OutputKind.STRUCTURE) return false;
         var stage = catalog.initialStage(id).orElse(null);
         if (stage == null) return false;
         int cost = 0;
         for (var step : stage.steps()) {
             if (!(step instanceof ConstructionGraph.MaterialStep material) || material.material() != ConstructionGraph.MaterialId.Steel) return false;
             cost = Math.addExact(cost, material.amount());
         }
         if (cost < 1 || c.stack().getCount() < cost || !Double.isFinite(stage.durationSeconds()) || stage.durationSeconds() <= 0) return false;
         String foundationNode = stage.node();
         var outputPolicy = PrototypeRuntime.serverConstructionEntities().get(ModConstruction.entityId(stage.entity()));
         var disposalOutput=PrototypeRuntime.serverDisposals().get(ModConstruction.entityId(stage.entity()));
         if (outputPolicy == null && disposalOutput != null && disposalOutput.tube() != null)
             return com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenuService.beginSharedPlacement(c,id,stage);
        Block target = outputPolicy == null ? null : mappedBlock(outputPolicy);
        if (target == null) return false;
        BlockPos pos = c.pos().relative(c.face()); BlockState prior = level.getBlockState(pos);
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !prior.isAir()
                || !level.mayInteract(c.actor(), pos)
                || com.juicyslew.moonstation14.ms14.atmos.piping.BodyPlacementCollision.blockIntersects(level,
                c.authority().body(), pos, target.defaultBlockState())) return false;
           ItemStack before = c.stack();
         UUID host = UUID.randomUUID();
         return beginReservedPlacement(c,cost,stage.durationSeconds(),pos,pos.asLong()+":"+host,
                 ()->validFoundation(c,level,pos,prior,target),
                 ()->placeFoundation(c,level,pos,prior,target,host,id.toString(),recipe.targetNode(),foundationNode,outputPolicy));
    }

    /** Shared reservation/timer lifecycle for other resolved initial physical forms (currently disposal tubes). */
    public static boolean beginReservedPlacement(BodyUseItemContext c, int cost, double seconds, BlockPos target,
            String sourceKey, java.util.function.BooleanSupplier valid,
            java.util.function.Supplier<TimedInteractionSystem.Completion> complete) {
        if (c == null || cost < 1 || !Double.isFinite(seconds) || seconds <= 0 || target == null || sourceKey == null
                || valid == null || complete == null || !c.stack().is(ModItems.STEEL)
                || !(c.authority().body().level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !HandActorAuthority.revalidate(c.actor(),c.authority()) || c.stack().getCount()<cost) return false;
        long duration=Math.max(1L,(long)Math.ceil(seconds*20)); if(duration>Integer.MAX_VALUE)return false;
        ItemStack before=c.stack(), after=before.copy(); after.shrink(cost);
        if(BodyHeldItemMutation.preview(c.actor(),c.authority(),c.hands(),c.handId(),c.token(),c.revision(),before,after).isEmpty())return false;
        if(BodyHeldItemMutation.commit(c.actor(),c.authority(),c.hands(),c.handId(),c.token(),c.revision(),before,after)
                !=BodyHeldItemMutation.Result.SUCCESS)return false;
        ItemStack reserved=before.copy();reserved.setCount(cost);
        var settlement=new ReservationSettlement(c,reserved);
        String uniqueSourceKey=reservationSourceKey(sourceKey,settlement.id());
        var result=TimedInteractionSystem.start(c.authority().body(),c.actor(),0,WORK,uniqueSourceKey,(int)duration,true,true,
                valid,()->{
                    var outcome=complete.get();
                    if(outcome==TimedInteractionSystem.Completion.STOP)settlement.commit();
                    return outcome;
                },java.util.Optional.of(target),settlement::refund);
        if(result!=com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED){
            try{settlement.refund();}catch(RuntimeException ignored){}
            return false;
        }
        return true;
    }

    private static boolean validFoundation(BodyUseItemContext c, ServerLevel level, BlockPos pos, BlockState prior, Block target) {
        return level.getServer().isSameThread() && level.hasChunkAt(pos) && level.getBlockState(pos) == prior && prior.isAir()
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(c.actor(), pos)
                && !com.juicyslew.moonstation14.ms14.atmos.piping.BodyPlacementCollision.blockIntersects(level,
                c.authority().body(), pos, target.defaultBlockState())
                && c.authority().body().isAlive() && HandActorAuthority.revalidate(c.actor(), c.authority());
    }

    private static final class ReservationSettlement {
        private final UUID id = UUID.randomUUID();
        private final BodyUseItemContext context;
        private final ItemStack reserved;
        private boolean settled;
        private ReservationSettlement(BodyUseItemContext context, ItemStack reserved) {
            this.context=context; this.reserved=reserved.copy(); PENDING_RESERVATIONS.put(id,this);
        }
        private UUID id() { return id; }
        private synchronized void commit() { settled=true; PENDING_RESERVATIONS.remove(id,this); }
        private synchronized void refund() {
            if (settled) return;
            if (refundFoundation(context,reserved)) { settled=true; PENDING_RESERVATIONS.remove(id,this); }
        }
    }

    static String reservationSourceKey(String candidateKey, UUID reservationId) {
        return java.util.Objects.requireNonNull(candidateKey) + ":reservation:" + java.util.Objects.requireNonNull(reservationId);
    }

    private static boolean refundFoundation(BodyUseItemContext c, ItemStack reserved) {
        return LiveHandsCommitGateway.batch(() -> refundFoundationCommitted(c, reserved));
    }

    private static boolean refundFoundationCommitted(BodyUseItemContext c, ItemStack reserved) {
        LivingEntity body = c.authority().body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands != null && hands.compatible(body)) {
            var placed = hands.placeCraftOutput(hands.revision(), reserved.copy(), UUID.randomUUID().toString());
            if (placed.isPresent()) {
                if (!BodyHandStateService.representable(placed.orElseThrow(), body)
                        || !LiveHandsCommitGateway.commitTrusted(body, hands, placed.orElseThrow())
                        || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != placed.orElseThrow()) return false;
                try { BodyHandStateService.requireFreshQuery(c.actor()); } catch (RuntimeException ignored) { }
                return true;
            }
        }
        // Cancellation returns the reserved sheets at the body's location if current inventory is full.
        ItemEntity entity = new ItemEntity(level, body.getX(), body.getY() + .25, body.getZ(), reserved.copy());
         entity.setDefaultPickUpDelay();
         if (!level.addFreshEntity(entity) || level.getEntity(entity.getUUID()) != entity)
             return false;
        return true;
    }

    private static TimedInteractionSystem.Completion placeFoundation(BodyUseItemContext c, ServerLevel l, BlockPos p,
            BlockState prior, Block target, UUID host, String recipe, String goal, String current,
            com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData outputPolicy) {
        if (!validFoundation(c, l, p, prior, target)) return TimedInteractionSystem.Completion.DENIED;
        BlockState placed = target.defaultBlockState();
        if (!l.setBlock(p, placed, Block.UPDATE_ALL)) return TimedInteractionSystem.Completion.DENIED;
        var data = ConstructionWorldData.forMutation(l);
        var record = new ConstructionWorldData.Record(p, host, BuiltInRegistries.BLOCK.getKey(target).toString(), recipe,
                goal, current, 0, 0, outputPolicy.defaultAnchored());
        if (!data.put(l, record)) {
            if (l.getBlockState(p) == placed && !l.setBlock(p, prior, Block.UPDATE_ALL)) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
            return TimedInteractionSystem.Completion.DENIED;
        }
        AtmosphereService.INSTANCE.topologyChanged(l, p);
        return TimedInteractionSystem.Completion.STOP;
    }

    private static com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData entityPolicy(String entity) {
        if (entity == null || entity.isBlank()) return null;
        return PrototypeRuntime.serverConstructionEntities().get(ModConstruction.entityId(entity));
    }
    private static Block mappedBlock(com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData policy) {
        try {
            Block block = BuiltInRegistries.BLOCK.get(parse(policy.block()));
            return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
        } catch (IllegalArgumentException invalidId) { return null; }
    }
    private static net.minecraft.resources.ResourceLocation parse(String id) {
        return id.indexOf(':') < 0 ? ModConstruction.id(id) : net.minecraft.resources.ResourceLocation.parse(id);
    }
}
