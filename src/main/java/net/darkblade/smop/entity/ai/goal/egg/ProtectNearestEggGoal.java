package net.darkblade.smop.entity.ai.goal.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Guards whichever matching nest is nearest — colonial species where any adult minds any clutch.
 *
 * <p>The search is a box scan, kept cheap by a shallow vertical range (nests sit on the ground) and
 * by only running when the goal is not already engaged.
 */
public class ProtectNearestEggGoal extends ProtectEggBaseGoal {

    /** Nests are on the ground, so there is no point scanning a tall column. */
    private static final int VERTICAL_SEARCH = 3;

    private final List<Supplier<? extends Block>> eggBlocks;
    private final int searchRadius;

    public ProtectNearestEggGoal(Animal mob, int searchRadius, int stayNearEggRadius, int defenseRadius,
                                 boolean attackOnApproach, boolean attackOnBreak,
                                 Predicate<LivingEntity> enemySelector, EggBreakReaction eggBreakReaction,
                                 List<Supplier<? extends Block>> eggBlocks) {
        super(mob, stayNearEggRadius, defenseRadius, attackOnApproach, attackOnBreak,
                enemySelector, eggBreakReaction);
        this.searchRadius = searchRadius;
        this.eggBlocks = eggBlocks;
    }

    @Override
    protected @Nullable BlockPos findTargetEgg() {
        Level level = this.mob.level();
        if (level.isClientSide()) {
            return null;
        }

        BlockPos origin = this.mob.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -VERTICAL_SEARCH; y <= VERTICAL_SEARCH; y++) {
            for (int x = -this.searchRadius; x <= this.searchRadius; x++) {
                for (int z = -this.searchRadius; z <= this.searchRadius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    Block block = level.getBlockState(cursor).getBlock();
                    for (Supplier<? extends Block> eggBlock : this.eggBlocks) {
                        if (block == eggBlock.get()) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }
}
