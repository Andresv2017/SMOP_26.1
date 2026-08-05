package net.darkblade.smop.entity.ai.goal.egg;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settles a gravid mob for a moment, then places its egg block.
 *
 * <p><b>Design note.</b> On 1.20.1 this goal took a {@code ProtectOwnEggGoal} directly so it could
 * tell it where the egg went, and had a second constructor that passed {@code null} for the
 * search-based case. It takes a {@code Consumer<BlockPos>} now: the laying goal no longer knows
 * anything about guarding, and {@link EggGoalRegistry} wires the two together.
 */
public class GenericLayEggGoal<T extends SMOPAnimal> extends Goal {

    /** Ticks the mob stays put before the egg appears, and how long it keeps trying. */
    private static final int LAY_DELAY_TICKS = 40;
    private static final int GIVE_UP_TICKS = 60;

    private final T entity;
    private final Supplier<? extends Block> eggBlock;
    @Nullable
    private final Consumer<BlockPos> onLaid;

    private int layEggTimer;

    public GenericLayEggGoal(T entity, Supplier<? extends Block> eggBlock, @Nullable Consumer<BlockPos> onLaid) {
        this.entity = entity;
        this.eggBlock = eggBlock;
        this.onLaid = onLaid;
    }

    @Override
    public boolean canUse() {
        return this.entity.hasEgg() && !this.entity.isMammal();
    }

    @Override
    public boolean canContinueToUse() {
        return this.entity.hasEgg() && this.layEggTimer <= GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        this.layEggTimer = 0;
    }

    @Override
    public void tick() {
        if (++this.layEggTimer <= LAY_DELAY_TICKS) {
            return;
        }
        BlockPos eggPos = this.entity.tryLayEgg(this.eggBlock.get());
        if (eggPos != null && this.onLaid != null) {
            this.onLaid.accept(eggPos);
        }
    }
}
