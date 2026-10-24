package com.juicyslew.moonstation14.ms14.combat;

/** Client networking bridge and predicted combat-mode action owner. */
public final class CombatModeClient {
    private static final CombatModeClientPolicy POLICY = new CombatModeClientPolicy();
    private static final long REQUEST_TIMEOUT_TICKS = 100;
    private CombatModeClient() { }
    public static void handleReply(CombatModeRequestResult result) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> POLICY.acknowledge(result));
    }
    public static boolean requestToggle() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null
                || !(mc.player.isSpectator())) return false;
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        if (body == null || epoch <= 0 || !body.getUUID().equals(POLICY.body()) || epoch != POLICY.epoch()) return false;
        var request = POLICY.toggle(mc.level.getGameTime());
        if (request == null) return false;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        return true;
    }
    /** Called by the existing body-hand lifecycle owner once each client tick. */
    public static void reconcile(net.minecraft.world.entity.Mob body, long epoch, long now) {
        if (body == null || epoch <= 0) { POLICY.clear(); return; }
        var replicated = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.COMBAT_MODE.get());
        POLICY.attachmentUpdate(body.getUUID(), epoch, replicated != null && replicated.inCombatMode());
        POLICY.timeout(now, REQUEST_TIMEOUT_TICKS);
    }
    public static void clearContext() { POLICY.clear(); }
    public static CombatModeClientPolicy policy() { return POLICY; }
    public static boolean projectedMode() { return POLICY.mode(); }
    /** Capability is profile-resolved and independent of whether the default-OFF attachment exists. */
    public static boolean hasCapability(net.minecraft.world.entity.Mob body) {
        return CombatModeSystem.capability(body).isPresent();
    }
}
