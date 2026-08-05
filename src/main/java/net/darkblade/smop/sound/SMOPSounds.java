package net.darkblade.smop.sound;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound event registry. Definitions live in {@code assets/smop/sounds.json}. */
public final class SMOPSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SMOP.MOD_ID);

    /** The Grand Tyrant's roar. Fixed 64-block range so it carries across the whole encounter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_ROAR =
            SOUNDS.register("gt_roar", () -> SoundEvent.createFixedRangeEvent(SMOP.id("gt_roar"), 64.0F));

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    private SMOPSounds() {}
}
