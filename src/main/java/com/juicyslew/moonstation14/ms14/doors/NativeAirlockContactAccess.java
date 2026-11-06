package com.juicyslew.moonstation14.ms14.doors;

import java.util.List;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Per-native-collision-result access to bounded observational airlock contacts. */
public interface NativeAirlockContactAccess {
    record Selection(SubLevel frame, BlockPos pos, BlockState state, int normalX, int normalY, int normalZ,
                     int substep, int iteration) {
        public Selection {
            java.util.Objects.requireNonNull(frame);
            pos = java.util.Objects.requireNonNull(pos).immutable();
            java.util.Objects.requireNonNull(state);
        }
    }

    List<NativeAirlockContact> moonstation14$airlockContacts();

    void moonstation14$recordAirlockContact(NativeAirlockContact contact);

    boolean moonstation14$claimAirlockSelection(SubLevel frame, BlockPos selectedPos, BlockState selectedState,
                                                 double normalX, double normalY, double normalZ,
                                                 int substep, int iteration);
}
