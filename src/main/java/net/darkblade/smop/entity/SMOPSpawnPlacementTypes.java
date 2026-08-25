package net.darkblade.smop.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class SMOPSpawnPlacementTypes {

    public static final SpawnPlacementType IN_WATER_OR_ON_SHORE = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader level, BlockPos pos, @Nullable EntityType<?> type) {
            if (type == null || !level.getWorldBorder().isWithinBounds(pos)) {
                return false;
            }

            BlockPos above = pos.above();
            if (level.getFluidState(pos).is(FluidTags.WATER)
                    && !level.getBlockState(above).isRedstoneConductor(level, above)) {
                return true;
            }

            BlockPos below = pos.below();
            return level.getBlockState(below).isValidSpawn(level, below, type)
                    && isValidEmptySpawnBlock(level, pos, type)
                    && isValidEmptySpawnBlock(level, above, type);
        }

        private boolean isValidEmptySpawnBlock(LevelReader level, BlockPos pos, EntityType<?> type) {
            BlockState state = level.getBlockState(pos);
            return NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), type);
        }
    };

    private SMOPSpawnPlacementTypes() {}
}
