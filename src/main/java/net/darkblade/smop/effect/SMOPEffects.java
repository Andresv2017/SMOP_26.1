package net.darkblade.smop.effect;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mob effect registry. */
public final class SMOPEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SMOP.MOD_ID);

    public static final DeferredHolder<MobEffect, FearEffect> FEAR =
            EFFECTS.register("fear", () -> new FearEffect());

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    private SMOPEffects() {}
}
