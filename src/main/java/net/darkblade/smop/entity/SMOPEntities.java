package net.darkblade.smop.entity;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.darkblade.smop.entity.salmon.SalmonEntity;
import net.darkblade.smop.entity.tangoftero.TangofteroEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity type registry. */
public final class SMOPEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SMOP.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TangofteroEntity>> TANGOFTERO =
            ENTITY_TYPES.register("tangoftero",
                    () -> EntityType.Builder.<TangofteroEntity>of(TangofteroEntity::new, MobCategory.CREATURE)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(8)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("tangoftero"))));

    public static final DeferredHolder<EntityType<?>, EntityType<SalmonEntity>> SALMON =
            ENTITY_TYPES.register("salmon",
                    () -> EntityType.Builder.<SalmonEntity>of(SalmonEntity::new, MobCategory.WATER_AMBIENT)
                            .sized(1.5F, 1.0F)
                            .clientTrackingRange(8)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("salmon"))));

    public static final DeferredHolder<EntityType<?>, EntityType<KriftognathusEntity>> KRIFTOGNATHUS =
            ENTITY_TYPES.register("kriftognathus",
                    () -> EntityType.Builder.<KriftognathusEntity>of(KriftognathusEntity::new, MobCategory.CREATURE)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("kriftognathus"))));

    public static final DeferredHolder<EntityType<?>, EntityType<HellHippoEntity>> HELL_HIPPO =
            ENTITY_TYPES.register("hell_hippo",
                    () -> EntityType.Builder.<HellHippoEntity>of(HellHippoEntity::new, MobCategory.CREATURE)
                            .sized(2.5F, 2.5F)
                            .clientTrackingRange(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("hell_hippo"))));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private SMOPEntities() {}
}
