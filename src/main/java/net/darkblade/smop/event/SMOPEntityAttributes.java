package net.darkblade.smop.event;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.darkblade.smop.entity.niras.NirasmosaurusEntity;
import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.darkblade.smop.entity.salmon.SalmonEntity;
import net.darkblade.smop.entity.tangoftero.TangofteroEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Attributes and spawn placement rules for SMOP's mobs. Both are mod-bus events. */
@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPEntityAttributes {

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SMOPEntities.TANGOFTERO.get(), TangofteroEntity.createAttributes().build());
        event.put(SMOPEntities.SALMON.get(), SalmonEntity.createAttributes().build());
        event.put(SMOPEntities.KRIFTOGNATHUS.get(), KriftognathusEntity.createAttributes().build());
        event.put(SMOPEntities.HELL_HIPPO.get(), HellHippoEntity.createAttributes().build());
        event.put(SMOPEntities.NIRASMOSAURUS.get(), NirasmosaurusEntity.createAttributes().build());
    }

    /**
     * 26.1 registers spawn placements through an event rather than the static
     * {@code SpawnPlacements.register} call 1.20.1 made from {@code commonSetup}.
     */
    @SubscribeEvent
    static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(SMOPEntities.TANGOFTERO.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                TangofteroEntity::checkTangofteroSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(SMOPEntities.KRIFTOGNATHUS.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                KriftognathusEntity::checkKriftoSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(SMOPEntities.SALMON.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SalmonEntity::checkSalmonSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        // ON_GROUND and not IN_WATER, despite the animal being amphibious: this decides where it is
        // first placed, not where it may go. A hippo walks into the water on its own once it exists.
        event.register(SMOPEntities.HELL_HIPPO.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HellHippoEntity::checkHellHippoSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        // IN_WATER, unlike the hippo above: this one is a water animal that hauls out, so the water
        // is where it should first appear. Its own rule still accepts a shoreline block, which is
        // what lets one show up basking instead of always submerged.
        event.register(SMOPEntities.NIRASMOSAURUS.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NirasmosaurusEntity::checkNirasSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private SMOPEntityAttributes() {}
}
