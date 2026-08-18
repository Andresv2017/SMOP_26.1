package net.darkblade.smop.block;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry.
 *
 * <p>Uses the specialised {@link DeferredRegister.Blocks} rather than a generic
 * {@code DeferredRegister<Block>}: 26.1 requires every {@code BlockBehaviour.Properties} to carry
 * the block's own registry id, and the specialised register threads that through for you.
 */
public final class SMOPBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SMOP.MOD_ID);

    /** Clutch of 1–4 eggs, 300 ticks per hatch stage (three stages, so ~45 s in total). */
    public static final DeferredBlock<SmallEggsBlock> TANGOFTERO_EGG =
            BLOCKS.registerBlock("tangoftero_egg",
                    props -> new SmallEggsBlock(SMOPEntities.TANGOFTERO, 300, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG));

    /**
     * Salmon roe: 3–6 fry after 10–40 s. Copies {@code FROGSPAWN} rather than {@code TURTLE_EGG} —
     * it needs to be non-solid and translucent so the water reads through it, which the egg
     * properties are not.
     */
    public static final DeferredBlock<RoeEggsBlock> SALMON_ROE_EGGS =
            BLOCKS.registerBlock("salmon_roe_eggs",
                    props -> new RoeEggsBlock(SMOPEntities.SALMON, 3, 6, 200, 800,
                            SoundEvents.TURTLE_EGG_HATCH, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.FROGSPAWN).noOcclusion());

    /** Clutch of 1–4 eggs, same three-stage incubation as the Tangoftero's. */
    public static final DeferredBlock<SmallEggsBlock> KRIFTO_EGG =
            BLOCKS.registerBlock("krifto_egg",
                    props -> new SmallEggsBlock(SMOPEntities.KRIFTOGNATHUS, 300, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG));

    /**
     * One large egg, 8×10 px, hatching a single Nirasmosaurus after ~90 s.
     *
     * <p>{@link EggBlock} rather than {@link SmallEggsBlock}: the blockstate authored for this one
     * carries {@code hatch} and no {@code eggs}, because a nest is a single egg rather than a
     * clutch. The dimensions are the model's own — {@code niras_egg_template.json} runs from
     * {@code [4,0,4]} to {@code [12,10,12]} — so the collision shape and the thing you see are one
     * number.
     *
     * <p>600 ticks a stage against the 300 of the two land layers. Not a figure inherited from
     * 1.20.1, which left none legible: it is the biggest and rarest of the egg layers and its nest
     * holds one, so a short wait would make it cheap.
     *
     * <p>{@code noOcclusion} because it is normally under water and the sea has to read through it;
     * the waterlogging that keeps it from punching an air pocket into the sea bed lives in
     * {@link EggBlock} itself.
     */
    public static final DeferredBlock<EggBlock> NIRAS_EGG =
            BLOCKS.registerBlock("niras_egg",
                    props -> new EggBlock(SMOPEntities.NIRASMOSAURUS, 600, 8, 10, props),
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG).noOcclusion());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private SMOPBlocks() {}
}
