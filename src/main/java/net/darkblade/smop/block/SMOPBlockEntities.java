package net.darkblade.smop.block;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SMOPBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SMOP.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GTHeadBlockEntity>> GT_HEAD =
            BLOCK_ENTITIES.register("gt_head",
                    () -> new BlockEntityType<>(GTHeadBlockEntity::new, SMOPBlocks.GT_HEAD.get()));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private SMOPBlockEntities() {}
}
