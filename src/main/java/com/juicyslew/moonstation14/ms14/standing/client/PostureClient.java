package com.juicyslew.moonstation14.ms14.standing.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.standing.PostureToggleRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class PostureClient {
    private static long lastMoveWishTick = Long.MIN_VALUE;
    private static int lastMoveWishMask;
    private static final java.util.Map<java.util.UUID, Boolean> LAST_POSE = new java.util.HashMap<>();
    private static final KeyMapping TOGGLE = new KeyMapping("key.moonstation14.toggle_prone",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.moonstation14");
    private static final KeyMapping ROLL = new KeyMapping("key.moonstation14.roll_prone",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.moonstation14");
    private PostureClient() { }
    public static void register(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(ROLL); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) continue;
                present.add(living.getUUID());
                var attachment = living.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
                boolean down = attachment != null && attachment.data().down();
                Boolean previous = LAST_POSE.put(living.getUUID(), down);
                if (previous == null || previous != down) {
                    com.juicyslew.moonstation14.ms14.standing.PhysicalPosture.apply(living);
                    living.refreshDimensions();
                }
            }
            LAST_POSE.keySet().retainAll(present);
        } else LAST_POSE.clear();
        if (mc.player == null) { lastMoveWishMask = 0; return; }
        int moveWishMask = (mc.options.keyUp.isDown() ? 1 : 0) | (mc.options.keyDown.isDown() ? 2 : 0)
                | (mc.options.keyLeft.isDown() ? 4 : 0) | (mc.options.keyRight.isDown() ? 8 : 0);
        boolean directionalWish = moveWishMask != 0;
        int previousWishMask = lastMoveWishMask;
        lastMoveWishMask = moveWishMask;
        if (com.juicyslew.moonstation14.ms14.standing.MovementWishPolicy.shouldNotify(moveWishMask,
                previousWishMask,
                mc.player.level().getGameTime(), lastMoveWishTick, 5)) {
            lastMoveWishTick = mc.player.level().getGameTime();
            PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.standing.PulledBodyMoveRequest(directionalWish));
        }
        while (TOGGLE.consumeClick()) PacketDistributor.sendToServer(new PostureToggleRequest());
        while (ROLL.consumeClick()) {
            long window = mc.getWindow().getWindow();
            boolean turnOther = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (!turnOther) {
                PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.standing.PostureRollRequest(null));
                if (directionalWish) {
                    PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.standing.PulledBodyMoveRequest());
                    lastMoveWishTick = mc.player.level().getGameTime();
                }
                continue;
            }
            net.minecraft.world.entity.LivingEntity actor = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            if (actor == null && mc.player != null && !mc.player.isSpectator()) actor = mc.player;
            var hit = actor == null ? null : aimedDownBody(actor);
            if (hit == null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.moonstation14.turn_body_target"), true);
                continue;
            }
            PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.standing.PostureRollRequest(hit.getUUID()));
        }
    }

    private static net.minecraft.world.entity.LivingEntity aimedDownBody(net.minecraft.world.entity.LivingEntity actor) {
        var start = actor.getEyePosition();
        var end = start.add(actor.getLookAngle().scale(4.5));
        var block = actor.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, actor));
        double limit = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? start.distanceTo(block.getLocation()) : 4.5;
        return actor.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        actor.getBoundingBox().inflate(4.5, 2.5, 4.5), target -> target != actor && !target.isRemoved())
                .stream().filter(target -> {
                    var posture = target.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
                    if (posture == null || !posture.data().down()) return false;
                    var box = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target);
                    return com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay
                            .hit(box, start, end, 4.5, limit).isPresent();
                }).min(java.util.Comparator.comparingDouble(target -> com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target)
                        .clip(start, end).map(start::distanceToSqr).orElse(Double.MAX_VALUE))).orElse(null);
    }
}
