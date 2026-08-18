package net.darkblade.smop.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A single large egg that hatches one baby — the Nirasmosaurus nest. Sized in pixels at
 * construction so one class covers eggs of any dimensions.
 *
 * <p><b>Waterloggable</b>, which for this egg is not a nicety: the Nirasmosaurus lays on the sea bed,
 * and a block that displaced the water source it was laid in would leave a one-block air pocket at
 * the bottom of the ocean. {@code RoeEggsBlock} avoids the same hole by declaring itself water
 * unconditionally, but that only works for a block that can exist nowhere else. This one is placeable
 * on dry land too, so it carries the state instead.
 */
public class EggBlock extends AbstractEggBlock implements SimpleWaterloggedBlock {

    private final VoxelShape shape;

    /**
     * @param widthPx  footprint in pixels, centred on the block
     * @param heightPx height in pixels
     */
    public EggBlock(Supplier<? extends EntityType<? extends AgeableMob>> mobType, int incubationTimeTicks,
                    int widthPx, int heightPx, BlockBehaviour.Properties properties) {
        super(mobType, incubationTimeTicks, properties);
        int margin = Math.max(0, (16 - widthPx) / 2);
        this.shape = Block.box(margin, 0.0D, margin, 16 - margin, heightPx, 16 - margin);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HATCH, 0)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected @NotNull FluidState getFluidState(@NotNull BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    /** Placed wet in water and dry out of it, like any waterloggable block. */
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, fluid.getType() == Fluids.WATER && fluid.isSource());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(HATCH, BlockStateProperties.WATERLOGGED);
    }

    @Override
    protected int getHatchCount(@NotNull BlockState state) {
        return 1;
    }

    @Override
    protected void breakOneEgg(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state) {
        this.playBreakSound(level, pos);
        level.destroyBlock(pos, false);
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return this.shape;
    }

    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull LevelReader level,
                                              @NotNull ScheduledTickAccess ticks, @NotNull BlockPos pos,
                                              @NotNull Direction directionToNeighbour, @NotNull BlockPos neighbourPos,
                                              @NotNull BlockState neighbourState, @NotNull RandomSource random) {
        // Keep the water around a submerged nest flowing, or the sea would stop updating through the
        // egg's cell the moment a neighbour changed.
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return this.popOffIfUnsupported(state, level, pos);
    }

    @Override
    public void playerDestroy(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos,
                              @NotNull BlockState state, @Nullable BlockEntity blockEntity,
                              @NotNull ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        this.playBreakSound(level, pos);
    }
}
