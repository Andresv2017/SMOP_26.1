package net.darkblade.smop.client;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.client.hellhippo.HellHippoBabyModel;
import net.darkblade.smop.client.hellhippo.HellHippoModel;
import net.darkblade.smop.client.hellhippo.HellHippoRenderer;
import net.darkblade.smop.client.gt.GTModel;
import net.darkblade.smop.client.gt.GTRenderer;
import net.darkblade.smop.client.niras.NirasBabyModel;
import net.darkblade.smop.client.niras.NirasRenderer;
import net.darkblade.smop.client.niras.NirasmosaurusModel;
import net.darkblade.smop.client.projectile.NirasSpearModel;
import net.darkblade.smop.client.projectile.NirasSpearRenderer;
import net.darkblade.smop.client.projectile.NirasSpearSpecialRenderer;
import net.darkblade.smop.client.projectile.TangoArrowRenderer;
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
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;

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
        event.registerLayerDefinition(HellHippoModel.LAYER_LOCATION, HellHippoModel::createBodyLayer);
        event.registerLayerDefinition(HellHippoBabyModel.LAYER_LOCATION, HellHippoBabyModel::createBodyLayer);
        event.registerLayerDefinition(NirasmosaurusModel.LAYER_LOCATION, NirasmosaurusModel::createBodyLayer);
        event.registerLayerDefinition(NirasBabyModel.LAYER_LOCATION, NirasBabyModel::createBodyLayer);
        event.registerLayerDefinition(NirasSpearModel.LAYER_LOCATION, NirasSpearModel::createLayer);
        event.registerLayerDefinition(GTModel.LAYER_LOCATION, GTModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SMOPEntities.TANGOFTERO.get(), TangofteroRenderer::new);
        event.registerEntityRenderer(SMOPEntities.SALMON.get(), SalmonRenderer::new);
        event.registerEntityRenderer(SMOPEntities.KRIFTOGNATHUS.get(), KriftognathusRenderer::new);
        event.registerEntityRenderer(SMOPEntities.HELL_HIPPO.get(), HellHippoRenderer::new);
        event.registerEntityRenderer(SMOPEntities.NIRASMOSAURUS.get(), NirasRenderer::new);
        event.registerEntityRenderer(SMOPEntities.TANGO_ARROW.get(), TangoArrowRenderer::new);
        event.registerEntityRenderer(SMOPEntities.NIRAS_SPEAR.get(), NirasSpearRenderer::new);
        event.registerEntityRenderer(SMOPEntities.GT.get(), GTRenderer::new);
    }

    /**
     * Binds {@code smop:niras_spear} as an item-model type, so the spear's item definition can name it
     * and get the real mesh instead of a flat sprite — see {@link NirasSpearSpecialRenderer}.
     */
    @SubscribeEvent
    static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(SMOP.id("niras_spear"), NirasSpearSpecialRenderer.Unbaked.MAP_CODEC);
    }

    private SMOPClientEvents() {}
}
