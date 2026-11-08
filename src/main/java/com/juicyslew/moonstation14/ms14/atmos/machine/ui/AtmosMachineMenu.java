package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Slotless exact machine capability; client instances contain display identity only. */
public final class AtmosMachineMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final UUID session;
    private final String profileId;
    private final ServerLevel level;
    private final AtmosMachineBlockEntity machine;
    private final UUID placementIdentity;
    private final HandActorAuthority.Snapshot authority;
    private final BodyBlockUseRequest originalRequest;
    private long lastRequest = -1;
    private int snapshotTicks;

    public AtmosMachineMenu(MenuType<?> type, int id, Inventory inventory, FriendlyByteBuf data) {
        this(type,id,data.readBlockPos(),data.readUUID(),data.readUtf(64),null,null,null,null,null);
    }
    /** Standard client menu factory; server instances use the injected constructor below. */
    public AtmosMachineMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(com.juicyslew.moonstation14.block.ModMenus.ATMOS_MACHINE.get(),id,inventory,data);
    }
    public AtmosMachineMenu(MenuType<?> type,int id,BlockPos pos,UUID session,String profileId,ServerLevel level,
            AtmosMachineBlockEntity machine,HandActorAuthority.Snapshot authority,BodyBlockUseRequest request,UUID placementIdentity) {
        super(type,id);this.pos=pos.immutable();this.session=session;this.profileId=profileId;this.level=level;
        this.machine=machine;this.authority=authority;this.originalRequest=request;this.placementIdentity=placementIdentity;
    }
    public UUID session(){return session;} public BlockPos pos(){return pos;} public String profileId(){return profileId;}
    public int menuId(){return containerId;}
    public ServerLevel serverLevel(){return level;} public AtmosMachineBlockEntity machine(){return machine;}
    public UUID placementIdentity(){return placementIdentity;} HandActorAuthority.Snapshot authority(){return authority;}
    BodyBlockUseRequest originalRequest(){return originalRequest;} long lastRequest(){return lastRequest;}
    void lastRequest(long value){lastRequest=value;}
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (level == null) return;
        if (++snapshotTicks >= 10) {
            snapshotTicks = 0;
            if (authority.actor() instanceof net.minecraft.server.level.ServerPlayer actor && stillValid(actor)) AtmosMachineNetworking.send(actor,
                    AtmosMachineMenuService.snapshot(this, lastRequest, true));
        }
    }
    @Override public boolean stillValid(Player player){return level==null||AtmosMachineMenuService.persistentValidity(player,this);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
