package net.darkblade.smop.block;

import net.darkblade.smop.entity.egg.CustomEggBorn;
import net.darkblade.smop.entity.egg.RandomVariantCapable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class AbstractEggBlock extends Block {

    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;

    private static final int TRAMPLE_CHANCE = 100;
    private static final int FALL_CHANCE = 3;

    private final Supplier<? extends EntityType<? extends AgeableMob>> mobType;
    private final int incubationTimeTicks;

    protected AbstractEggBlock(Supplier<? extends EntityType<? extends AgeableMob>> mobType,
                               int incubationTimeTicks,
                               BlockBehaviour.Properties properties) {
        super(properties);
        this.mobType = mobType;
        this.incubationTimeTicks = incubationTimeTicks;
    }

    protected abstract int getHatchCount(BlockState state);

    public BlockState newClutchState(RandomSource random) {
        return this.defaultBlockState();
    }

    protected abstract void breakOneEgg(Level level, BlockPos pos, BlockState state);

    // ───────────────────────────────────────────────────── INCUBATION ─────

    @Override
    protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                           @NotNull BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, this.incubationTimeTicks);
        }
    }

    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                        @NotNull RandomSource random) {
        int hatch = state.getValue(HATCH);
        if (hatch < 2) {
            level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS,
                    0.7F, 0.9F + random.nextFloat() * 0.2F);
            level.setBlock(pos, state.setValue(HATCH, hatch + 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, this.incubationTimeTicks);
            return;
        }

        int count = this.getHatchCount(state);
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS,
                0.7F, 0.9F + random.nextFloat() * 0.2F);
        level.removeBlock(pos, false);

        for (int i = 0; i < count; i++) {
            this.spawnBaby(level, pos, i);
        }
    }

    private void spawnBaby(ServerLevel level, BlockPos pos, int index) {
        AgeableMob baby = this.mobType.get().create(level, EntitySpawnReason.BREEDING);
        if (baby == null) {
            return;
        }
        baby.setAge(-24000);
        baby.snapTo(pos.getX() + 0.3D + index * 0.2D, pos.getY(), pos.getZ() + 0.3D, 0.0F, 0.0F);

        if (baby instanceof RandomVariantCapable variant) {
            variant.setRandomVariant(level.getRandom());
        }
        if (baby instanceof CustomEggBorn born) {
            born.onEggBorn(level, pos);
        }
        level.addFreshEntity(baby);
    }

    // ───────────────────────────────────────────────────── TRAMPLING ─────

    @Override
    public void stepOn(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Entity entity) {
        if (!entity.isSteppingCarefully()) {
            this.tryTrample(level, state, pos, entity, TRAMPLE_CHANCE);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(@NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos,
                       @NotNull Entity entity, double fallDistance) {
        if (!(entity instanceof Zombie)) {
            this.tryTrample(level, state, pos, entity, FALL_CHANCE);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    private void tryTrample(Level level, BlockState state, BlockPos pos, Entity entity, int chance) {
        if (state.is(this)
                && level instanceof ServerLevel serverLevel
                && this.canTrample(serverLevel, entity)
                && level.getRandom().nextInt(chance) == 0) {
            this.breakOneEgg(level, pos, state);
        }
    }

    private boolean canTrample(ServerLevel level, Entity entity) {
        if (entity.getType() == this.mobType.get() || entity instanceof Bat) {
            return false;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return entity instanceof Player || EventHooks.canEntityGrief(level, entity);
    }

    protected void playBreakSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS,
                0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    // ───────────────────────────────────────────────────── SUPPORT ─────

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected boolean propagatesSkylightDown(@NotNull BlockState state) {
        return true;
    }

    protected BlockState popOffIfUnsupported(BlockState state, LevelReader level, BlockPos pos) {
        return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}
