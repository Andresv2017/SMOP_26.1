package net.darkblade.smop.block;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SMOPBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SMOP.MOD_ID);

    public static final DeferredBlock<SmallEggsBlock> TANGOFTERO_EGG =
            BLOCKS.registerBlock("tangoftero_egg",
                    props -> new SmallEggsBlock(SMOPEntities.TANGOFTERO, 300, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG));

    public static final DeferredBlock<RoeEggsBlock> SALMON_ROE_EGGS =
            BLOCKS.registerBlock("salmon_roe_eggs",
                    props -> new RoeEggsBlock(SMOPEntities.SALMON, 3, 6, 200, 800,
                            SoundEvents.TURTLE_EGG_HATCH, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.FROGSPAWN).noOcclusion());

    public static final DeferredBlock<SmallEggsBlock> KRIFTO_EGG =
            BLOCKS.registerBlock("krifto_egg",
                    props -> new SmallEggsBlock(SMOPEntities.KRIFTOGNATHUS, 300, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG));

    public static final DeferredBlock<EggBlock> NIRAS_EGG =
            BLOCKS.registerBlock("niras_egg",
                    props -> new EggBlock(SMOPEntities.NIRASMOSAURUS, 600, 8, 10, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG).noOcclusion());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private SMOPBlocks() {}
}
