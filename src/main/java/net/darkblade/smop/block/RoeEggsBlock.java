package net.darkblade.smop.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Underwater roe: a translucent clutch that can only sit in a water source over solid ground, and
 * releases a school of fry after a randomised delay. The Salmon's spawn.
 *
 * <p>Unlike the land eggs it has no visible incubation stages — it either exists or it has hatched —
 * so it does not extend {@link AbstractEggBlock}, which is built around the {@code HATCH} property.
 */
public class RoeEggsBlock extends Block {

    /**
     * A film on the river bed, matching vanilla frogspawn exactly (1.5 px tall).
     *
     * <p>Without this the block kept {@code Block}'s default full-cube shape: the model was already
     * the flat plane, but the selection outline was a whole cube floating around it, so pointing at
     * a clutch highlighted a block-sized box in the water.
     */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.5D, 16.0D);

    private final Supplier<? extends EntityType<? extends LivingEntity>> mobType;
    private final int minSpawn;
    private final int maxSpawn;
    private final int hatchDelayMin;
    private final int hatchDelayMax;
    private final SoundEvent hatchSound;

    public RoeEggsBlock(Supplier<? extends EntityType<? extends LivingEntity>> mobType,
                        int minSpawn, int maxSpawn,
                        int hatchDelayMin, int hatchDelayMax,
                        SoundEvent hatchSound,
                        Properties properties) {
        super(properties);
        this.mobType = mobType;
        this.minSpawn = minSpawn;
        this.maxSpawn = maxSpawn;
        this.hatchDelayMin = hatchDelayMin;
        this.hatchDelayMax = hatchDelayMax;
        this.hatchSound = hatchSound;
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    // ───────────────────────────────────────────────────── PLACEMENT ─────

    /** Only inside a full water source that has something solid underneath it. */
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.isValidNest(level.getFluidState(pos), level, pos) ? this.defaultBlockState() : null;
    }

    @Override
    protected boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext context) {
        return context.getLevel().getFluidState(context.getClickedPos()).is(FluidTags.WATER);
    }

    /** Reports itself as water so the block does not carve a dry pocket out of the sea. */
    @Override
    protected @NotNull FluidState getFluidState(@NotNull BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return this.isValidNest(level.getFluidState(pos), level, pos);
    }

    private boolean isValidNest(FluidState fluid, BlockGetter level, BlockPos pos) {
        return fluid.getType() == Fluids.WATER
                && fluid.isSource()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull LevelReader level,
                                              @NotNull ScheduledTickAccess ticks, @NotNull BlockPos pos,
                                              @NotNull Direction directionToNeighbour, @NotNull BlockPos neighbourPos,
                                              @NotNull BlockState neighbourState, @NotNull RandomSource random) {
        if (!this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        // Keep the surrounding water ticking so it re-flows around the block.
        ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }

    /** A falling block landing in the nest destroys it. */
    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Entity entity, @NotNull InsideBlockEffectApplier effectApplier,
                                boolean isPrecise) {
        if (entity.getType() == EntityType.FALLING_BLOCK) {
            level.destroyBlock(pos, false);
        }
    }

    // ───────────────────────────────────────────────────── HATCHING ─────

    @Override
    protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                           @NotNull BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, this.randomHatchDelay(level.getRandom()));
        }
    }

    private int randomHatchDelay(RandomSource random) {
        return random.nextInt(this.hatchDelayMin, this.hatchDelayMax + 1);
    }

    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                        @NotNull RandomSource random) {
        if (!this.canSurvive(state, level, pos)) {
            level.destroyBlock(pos, false);
            return;
        }

        level.destroyBlock(pos, false);
        level.playSound(null, pos, this.hatchSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.spawnFry(level, pos, random);
        level.sendParticles(ParticleTypes.HEART,
                pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                6, 0.25D, 0.1D, 0.25D, 0.0D);
    }

    private void spawnFry(ServerLevel level, BlockPos pos, RandomSource random) {
        int count = random.nextInt(this.minSpawn, this.maxSpawn + 1);
        EntityType<? extends LivingEntity> type = this.mobType.get();

        for (int i = 0; i < count; i++) {
            LivingEntity fry = type.create(level, EntitySpawnReason.BREEDING);
            if (fry == null) {
                continue;
            }
            // Scatter across the block so the school does not spawn inside itself.
            //
            // The Y is the clutch's own block, NOT half a block under it. The roe sits ON the river
            // bed, so pos.getY() - 0.5 dropped every fry into the solid bed below: they spawned
            // buried, took suffocation damage immediately, and a 10 HP baby was dead long before
            // anyone saw it — which is why hatching appeared to produce nothing at all. The clutch
            // block has just been destroyed and, because it reported itself as a water source, has
            // refilled with water, so this position is open water.
            double x = pos.getX() + Mth.clamp(random.nextDouble(), 0.2D, 0.8D);
            double z = pos.getZ() + Mth.clamp(random.nextDouble(), 0.2D, 0.8D);
            fry.snapTo(x, pos.getY(), z, random.nextFloat() * 360.0F, 0.0F);

            if (fry instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.BREEDING, null);
                // Fry are a bred population, not ambient spawns — do not let them despawn.
                mob.setPersistenceRequired();
            }
            if (fry instanceof AgeableMob ageable) {
                ageable.setAge(-24000);
            }
            level.addFreshEntity(fry);
        }
    }
}
