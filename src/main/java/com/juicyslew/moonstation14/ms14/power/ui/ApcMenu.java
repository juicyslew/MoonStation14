package com.juicyslew.moonstation14.ms14.power.ui;

import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/** APC UI session bound to the already-opened server menu and BE identity. */
public final class ApcMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos pos;
    private final UUID session;
    private final ServerLevel serverLevel;
    private final PowerDeviceBlockEntity serverDevice;

    /** Client-side constructor used by vanilla's menu-open handshake; the client registers the Screen separately. */
    public ApcMenu(int containerId, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readUUID(), null, null,
                new SimpleContainerData(7));
    }

    public ApcMenu(int containerId, Inventory inventory, BlockPos pos, UUID session,
                   ServerLevel level, PowerDeviceBlockEntity device) {
        this(containerId, inventory, pos, session, level, device, serverData(device));
    }

    private ApcMenu(int containerId, Inventory inventory, BlockPos pos, UUID session,
                    ServerLevel level, PowerDeviceBlockEntity device, ContainerData data) {
        super(ModMenus.APC.get(), containerId);
        this.pos = pos.immutable();
        this.session = session;
        this.serverLevel = level;
        this.serverDevice = device;
        this.data = data;
        addDataSlots(data);
    }

    private static ContainerData serverData(PowerDeviceBlockEntity device) {
        return new ContainerData() {
            @Override public int get(int index) {
                if (index == 0) return device.breakerClosed() ? 1 : 0;
                long revision = device.breakerRevision();
                if (index >= 1 && index <= 4) return revisionWord(revision, index - 1);
                if (index == 5) return batteryPermille(device.energyJoules());
                if (index == 6) return device.tripLatched() ? 1 : 0;
                return 0;
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 7; }
        };
    }

    /** Bounded authoritative battery projection: 0..1000 represents 0..100.0%. */
    public static int batteryPermille(double joules) {
        double bounded = PowerDeviceRules.validatedEnergy(joules);
        return (int) Math.round(bounded * 1000.0 / PowerDeviceRules.APC_CAPACITY_JOULES);
    }

    public BlockPos devicePos() { return pos; }
    public UUID session() { return session; }
    public int displayedBreaker() { return data.get(0); }
    /** Reassembles four unsigned menu-data words; vanilla's short wire slots must not narrow a long revision. */
    public long displayedRevision() {
        int[] words = new int[4];
        for (int word = 0; word < words.length; word++) words[word] = data.get(word + 1);
        return assembleRevision(words);
    }
    static int revisionWord(long revision, int word) {
        if (word < 0 || word > 3) throw new IllegalArgumentException("revision word out of range");
        return (int) (revision >>> (word * 16)) & 0xffff;
    }
    static long assembleRevision(int[] words) {
        if (words.length != 4) throw new IllegalArgumentException("revision requires four words");
        long revision = 0;
        for (int word = 0; word < words.length; word++) revision |= (long) (words[word] & 0xffff) << (word * 16);
        return revision;
    }
    public int displayedBatteryPermille() { return data.get(5); }
    public boolean displayedTripLatched() { return data.get(6) != 0; }
    public long currentRevision() { return serverDevice == null ? -1L : serverDevice.breakerRevision(); }
    public PowerDeviceBlockEntity boundDevice() { return serverDevice; }

    @Override public boolean stillValid(Player player) {
        return serverLevel != null && validContext(player);
    }

    public boolean validContext(Player player) {
        if (serverLevel == null || serverDevice == null || player.level() != serverLevel
                || !serverLevel.hasChunkAt(pos)) return false;
        BlockEntity current = serverLevel.getBlockEntity(pos);
        if (current != serverDevice || !(serverLevel.getBlockState(pos).getBlock() instanceof PowerDeviceBlock block)
                || block.kind() != PowerDeviceKind.APC) return false;
        double distance = player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        return PowerDeviceRules.canToggle(true, player.mayBuild(), distance);
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) { return false; }
    @Override public void removed(Player player) { super.removed(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
