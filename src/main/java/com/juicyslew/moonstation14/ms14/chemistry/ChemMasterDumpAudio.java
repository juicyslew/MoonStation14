package com.juicyslew.moonstation14.ms14.chemistry;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Server-side positional sound for an accepted ChemMaster solution dump. */
public final class ChemMasterDumpAudio {
    public static final float GAIN = (float) Math.pow(10.0, -3.0 / 20.0);
    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "chemistry/chemmaster/dump");

    private ChemMasterDumpAudio() { }

    public static void playAccepted(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        ControlledSoundDispatch.emit(level, SOUND, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, GAIN);
    }
}
