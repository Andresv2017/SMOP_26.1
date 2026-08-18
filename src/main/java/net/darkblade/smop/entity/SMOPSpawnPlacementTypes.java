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

/**
 * Placement types vanilla does not ship.
 *
 * <p>A placement type answers one question — "is this block a place this species could be put down?"
 * — and it is the FIRST gate a candidate position passes: {@code NaturalSpawner
 * #isValidSpawnPostitionForType} calls {@code SpawnPlacements#isSpawnPositionOk} before it ever
 * reaches the entity's own {@code checkSpawnRules} predicate. That order is why a mob registered
 * under {@link SpawnPlacementTypes#IN_WATER} can never see a dry position, however permissive its
 * own rule is.
 */
public final class SMOPSpawnPlacementTypes {

    /**
     * Water, or the shoreline a marine animal could have hauled out onto — vanilla's
     * {@link SpawnPlacementTypes#IN_WATER} and {@link SpawnPlacementTypes#ON_GROUND} in one type.
     *
     * <p>Vanilla has no such thing because it has no such animal: everything {@code IN_WATER} is
     * aquatic and everything {@code CREATURE} is {@code ON_GROUND}. The turtle, which is the closest
     * vanilla comes to "marine animal that comes ashore", is plain {@code ON_GROUND} on beach sand
     * and reaches the sea by walking. The Nirasmosaurus wants both halves: the ocean is where the
     * population lives, and the beach is where it is worth seeing one out of the water.
     *
     * <p>Both branches are lifted verbatim from their vanilla counterparts rather than approximated:
     * the water branch demands a non-conducting block above so it is not entombed, and the ground
     * branch demands a spawnable block below plus two clear blocks — the animal is 1.6 tall, so two
     * is what it needs.
     *
     * <p><b>No {@code adjustSpawnPosition} override, deliberately.</b> {@code ON_GROUND} overrides it
     * to step down one block, and that hook is read by {@code NaturalSpawner#getTopNonCollidingPos},
     * which only the world-generation pass uses. Leaving it as the identity is what keeps the two
     * generation paths honest: over a beach the heightmap already lands on the air above the sand, so
     * chunk generation seeds the shoreline the way it seeds turtles; over open water it lands on the
     * air above the surface, where neither branch accepts, so the ocean population comes purely from
     * the periodic cycle and its whole-column Y roll. Stepping down would put world-gen animals at the
     * water surface, which is the behaviour {@code SMOPEntities} went out of its way to avoid.
     */
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
