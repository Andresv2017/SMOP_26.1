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

/**
 * Shared behaviour for SMOP's land egg blocks: a timed incubation that visibly cracks through
 * {@link #HATCH} stages before hatching, and eggs that break when trampled.
 *
 * <p>Modelled on vanilla's {@code TurtleEggBlock}, with one deliberate difference: turtle eggs
 * advance on random ticks gated by a biome temperature attribute, whereas these use a scheduled tick
 * with a fixed period, so a species' incubation time is an authored number rather than a
 * climate-dependent lottery.
 *
 * <p><b>Port note.</b> 1.20.1 had {@code EggBlock} and {@code SmallEggsBlock} as two ~170-line
 * classes that were about 80% identical. Everything shared lives here now; the subclasses only
 * describe their shape and what "one egg breaking" means for them.
 */
public abstract class AbstractEggBlock extends Block {

    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;

    /** Chance denominator for a trample to break an egg — 1-in-100 walking, 1-in-3 landing on it. */
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

    /** How many babies this block's current state hatches into. */
    protected abstract int getHatchCount(BlockState state);

    /**
     * The state a clutch laid by a mob starts in — see {@code SMOPAnimal#tryLayEgg}.
     *
     * <p>Lives on the block, not on the animal, because how many eggs fit in one is the block's
     * business: a single-egg species has no clutch size to roll, and the default here says exactly
     * that. Player-placed eggs deliberately do <em>not</em> go through this — those keep stacking
     * one at a time like vanilla turtle eggs.
     */
    public BlockState newClutchState(RandomSource random) {
        return this.defaultBlockState();
    }

    /** Break one egg — destroying the block outright, or decrementing a clutch. */
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
        // Fan a clutch out slightly so the babies do not all stack on one block centre.
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
        // Zombies are exempt so a horde cannot wipe a nest out on the way past — vanilla does the
        // same for turtle eggs, where zombies instead seek eggs out deliberately.
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

    /** The parent species and bats never break their own nests; everything else needs mob griefing on. */
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

    /** Falls apart if whatever it was resting on is removed. */
    protected BlockState popOffIfUnsupported(BlockState state, LevelReader level, BlockPos pos) {
        return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}
