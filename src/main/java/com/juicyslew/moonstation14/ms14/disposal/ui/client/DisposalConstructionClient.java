package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionState;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Interim, non-screen disposal construction controls. Intents request authority; they never place locally. */
public final class DisposalConstructionClient {
    public enum Action { ENTER, SELECT, ROTATE, EXIT }
    public record Intent(Action action, String prototype) { }
    public record ClientState(UUID bodyUUID, long epoch, boolean active, String prototype, int rotationOffset, long revision) { }
    public enum Readiness { UPDATING, WRONG_MATERIAL, INSUFFICIENT, READY }
    public record ReadinessView(Readiness state, int held, int required) { }
    public record KeyStep(boolean wasDown, boolean rising) { }
    public record RenderPolicy(boolean paletteOnly, boolean showPlacementStatus) { }
    /** Tracks the client context that owns gesture and snapshot state. */
    public static final class ContextBinding {
        private Object connection, level;
        private UUID body;
        private long epoch;
        private boolean initialized;

        public boolean update(Object nextConnection, Object nextLevel, UUID nextBody, long nextEpoch) {
            boolean changed = !initialized || connection != nextConnection || level != nextLevel
                    || !Objects.equals(body, nextBody) || epoch != nextEpoch;
            connection = nextConnection;
            level = nextLevel;
            body = nextBody;
            epoch = nextEpoch;
            initialized = true;
            return changed;
        }
    }
    public static boolean bindTickContext(ContextBinding binding, Object connection, Object level,
            UUID body, long epoch, Runnable reset) {
        if (!binding.update(connection, level, body, epoch)) return false;
        reset.run();
        return true;
    }
    public static KeyStep keyStep(boolean wasDown, boolean isDown) { return new KeyStep(isDown, isDown && !wasDown); }
    public static RenderPolicy renderPolicy(boolean paletteOpen) { return new RenderPolicy(paletteOpen, !paletteOpen); }
    public static boolean constructionKeysAdmitted(boolean hasContext, boolean allowedScreen, boolean searchFocused) {
        return hasContext && allowedScreen && !searchFocused;
    }
    public static boolean paletteVisible(boolean held, boolean suppressed, boolean active, boolean pending) {
        return held && !suppressed && (active || pending);
    }

    private static final List<String> SHAPES = List.of("disposal_pipe", "disposal_bend", "disposal_trunk", "disposal_junction",
            "disposal_junction_flipped", "disposal_y_junction", "disposal_x_junction", "disposal_tagger", "disposal_router", "disposal_router_flipped");
    private static final KeyMapping CHANGE = key("key.moonstation14.disposal_construction_change", GLFW.GLFW_KEY_G);
    private static final KeyMapping EXIT = key("key.moonstation14.disposal_construction_exit", GLFW.GLFW_KEY_H);
    private static final KeyMapping ROTATE = key("key.moonstation14.disposal_construction_rotate", GLFW.GLFW_KEY_R);
    private static Consumer<Intent> sender = ignored -> { };
    private static Consumer<ResourceLocation> recipeSelection = ignored -> { };
    private static ClientState state;
    private static UUID boundBody;
    private static long boundEpoch;
    private static final ContextBinding contextBinding = new ContextBinding();
    private static boolean palette;
    private static boolean changeWasDown;
    private static final com.juicyslew.moonstation14.ms14.construction.client.ConstructionKeyGesture changeGesture = new com.juicyslew.moonstation14.ms14.construction.client.ConstructionKeyGesture();
    private static boolean enterPending;
    private static boolean suppressHeldPalette;
    private static int legacyCursor;
    private static boolean paletteInitialized;
    public static final class PaletteIntentOwner {
        private ResourceLocation desired;
        private long sequence;
        public void request(ResourceLocation recipe,long requestSequence) { desired=recipe; sequence=requestSequence; }
        public ResourceLocation display(ResourceLocation authoritative) { return desired==null?authoritative:desired; }
        public boolean acknowledge(long ackSequence) {
            if(desired==null||ackSequence<sequence)return false;
            desired=null; sequence=0; return true;
        }
        public void reset() { desired=null; sequence=0; }
        public boolean pending() { return desired!=null; }
        public long sequence() { return sequence; }
        public ResourceLocation desired() { return desired; }
        public ResourceLocation nextRecipe(ResourceLocation authoritative, List<ResourceLocation> options, int delta) {
            ResourceLocation current=display(authoritative);
            int index=options.indexOf(current);
            return options.isEmpty()?null:options.get(selectionIndex(Math.max(0,index),delta,options.size()));
        }
        public int displayIndex(ResourceLocation authoritative, List<ResourceLocation> options) {
            int index=options.indexOf(display(authoritative));
            return index<0?0:index;
        }
    }
    private static final PaletteIntentOwner paletteIntent = new PaletteIntentOwner();
    private static List<ResourceLocation> paletteRecipes() {
        var catalog=PrototypeRuntime.clientConstructionCatalog();
        ResourceLocation selectedId=paletteIntent.display(sharedRecipe);
        if(selectedId==null) return List.of();
        var selected=catalog.recipe(selectedId);
        if(selected==null||selected.family()==null||selected.family().isBlank()) return List.of();
        String family=selected.family();
        return familyOptions(selectedId,new ArrayList<>(catalog.recipeIds()),id->{var recipe=catalog.recipe(id);return recipe==null?null:recipe.family();},family);
    }
    public static List<ResourceLocation> familyOptions(ResourceLocation selected,List<ResourceLocation> orderedIds,
            java.util.function.Function<ResourceLocation,String> familyOf,String selectedFamily) {
        if(selected==null||selectedFamily==null||selectedFamily.isBlank())return List.of();
        return orderedIds.stream().filter(id->selectedFamily.equals(familyOf.apply(id))).toList();
    }
    public static int confirmedCursor(ResourceLocation confirmed,List<ResourceLocation> options,int previous) {
        if(confirmed==null)return 0;
        int index=options.indexOf(confirmed); return index<0?previous:index;
    }
    private static long sequence;
    private static long lastAcceptedDisposalSequence;
    private static long lastAcceptedSharedSequence;
    private static long lastSentDisposalSequence;
    private static long lastSentSharedSequence;
    private static ResourceLocation sharedRecipe;
    private static long sharedRevision = -1;
    private static int sharedRotation;
    private static DisposalConstructionPreview.PlacementStatus placement = new DisposalConstructionPreview.PlacementStatus(false, false, "no_target");
    private static long clientTicks;
    private static final DisposalPlacementIssue placementIssue = new DisposalPlacementIssue();
    private static java.util.function.BiFunction<net.minecraft.core.BlockPos, ResourceLocation,
            java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord>> tubeRecords = (p,d) -> java.util.Optional.empty();

    private DisposalConstructionClient() { }
    private static KeyMapping key(String name, int key) { return new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.NONE,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, key, "key.categories.moonstation14"); }
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(CHANGE); event.register(EXIT); event.register(ROTATE); }
    public static void register() {
        DisposalConstructionState.setClientReceiver(DisposalConstructionClient::receive);
        sender = DisposalConstructionClient::send;
        NeoForge.EVENT_BUS.addListener(DisposalConstructionClient::tick);
        NeoForge.EVENT_BUS.addListener(DisposalConstructionClient::render);
        NeoForge.EVENT_BUS.addListener(DisposalConstructionClient::scroll);
        NeoForge.EVENT_BUS.addListener(DisposalConstructionClient::keyInput);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> clear());
    }
    private static void keyInput(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            releaseBrowserClosePress(event.getKey(), event.getScanCode());
            return;
        }
        var mc=Minecraft.getInstance();
        if (event.getAction()!=GLFW.GLFW_PRESS || changeGesture.pressConsumed() || mc.screen!=null &&
                !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen) ||
                typingFocused()) return;
        if (!com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.matches(CHANGE,event.getKey(),event.getScanCode(),event.getModifiers())) return;
        com.juicyslew.moonstation14.ms14.construction.client.ConstructionBrowserWindow.handleConfiguredClose(
                event.getKey(),event.getScanCode(),event.getModifiers());
    }
    /** Physical release owner; called before screen routing and again by the raw-input fallback. */
    public static boolean releaseBrowserClosePress(int key,int scan) {
        return changeGesture.consumeRelease(key,scan);
    }
    public static boolean handleBrowserClose(int key,int scan,int modifiers,boolean visible,boolean searchFocused,Runnable close) {
        boolean focused=searchFocused || typingFocused();
        return changeGesture.closeBrowserOnPress(visible,focused,
                BodyHandClient.matches(CHANGE,key,scan,modifiers),key,scan,close);
    }
    private static boolean typingFocused() {
        var screen=Minecraft.getInstance().screen;
        return com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.textFocused() ||
                screen!=null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox edit && edit.isFocused();
    }
    public static boolean configuredChangeMatches(int key,int scan,int modifiers) {
        return BodyHandClient.matches(CHANGE,key,scan,modifiers);
    }
    private static void send(Intent intent) {
        MobContext context = current(); if (context == null) return;
        if (!context.id.equals(boundBody) || context.epoch != boundEpoch) { resetSequences(); boundBody = context.id; boundEpoch = context.epoch; }
        if (sequence == Long.MAX_VALUE) return;
        long next = ++sequence;
        lastSentDisposalSequence=next;
        DisposalConstructionRequest.Action action = DisposalConstructionRequest.Action.valueOf(intent.action().name());
        String prototype = intent.action() == Action.SELECT ? canonical(intent.prototype()) : "";
        PacketDistributor.sendToServer(new DisposalConstructionRequest(context.id, context.epoch, next, action, prototype));
    }
    private static String canonical(String shortId) { return shortId != null && shortId.contains(":") ? shortId : ModDisposals.id(shortId == null ? "disposal_pipe" : shortId).toString(); }
    private static void receive(DisposalConstructionState incoming) {
        acceptClientState(new ClientState(incoming.bodyUUID(), incoming.epoch(), incoming.active(),
                incoming.active() ? incoming.prototype() : null, incoming.rotationOffset(), incoming.revision()), incoming.sequence());
    }
    private static void acceptClientState(ClientState incoming, long packetSequence) {
        MobContext current = current();
        if (incoming == null || current == null || !acceptsSnapshot(incoming.bodyUUID(), incoming.epoch(), packetSequence,
                incoming.revision(), current.id, current.epoch, lastSentDisposalSequence, lastAcceptedDisposalSequence,
                state == null ? -1 : state.revision())) return;
        lastAcceptedDisposalSequence = packetSequence;
        if(state!=null&& !Objects.equals(state.prototype(),incoming.prototype())) placementIssue.clear();
        state = incoming; boundBody = incoming.bodyUUID(); boundEpoch = incoming.epoch();
        enterPending = false;
        int found = SHAPES.indexOf(shortName(incoming.prototype())); if (found >= 0 && !paletteIntent.pending()) legacyCursor = found;
    }
    public static boolean acceptsSnapshot(UUID body, long epoch, long packetSequence, long revision,
            UUID currentBody, long currentEpoch, long sentSequence, long acceptedSequence, long currentRevision) {
        return body != null && body.equals(currentBody) && epoch > 0 && epoch == currentEpoch
                && packetSequence >= sentSequence && packetSequence >= acceptedSequence && revision >= currentRevision;
    }
    public static void setIntentSender(Consumer<Intent> consumer) { sender = Objects.requireNonNull(consumer); }
    public static void acceptClientState(ClientState incoming) {
        if (incoming == null || incoming.bodyUUID() == null || incoming.epoch() <= 0) return;
        MobContext current = current();
        if (current == null || !current.id.equals(incoming.bodyUUID()) || current.epoch != incoming.epoch()) return;
        acceptClientState(incoming, sequence);
    }
    public static ClientState state() { return state; }
    public static String displayName(String id) { return name(id).getString(); }
    public static void selectFromBrowser(String id) {
        if (state == null || !state.active()) sender.accept(new Intent(Action.ENTER, null));
        sender.accept(new Intent(Action.SELECT, id));
    }
    /** Integration seam for the shared plan authority; never maps non-disposal recipes to disposal prototypes. */
    public static void setRecipeSelectionHandler(Consumer<ResourceLocation> handler) { recipeSelection = Objects.requireNonNull(handler); }
    public static void selectRecipeFromBrowser(ResourceLocation recipe) {
        if (recipe == null) return;
        var data = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientConstructionCatalog().recipe(recipe);
        if (data == null) return;
        if (data.family().equalsIgnoreCase("disposal_pipes")) {
            sendSharedPlan(recipe);
        } else { recipeSelection.accept(recipe); sendSharedPlan(recipe); }
    }
    private static void sendSharedPlan(ResourceLocation recipe) {
        MobContext context=current(); if(context==null)return;
        if(!context.id.equals(boundBody)||context.epoch!=boundEpoch){resetSequences();boundBody=context.id;boundEpoch=context.epoch;}
        if(sequence==Long.MAX_VALUE)return;
        long sent=++sequence;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest(
                context.id,context.epoch,sent,com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.SELECT,recipe.toString()));
        lastSentSharedSequence=sequence;
        paletteIntent.request(recipe,sent);
    }
    private static void sendSharedClear() {
        sendSharedAction(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.CLEAR,"");
    }
    private static void sendSharedAction(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action action,String recipe) {
        MobContext context=current();if(context==null||sequence==Long.MAX_VALUE)return;
        if(!context.id.equals(boundBody)||context.epoch!=boundEpoch){resetSequences();boundBody=context.id;boundEpoch=context.epoch;}
        long sent=++sequence;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest(context.id,context.epoch,sent,action,recipe));
        lastSentSharedSequence=sequence;
        if(action==com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.FAMILY_VARIANT) {
            paletteIntent.request(ResourceLocation.tryParse(recipe),sent);
        }
    }
    public static void receiveSharedPlan(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanState incoming) {
        MobContext c=current(); if(c==null||incoming==null||!acceptsSnapshot(incoming.body(),incoming.epoch(),incoming.sequence(),incoming.revision(),c.id,c.epoch,lastSentSharedSequence,lastAcceptedSharedSequence,sharedRevision))return;
        lastAcceptedSharedSequence=incoming.sequence(); sharedRevision=incoming.revision(); sharedRotation=incoming.rotation();
        sharedRecipe=incoming.recipe()==null?null:ResourceLocation.tryParse(incoming.recipe());
        paletteIntent.acknowledge(incoming.sequence());
    }
    public static ResourceLocation sharedRecipe() { return sharedRecipe; }
    /** Confirmed shared-plan identity only; excludes optimistic palette intent. */
    public static com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanState confirmedSharedPlan() {
        if (sharedRecipe == null || sharedRevision < 0 || boundBody == null || boundEpoch <= 0) return null;
        return new com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanState(
                boundBody, boundEpoch, lastAcceptedSharedSequence, sharedRevision, sharedRecipe.toString(), sharedRotation);
    }
    public static boolean paletteOpen() { return palette; }
    public static List<String> supportedShapes() { return SHAPES; }
    public static int wrapShapeIndex(int index) { return Math.floorMod(index, SHAPES.size()); }
    public static int selectionIndex(int current, int delta, int size) { return size<=0?0:Math.floorMod(current+delta,size); }
    public static ReadinessView readiness(String materialId, int required, ItemStack held) {
        if (held == null || held.isEmpty() || !held.is(material(materialId))) return new ReadinessView(Readiness.WRONG_MATERIAL, held == null ? 0 : held.getCount(), required);
        return new ReadinessView(held.getCount() < required ? Readiness.INSUFFICIENT : Readiness.READY, held.getCount(), required);
    }
    public static ReadinessView readiness(boolean confirmed, String materialId, int required, ItemStack held) {
        return confirmed ? readiness(materialId, required, held) : new ReadinessView(Readiness.UPDATING, 0, required);
    }
    private static net.minecraft.world.item.Item material(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key) : net.minecraft.world.item.Items.AIR;
    }
    public static void installPreviewProviders() {
        DisposalConstructionPreview.setPlanProvider(() -> Minecraft.getInstance().screen == null && state != null && state.active()
                ? new DisposalConstructionPreview.PreviewPlan(state.bodyUUID(), state.epoch(), state.prototype(), state.rotationOffset()) : null);
        DisposalConstructionPreview.setHeldStackProvider(BodyHandClient::activeHeldStackFor);
        DisposalConstructionPreview.setReadiness(value -> {
            placement = value;
            if (value.aimed() && !value.valid()) placementIssue.invalid(value.reason(),clientTicks,state==null?null:state.bodyUUID(),state==null?0:state.epoch());
        });
    }
    public static void setTubeRecordProvider(java.util.function.BiFunction<net.minecraft.core.BlockPos, ResourceLocation,
            java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord>> provider) {
        tubeRecords = Objects.requireNonNull(provider);
        DisposalConstructionPreview.setTubeRecordProvider(provider);
    }
    public static java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord> tubeRecord(
            net.minecraft.core.BlockPos pos, ResourceLocation dimension) { return tubeRecords.apply(pos, dimension); }
    private static void tick(ClientTickEvent.Post event) {
        clientTicks++;
        Minecraft mc = Minecraft.getInstance(); MobContext context = current();
        bindTickContext(contextBinding, mc.getConnection(), mc.level, context == null ? null : context.id,
                context == null ? 0 : context.epoch, DisposalConstructionClient::clear);
        // clear() deliberately clears boundBody/boundEpoch; bind the current identity immediately so
        // ordinary ticks don't look like a new body transition and erase an in-progress gesture.
        if (context != null) { boundBody = context.id; boundEpoch = context.epoch; }
        boolean searchFocused=com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.textFocused();
        if (searchFocused) {
            changeGesture.reset(); changeWasDown=false; palette=false; enterPending=false; consumeKeys(); return;
        }
        boolean admittedScreen = mc.screen == null || mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen;
        if (!constructionKeysAdmitted(context != null,admittedScreen,searchFocused)) { palette = false; changeWasDown = false; enterPending = false; consumeKeys(); return; }
        boolean changeDown = CHANGE.isDown();
        KeyStep keyStep = keyStep(changeWasDown, changeDown);
        changeWasDown = keyStep.wasDown();
        while (CHANGE.consumeClick()) { }
        boolean exited = false; while (EXIT.consumeClick()) exited = true;
        boolean rotated = false; while (ROTATE.consumeClick()) rotated = true;
        if (!changeDown) suppressHeldPalette = false;
        var gesture = changeGesture.update(changeDown && !suppressHeldPalette && !changeGesture.pressConsumed(), (state != null && state.active()) || !paletteRecipes().isEmpty());
        palette = gesture.showPalette();
        if (gesture.openBrowser()) com.juicyslew.moonstation14.ms14.construction.client.ConstructionBrowserWindow.toggle(state == null ? null : state.prototype());
        if (changeDown && changeGestureOpenPalette() && !paletteInitialized) { var options=paletteRecipes(); if(options.isEmpty()&&state!=null&&state.prototype()!=null)legacyCursor=Math.max(0,SHAPES.indexOf(shortName(state.prototype()))); paletteInitialized=true; }
        if(!changeDown) paletteInitialized=false;
        if (exited && (state != null && state.active() || sharedRecipe != null)) { palette = false; suppressHeldPalette = changeDown; enterPending = false; state = null; placementIssue.clear(); if(sharedRecipe==null)sender.accept(new Intent(Action.EXIT, null)); else sendSharedClear(); }
        if (rotated && sharedRecipe != null && placement.aimed()) sendSharedAction(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.ROTATE,"");
        else if (rotated && state != null && state.active() && placement.aimed()) sender.accept(new Intent(Action.ROTATE, null));
    }
    private static void consumeKeys() { while (CHANGE.consumeClick()) {} while (EXIT.consumeClick()) {} while (ROTATE.consumeClick()) {} }
    private static void scroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (!palette || Minecraft.getInstance().screen != null || event.getScrollDeltaY() == 0) return;
        var options=paletteRecipes();
        if(!options.isEmpty()) { var next=paletteIntent.nextRecipe(sharedRecipe,options,event.getScrollDeltaY()>0?-1:1); sendSharedAction(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.FAMILY_VARIANT,next.toString()); event.setCanceled(true); return; }
        if(state==null||!state.active())return;
        legacyCursor = wrapShapeIndex(legacyCursor + (event.getScrollDeltaY() > 0 ? -1 : 1));
        placementIssue.clear();
        String shape=SHAPES.get(legacyCursor);
        ResourceLocation recipe=PrototypeRuntime.clientConstructionCatalog().recipeIds().stream().filter(id->{var r=PrototypeRuntime.clientConstructionCatalog().recipe(id);return r!=null&&r.family().equalsIgnoreCase("disposal_pipes")&&r.icon().equals(shape);}).findFirst().orElse(null);
        if(recipe!=null)sendSharedAction(com.juicyslew.moonstation14.ms14.construction.plan.ConstructionPlanRequest.Action.FAMILY_VARIANT,recipe.toString());
        event.setCanceled(true);
    }
    private static boolean changeGestureOpenPalette() { return palette; }
    private static void render(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().options.hideGui) return;
        Minecraft mc = Minecraft.getInstance();
        if (sharedRecipe != null || paletteIntent.pending()) {
            if(palette) {
                var options=paletteRecipes(); List<Component> labels=options.stream().map(id->(Component)Component.literal(PrototypeRuntime.clientConstructionCatalog().recipe(id).name())).toList();
                com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.drawPalette(event.getGuiGraphics(),mc,
                        Component.translatable("moonstation14.construction.hud.palette"),labels,paletteIntent.displayIndex(sharedRecipe,options),
                        Component.empty());
                return;
            }
            var displayRecipe=paletteIntent.display(sharedRecipe);
            var recipe=PrototypeRuntime.clientConstructionCatalog().recipe(displayRecipe);
            if(recipe!=null) {
                var body=GhostControlClient.ownedCharacterForHud();
                var held=body==null?ItemStack.EMPTY:BodyHandClient.activeHeldStackFor(body.getUUID(),GhostControlClient.committedCharacterEpoch()).orElse(ItemStack.EMPTY);
                String controls=CHANGE.getTranslatedKeyMessage().getString()+" Change · "+EXIT.getTranslatedKeyMessage().getString()+" Exit · "+ROTATE.getTranslatedKeyMessage().getString()+" Rotate";
                var requiredByMaterial=new java.util.EnumMap<com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialId,Integer>(com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialId.class);
                var initial=PrototypeRuntime.clientConstructionCatalog().initialStage(displayRecipe).orElse(null);
                if(initial!=null) for(var requirement:initial.steps()) {
                    if(requirement instanceof com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialStep m) {
                        requiredByMaterial.merge(m.material(),m.amount(),Math::addExact);
                    }
                }
                String materialName=""; int required=0; String blocked="";
                for(var entry:requiredByMaterial.entrySet()) {
                    var item=entry.getKey()==com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialId.Steel
                            ?com.juicyslew.moonstation14.item.ModItems.STEEL.get():com.juicyslew.moonstation14.item.ModItems.PLASTIC.get();
                    if(!materialName.isEmpty())materialName+=" + ";
                    materialName+=new ItemStack(item).getHoverName().getString()+(requiredByMaterial.size()>1?" "+entry.getValue():"");
                }
                if(requiredByMaterial.size()==1) {
                    var entry=requiredByMaterial.entrySet().iterator().next(); required=entry.getValue();
                    var item=entry.getKey()==com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialId.Steel
                            ?com.juicyslew.moonstation14.item.ModItems.STEEL.get():com.juicyslew.moonstation14.item.ModItems.PLASTIC.get();
                    if(held.isEmpty()||!held.is(item))held=ItemStack.EMPTY;
                } else if(requiredByMaterial.size()>1) {
                    blocked="moonstation14.construction.hud.multiple_materials"; held=ItemStack.EMPTY;
                }
                var model=com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.plan(recipe.name(),materialName,held.getCount(),required,blocked,controls);
                com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.drawPlan(event.getGuiGraphics(),mc,model);
            }
            return;
        }
        if (state == null || !state.active()) {
            return;
        }
        var g = event.getGuiGraphics(); int x = 8;
        RenderPolicy policy = renderPolicy(palette);
        if (policy.paletteOnly()) {
            var options=paletteRecipes(); int count=options.isEmpty()?SHAPES.size():options.size();
            var labels=new ArrayList<Component>();
            for(int i=0;i<count;i++)labels.add(options.isEmpty()?name(SHAPES.get(i)):Component.literal(PrototypeRuntime.clientConstructionCatalog().recipe(options.get(i)).name()));
            var title=Component.translatable("moonstation14.construction.hud.palette");
            var controls=Component.empty();
            com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.drawPalette(g,mc,title,labels,options.isEmpty()?legacyCursor:paletteIntent.displayIndex(sharedRecipe,options),controls);
            return;
        }
        String proto = state.prototype(); var data = proto == null ? null : PrototypeRuntime.clientDisposals().get(ResourceLocation.tryParse(proto));
        var step = data == null ? null : data.construction();
        ItemStack held = BodyHandClient.activeHeldStackFor(state.bodyUUID(), state.epoch()).orElse(ItemStack.EMPTY);
        boolean confirmedHands = BodyHandClient.hasConfirmedHandsFor(state.bodyUUID(), state.epoch());
        ReadinessView ready = readiness(confirmedHands, step == null ? "" : step.material(), step == null ? 1 : step.steelCount(), held);
        Component materialName = new ItemStack(material(step == null ? null : step.material())).getHoverName();
        String issue=policy.showPlacementStatus()?placementIssue.visible(clientTicks,state.bodyUUID(),state.epoch()):null;
        String status=issue==null?"":"moonstation14.construction.hud.blocked";
        var model=com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.plan(name(proto).getString(),materialName.getString(),ready.held(),ready.required(),status,
                CHANGE.getTranslatedKeyMessage().getString()+" Change · "+EXIT.getTranslatedKeyMessage().getString()+" Exit · "+ROTATE.getTranslatedKeyMessage().getString()+" Rotate");
        com.juicyslew.moonstation14.ms14.construction.client.ConstructionHudRenderer.drawPlan(g,mc,model);
    }
    private static void drawPlacementIssue(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,int x,int y) {
        String issue=placementIssue.visible(clientTicks,state==null?null:state.bodyUUID(),state==null?0:state.epoch());
        if(issue!=null)g.drawString(mc.font,Component.translatable("moonstation14.disposal.construction.blocked",Component.translatable(issue)),x,y,0xffff7777);
    }
    private static String shortName(String id) { return id == null ? null : id.substring(id.indexOf(':') + 1); }
    private static Component name(String id) { return Component.translatable("moonstation14.disposal.prototype." + (id == null ? "unknown" : shortName(id))); }
    private static void resetSequences(){sequence=0;lastAcceptedDisposalSequence=0;lastAcceptedSharedSequence=0;lastSentDisposalSequence=0;lastSentSharedSequence=0;paletteIntent.reset();}
    private static void clear() { state = null; sharedRecipe=null; sharedRevision=-1; sharedRotation=0; boundBody = null; boundEpoch = 0; resetSequences(); paletteIntent.reset(); palette = false; changeWasDown = false; changeGesture.resetContext(); enterPending = false; suppressHeldPalette = false; legacyCursor = 0; paletteInitialized=false; placement = new DisposalConstructionPreview.PlacementStatus(false, false, "no_target"); placementIssue.clear(); }
    private record MobContext(UUID id, long epoch) { }
    private static MobContext current() {
        Minecraft mc = Minecraft.getInstance(); var body = GhostControlClient.ownedCharacterForHud(); long epoch = GhostControlClient.committedCharacterEpoch();
        return mc.player != null && mc.player.isSpectator() && mc.getConnection() != null && body != null && epoch > 0
                ? new MobContext(body.getUUID(), epoch) : null;
    }
}
