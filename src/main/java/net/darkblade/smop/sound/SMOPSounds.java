package net.darkblade.smop.sound;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SMOPSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SMOP.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_ROAR =
            SOUNDS.register("gt_roar", () -> SoundEvent.createFixedRangeEvent(SMOP.id("gt_roar"), 64.0F));

    public static final DeferredHolder<SoundEvent, SoundEvent> KRIFTO_SQUAWK =
            SOUNDS.register("krifto_squawk", () -> SoundEvent.createVariableRangeEvent(SMOP.id("krifto_squawk")));

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    private SMOPSounds() {}
}
