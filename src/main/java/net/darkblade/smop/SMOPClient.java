package net.darkblade.smop;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Client-only entrypoint. Never loaded on a dedicated server, so client classes are safe to touch
 * from here.
 *
 * <p>Renderers, model layers, keybinds and client events get wired up here as each mob is ported.
 */
@Mod(value = SMOP.MOD_ID, dist = Dist.CLIENT)
public class SMOPClient {

    public SMOPClient(IEventBus modEventBus, ModContainer modContainer) {
        // Client wiring goes here, phase by phase (entity renderers, layer definitions, keybinds).
    }
}
