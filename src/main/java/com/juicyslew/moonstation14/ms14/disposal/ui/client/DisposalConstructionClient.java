package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionState;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

/** Interim, non-screen disposal construction controls. Intents request authority; they never place locally. */
public final class DisposalConstructionClient {
    public enum Action { ENTER, SELECT, ROTATE, EXIT }
    public record Intent(Action action, String prototype) { }
    public record ClientState(UUID bodyUUID, long epoch, boolean active, String prototype, int rotationOffset, long revision) { }
    public enum Readiness { WRONG_MATERIAL, INSUFFICIENT, READY }
    public record ReadinessView(Readiness state, int held, int required) { }

    private static final List<String> SHAPES = List.of("disposal_pipe", "disposal_bend", "disposal_trunk", "disposal_junction",
            "disposal_junction_flipped", "disposal_y_junction", "disposal_x_junction", "disposal_tagger", "disposal_router", "disposal_router_flipped");
    private static final KeyMapping CHANGE = key("key.moonstation14.disposal_construction_change", GLFW.GLFW_KEY_G);
    private static final KeyMapping EXIT = key("key.moonstation14.disposal_construction_exit", GLFW.GLFW_KEY_H);
    private static final KeyMapping ROTATE = key("key.moonstation14.disposal_construction_rotate", GLFW.GLFW_KEY_R);
    private static Consumer<Intent> sender = ignored -> { };
    private static ClientState state;
    private static UUID boundBody;
    private static long boundEpoch;
    private static Object connection, level;
    private static boolean palette;
    private static int cursor;
    private static long sequence;
    private static long lastAcceptedSequence;
    private static DisposalConstructionPreview.PlacementStatus placement = new DisposalConstructionPreview.PlacementStatus(false, false, "no_target");
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
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> clear());
    }
    private static void send(Intent intent) {
        MobContext context = current(); if (context == null) return;
        if (!context.id.equals(boundBody) || context.epoch != boundEpoch) { sequence = 0; lastAcceptedSequence = 0; boundBody = context.id; boundEpoch = context.epoch; }
        if (sequence == Long.MAX_VALUE) return;
        long next = ++sequence;
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
                incoming.revision(), current.id, current.epoch, sequence, lastAcceptedSequence,
                state == null ? -1 : state.revision())) return;
        lastAcceptedSequence = packetSequence;
        state = incoming; boundBody = incoming.bodyUUID(); boundEpoch = incoming.epoch();
        int found = SHAPES.indexOf(shortName(incoming.prototype())); if (found >= 0) cursor = found;
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
    public static boolean paletteOpen() { return palette; }
    public static List<String> supportedShapes() { return SHAPES; }
    public static int wrapShapeIndex(int index) { return Math.floorMod(index, SHAPES.size()); }
    public static ReadinessView readiness(String materialId, int required, ItemStack held) {
        if (held == null || held.isEmpty() || !held.is(material(materialId))) return new ReadinessView(Readiness.WRONG_MATERIAL, held == null ? 0 : held.getCount(), required);
        return new ReadinessView(held.getCount() < required ? Readiness.INSUFFICIENT : Readiness.READY, held.getCount(), required);
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
        DisposalConstructionPreview.setReadiness(value -> placement = value);
    }
    public static void setTubeRecordProvider(java.util.function.BiFunction<net.minecraft.core.BlockPos, ResourceLocation,
            java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord>> provider) {
        tubeRecords = Objects.requireNonNull(provider);
        DisposalConstructionPreview.setTubeRecordProvider(provider);
    }
    public static java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord> tubeRecord(
            net.minecraft.core.BlockPos pos, ResourceLocation dimension) { return tubeRecords.apply(pos, dimension); }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); MobContext context = current();
        if (connection != mc.getConnection() || level != mc.level || context == null
                || !Objects.equals(boundBody, context.id) || boundEpoch != context.epoch) {
            clear(); connection = mc.getConnection(); level = mc.level;
        }
        if (context == null || mc.screen != null) { palette = false; consumeKeys(); return; }
        boolean changed = CHANGE.consumeClick(), exited = EXIT.consumeClick(), rotated = ROTATE.consumeClick();
        if (changed) {
            if (state == null || !state.active()) { cursor = 0; palette = true; sender.accept(new Intent(Action.ENTER, null)); }
            else { palette = !palette; if (palette && state.prototype() != null) cursor = Math.max(0, SHAPES.indexOf(shortName(state.prototype()))); }
        }
        if (exited && state != null && state.active()) { palette = false; state = null; sender.accept(new Intent(Action.EXIT, null)); }
        if (rotated && state != null && state.active() && placement.aimed()) sender.accept(new Intent(Action.ROTATE, null));
        if (!CHANGE.isDown()) palette = false;
    }
    private static void consumeKeys() { while (CHANGE.consumeClick()) {} while (EXIT.consumeClick()) {} while (ROTATE.consumeClick()) {} }
    private static void scroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (!palette || state == null || !state.active() || Minecraft.getInstance().screen != null || event.getScrollDeltaY() == 0) return;
        cursor = wrapShapeIndex(cursor + (event.getScrollDeltaY() > 0 ? -1 : 1));
        sender.accept(new Intent(Action.SELECT, SHAPES.get(cursor))); event.setCanceled(true);
    }
    private static void render(RenderGuiEvent.Post event) {
        if (state == null || !state.active() || Minecraft.getInstance().player == null || Minecraft.getInstance().options.hideGui) return;
        Minecraft mc = Minecraft.getInstance();
        String proto = state.prototype(); var data = proto == null ? null : PrototypeRuntime.clientDisposals().get(ResourceLocation.tryParse(proto));
        var step = data == null ? null : data.construction();
        ItemStack held = BodyHandClient.activeHeldStackFor(state.bodyUUID(), state.epoch()).orElse(ItemStack.EMPTY);
        ReadinessView ready = readiness(step == null ? "" : step.material(), step == null ? 1 : step.steelCount(), held);
        int color = ready.state() == Readiness.READY ? 0xff55dd66 : ready.state() == Readiness.INSUFFICIENT ? 0xffffcc44 : 0xffff5555;
        var g = event.getGuiGraphics(); int x = 8, y = mc.getWindow().getGuiScaledHeight() - (palette ? 190 : 76);
        g.drawString(mc.font, Component.translatable("moonstation14.disposal.construction.selected", name(proto)), x, y, 0xffffffff);
        Component materialName = new ItemStack(material(step == null ? null : step.material())).getHoverName();
        String status = ready.state() == Readiness.READY ? "moonstation14.disposal.construction.ready" : ready.state() == Readiness.INSUFFICIENT
                ? "moonstation14.disposal.construction.insufficient" : "moonstation14.disposal.construction.wrong_material";
        g.drawString(mc.font, Component.translatable(status, materialName, ready.held(), ready.required()), x, y + 12, color);
        g.drawString(mc.font, Component.translatable("moonstation14.disposal.construction.controls", CHANGE.getTranslatedKeyMessage(), EXIT.getTranslatedKeyMessage(), ROTATE.getTranslatedKeyMessage()), x, y + 24, 0xffdddddd);
        if (placement.aimed() && !placement.valid()) g.drawString(mc.font, Component.translatable("moonstation14.disposal.construction.blocked", placement.reason()), x, y + 36, 0xffff5555);
        if (palette) {
            int py = y + (placement.aimed() && !placement.valid() ? 48 : 36);
            g.drawString(mc.font, Component.translatable("moonstation14.disposal.construction.palette", CHANGE.getTranslatedKeyMessage()), x, py, 0xffdddddd);
            for (int i = 0; i < SHAPES.size(); i++) {
                int lineY = py + 12 + i * 10;
                int c = i == cursor ? 0xffffff55 : 0xffdddddd;
                g.drawString(mc.font, name(SHAPES.get(i)), x, lineY, c);
            }
        }
    }
    private static String shortName(String id) { return id == null ? null : id.substring(id.indexOf(':') + 1); }
    private static Component name(String id) { return Component.translatable("moonstation14.disposal.prototype." + (id == null ? "unknown" : shortName(id))); }
    private static void clear() { state = null; boundBody = null; boundEpoch = 0; palette = false; cursor = 0; sequence = 0; lastAcceptedSequence = 0; placement = new DisposalConstructionPreview.PlacementStatus(false, false, "no_target"); }
    private record MobContext(UUID id, long epoch) { }
    private static MobContext current() {
        Minecraft mc = Minecraft.getInstance(); var body = GhostControlClient.ownedCharacterForHud(); long epoch = GhostControlClient.committedCharacterEpoch();
        return mc.player != null && mc.player.isSpectator() && mc.getConnection() != null && body != null && epoch > 0
                ? new MobContext(body.getUUID(), epoch) : null;
    }
}
