package net.darkblade.smop;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = SMOP.MOD_ID, dist = Dist.CLIENT)
public class SMOPClient {

    public SMOPClient(IEventBus modEventBus, ModContainer modContainer) {
        // Client wiring goes here, phase by phase (entity renderers, layer definitions, keybinds).
    }
}
