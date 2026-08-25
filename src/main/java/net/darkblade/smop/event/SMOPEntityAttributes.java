package net.darkblade.smop.event;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.SMOPSpawnPlacementTypes;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.world.entity.Mob;
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

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPEntityAttributes {

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SMOPEntities.TANGOFTERO.get(), TangofteroEntity.createAttributes().build());
        event.put(SMOPEntities.SALMON.get(), SalmonEntity.createAttributes().build());
        event.put(SMOPEntities.KRIFTOGNATHUS.get(), KriftognathusEntity.createAttributes().build());
        event.put(SMOPEntities.HELL_HIPPO.get(), HellHippoEntity.createAttributes().build());
        event.put(SMOPEntities.NIRASMOSAURUS.get(), NirasmosaurusEntity.createAttributes().build());
        event.put(SMOPEntities.GT.get(), GTEntity.createAttributes().build());
    }

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

        // A custom type rather than one of vanilla's, because this animal wants both: the ocean is
        // where the population lives and the beach is where it is worth seeing one out of the water.
        //
        // Plain IN_WATER was the wrong half. The placement type runs BEFORE the entity's own rule
        // (NaturalSpawner#isValidSpawnPostitionForType calls isSpawnPositionOk, then checkSpawnRules),
        // so IN_WATER rejected every dry position and the onShore branch of checkNirasSpawnRules was
        // dead code — which is why no Nirasmosaurus was ever seen in a beach biome despite the biome
        // being listed in SMOPSpawns. See SMOPSpawnPlacementTypes for what the type accepts and why it
        // leaves adjustSpawnPosition alone.
        event.register(SMOPEntities.NIRASMOSAURUS.get(),
                SMOPSpawnPlacementTypes.IN_WATER_OR_ON_SHORE,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NirasmosaurusEntity::checkNirasSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        // Mob::checkMobSpawnRules, no la de Animal ni la de Monster.
        //
        // La de Animal dejó de aplicar cuando el GT pasó a CortexMonster, que extiende PathfinderMob
        // y no Animal. La de Monster habría sido el reflejo fácil, pero exige oscuridad y eso cambia
        // lo que se decidió: llanura y desierto, sin condición de luz. checkMobSpawnRules es la
        // neutral — solo comprueba que el bloque de debajo admita spawn — y conserva esa intención.
        event.register(SMOPEntities.GT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private SMOPEntityAttributes() {}
}
