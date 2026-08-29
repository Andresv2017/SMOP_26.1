package net.darkblade.smop.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries no data of its own. It exists only so the head has something for a
 * {@code BlockEntityRenderer} to hang off: the trophy's shape is an entity model, which the
 * ordinary block-model pipeline cannot draw.
 */
public class GTHeadBlockEntity extends BlockEntity {

    public GTHeadBlockEntity(BlockPos pos, BlockState state) {
        super(SMOPBlockEntities.GT_HEAD.get(), pos, state);
    }
}
