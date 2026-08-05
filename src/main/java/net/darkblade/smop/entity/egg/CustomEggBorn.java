package net.darkblade.smop.entity.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Hook for a mob that needs to do something extra the moment it hatches — the Kriftognathus records
 * the biome it was born in, for example. Called by the egg block right before the baby is added to
 * the world.
 */
public interface CustomEggBorn {

    void onEggBorn(ServerLevel level, BlockPos nestPos);
}
