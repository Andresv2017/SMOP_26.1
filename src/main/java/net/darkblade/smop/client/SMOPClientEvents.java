package net.darkblade.smop.client;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.client.krifto.KriftoBabyModel;
import net.darkblade.smop.client.krifto.KriftognathusModel;
import net.darkblade.smop.client.krifto.KriftognathusRenderer;
import net.darkblade.smop.client.salmon.SalmonModel;
import net.darkblade.smop.client.salmon.SalmonRenderer;
import net.darkblade.smop.client.tangoftero.TangoBabyModel;
import net.darkblade.smop.client.tangoftero.TangofteroModel;
import net.darkblade.smop.client.tangoftero.TangofteroRenderer;
import net.darkblade.smop.entity.SMOPEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layer definitions and entity renderers. Mod-bus, client only. */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class SMOPClientEvents {

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TangofteroModel.LAYER_LOCATION, TangofteroModel::createBodyLayer);
        event.registerLayerDefinition(TangoBabyModel.LAYER_LOCATION, TangoBabyModel::createBodyLayer);
        event.registerLayerDefinition(SalmonModel.LAYER_LOCATION, SalmonModel::createBodyLayer);
        event.registerLayerDefinition(KriftognathusModel.LAYER_LOCATION, KriftognathusModel::createBodyLayer);
        event.registerLayerDefinition(KriftoBabyModel.LAYER_LOCATION, KriftoBabyModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SMOPEntities.TANGOFTERO.get(), TangofteroRenderer::new);
        event.registerEntityRenderer(SMOPEntities.SALMON.get(), SalmonRenderer::new);
        event.registerEntityRenderer(SMOPEntities.KRIFTOGNATHUS.get(), KriftognathusRenderer::new);
    }

    private SMOPClientEvents() {}
}
