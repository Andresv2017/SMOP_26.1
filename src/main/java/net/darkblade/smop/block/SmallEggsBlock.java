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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SmallEggsBlock extends AbstractEggBlock {

    public static final IntegerProperty EGGS = BlockStateProperties.EGGS;
    public static final int MAX_EGGS = 4;

    private static final VoxelShape SHAPE_SINGLE = Block.box(3.0D, 0.0D, 3.0D, 12.0D, 7.0D, 12.0D);
    private static final VoxelShape SHAPE_MULTIPLE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);

    public SmallEggsBlock(Supplier<? extends EntityType<? extends AgeableMob>> mobType, int incubationTimeTicks,
                          BlockBehaviour.Properties properties) {
        super(mobType, incubationTimeTicks, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0).setValue(EGGS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(HATCH, EGGS);
    }

    @Override
    protected int getHatchCount(@NotNull BlockState state) {
        return state.getValue(EGGS);
    }

    private static final int[] CLUTCH_WEIGHTS = {35, 35, 20, 10};
    private static final int CLUTCH_WEIGHT_TOTAL = java.util.Arrays.stream(CLUTCH_WEIGHTS).sum();

    @Override
    public @NotNull BlockState newClutchState(@NotNull RandomSource random) {
        return this.defaultBlockState().setValue(EGGS, rollClutchSize(random));
    }

    private static int rollClutchSize(RandomSource random) {
        int roll = random.nextInt(CLUTCH_WEIGHT_TOTAL);
        for (int i = 0; i < CLUTCH_WEIGHTS.length; i++) {
            roll -= CLUTCH_WEIGHTS[i];
            if (roll < 0) {
                return Math.min(i + 1, MAX_EGGS);
            }
        }
        return 1;
    }

    @Override
    protected void breakOneEgg(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state) {
        this.playBreakSound(level, pos);
        int eggs = state.getValue(EGGS);
        if (eggs <= 1) {
            level.destroyBlock(pos, false);
            return;
        }
        level.setBlock(pos, state.setValue(EGGS, eggs - 1), Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
        level.levelEvent(2001, pos, Block.getId(state));
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return state.getValue(EGGS) == 1 ? SHAPE_SINGLE : SHAPE_MULTIPLE;
    }

    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull LevelReader level,
                                              @NotNull ScheduledTickAccess ticks, @NotNull BlockPos pos,
                                              @NotNull Direction directionToNeighbour, @NotNull BlockPos neighbourPos,
                                              @NotNull BlockState neighbourState, @NotNull RandomSource random) {
        return this.popOffIfUnsupported(state, level, pos);
    }

    @Override
    protected boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext context) {
        boolean addingToClutch = !context.isSecondaryUseActive()
                && context.getItemInHand().is(this.asItem())
                && state.getValue(EGGS) < MAX_EGGS;
        return addingToClutch || super.canBeReplaced(state, context);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        return existing.is(this)
                ? existing.setValue(EGGS, Math.min(MAX_EGGS, existing.getValue(EGGS) + 1))
                : super.getStateForPlacement(context);
    }

    @Override
    public void playerDestroy(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos,
                              @NotNull BlockState state, @Nullable BlockEntity blockEntity,
                              @NotNull ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        this.breakOneEgg(level, pos, state);
    }
}
