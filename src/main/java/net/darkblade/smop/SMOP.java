package net.darkblade.smop;

import com.mojang.logging.LogUtils;
import net.darkblade.smop.block.SMOPBlockEntities;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.datagen.SMOPDatagen;
import net.darkblade.smop.effect.SMOPEffects;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.SMOPSpawns;
import net.darkblade.smop.item.SMOPItems;
import net.darkblade.smop.network.SMOPNetwork;
import net.darkblade.smop.sound.SMOPSounds;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SMOP.MOD_ID)
public class SMOP {

    public static final String MOD_ID = "smop";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SMOP(IEventBus modEventBus, ModContainer modContainer) {
        SMOPBlocks.register(modEventBus);
        SMOPBlockEntities.register(modEventBus);
        SMOPItems.register(modEventBus);
        SMOPEntities.register(modEventBus);
        SMOPEffects.register(modEventBus);
        SMOPSounds.register(modEventBus);

        SMOPNetwork.register(modEventBus);

        SMOPSpawns.register();

        modEventBus.addListener(SMOPDatagen::gatherClientData);
        modEventBus.addListener(SMOPDatagen::gatherServerData);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
