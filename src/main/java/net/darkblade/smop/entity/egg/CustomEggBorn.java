package net.darkblade.smop.entity.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface CustomEggBorn {

    void onEggBorn(ServerLevel level, BlockPos nestPos);
}
