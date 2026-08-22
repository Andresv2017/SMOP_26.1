package net.darkblade.smop.entity;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.darkblade.smop.entity.niras.NirasmosaurusEntity;
import net.darkblade.smop.entity.projectile.NirasSpearEntity;
import net.darkblade.smop.entity.projectile.TangoArrowEntity;
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

    /**
     * 3.0 long and 1.6 tall: one AABB, not the multipart hitbox 1.20.1 gave it. See the port spec —
     * {@code PartEntity} support does not exist in DeluxeLib yet and lands with the Grand Tyrant,
     * which needs it far more than a mount does.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<NirasmosaurusEntity>> NIRASMOSAURUS =
            ENTITY_TYPES.register("nirasmosaurus",
                    // WATER_CREATURE. This has now been wrong twice in opposite directions, so the
                    // measurements that settled it are worth keeping.
                    //
                    // The legacy was CREATURE. A first move to WATER_CREATURE was reverted on the
                    // reasoning that its cap of 5 is shared with squid, dolphins and nautilus and is
                    // always saturated, while the CREATURE pool in ocean biomes is empty — creature: []
                    // in every ocean JSON — so any weight at all would win there. Both halves of that
                    // are true and the conclusion was still wrong, because an empty POOL is worthless
                    // when the CATEGORY never gets a turn.
                    //
                    // What the in-game instrumentation showed (see SMOPSpawnDebug): CREATURE is not
                    // merely full, it is permanently full by a factor of three to eight and it does not
                    // recover. Sampled across four different warm oceans in a fresh world, thousands of
                    // blocks apart, the count ran 27 to 79 against a cap of 10 and never once dropped
                    // below 27 — usually with no SMOP mob loaded at all. The simulator attributed 100%
                    // of 4335 attempts to that one gate; not a single attempt ever reached the Y roll.
                    //
                    // The cause is that NaturalSpawner#createState counts level.getAllEntities() — the
                    // WHOLE level, not the player's surroundings — so every cow, sheep, pig and chicken
                    // in any loaded chunk spends the same budget of ten. At view distance 16 that is
                    // 1089 chunks of world generation seeding farm animals. And they are Animals, so
                    // they never despawn. CREATURE is a land-animal budget that happens to be global.
                    //
                    // Which is exactly why Mojang leaves creature: [] in the oceans and files squid,
                    // dolphins and nautilus under WATER_CREATURE. That pool is not empty by oversight;
                    // the category is unusable at sea, and the empty pool is the consequence.
                    //
                    // WATER_CREATURE is saturated too — 5 to 8 against a cap of 5 in the same samples —
                    // but the two situations are not comparable. It overshoots by a sixth rather than
                    // several times over, it is consulted EVERY tick instead of one in four hundred
                    // (MobCategory.isPersistent gates that, and CREATURE is the persistent one), and its
                    // occupants despawn, so the cap turns over continuously instead of setting like
                    // concrete. Fish and squid visibly keep spawning in a "full" ocean; nothing CREATURE
                    // does.
                    //
                    // One consequence worth knowing rather than discovering: EntityType.Builder defaults
                    // canSpawnFarFromPlayer to (category == CREATURE || category == MISC), so leaving
                    // CREATURE also gives that up and spawns are confined to 128 blocks of a player.
                    // Squid and dolphins live under the same rule, and for an animal meant to be SEEN
                    // that is a gain, not a loss.
                    () -> EntityType.Builder.<NirasmosaurusEntity>of(NirasmosaurusEntity::new, MobCategory.WATER_CREATURE)
                            .sized(3.0F, 1.6F)
                            .clientTrackingRange(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("nirasmosaurus"))));

    /**
     * The Tangoftero's arrow.
     *
     * <p>Not a mob, so the numbers come from vanilla's own {@code EntityType.ARROW} rather than from
     * anything in this file: 0.5 cubed, eye height 0.13, a tracking range of 4 and — the one that
     * matters — {@code updateInterval(20)}. An arrow's flight is dead reckoning on the client, so it
     * needs a position packet twenty times less often than a walking animal; leaving the default of 3
     * would put three times the traffic on the wire for a projectile that lives two seconds.
     *
     * <p><b>{@code noLootTable()} is deliberately absent</b>, though vanilla's arrow carries it. It
     * cannot be used here: {@code DeluxeEntityLootSubProvider} hands an empty table to every entity in
     * the register, and {@code EntityLootSubProvider#add} resolves the default loot table key with
     * {@code orElseThrow}. Declaring this arrow table-less would abort {@code runDataServer} rather
     * than skip it. The cost of leaving it out is one three-line generated file that nothing reads,
     * because loot is only ever rolled for a {@code LivingEntity}. Fixing it properly means teaching
     * DeluxeLib's provider to skip table-less types, which belongs in DeluxeLib.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TangoArrowEntity>> TANGO_ARROW =
            ENTITY_TYPES.register("tango_arrow",
                    () -> EntityType.Builder.<TangoArrowEntity>of(TangoArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("tango_arrow"))));

    /**
     * The thrown Nirasmosaurus spear. Sized and tracked like vanilla's thrown trident rather than
     * like the arrow above — it is a heavier, slower thing and its 0.25 hitbox is the trident's.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<NirasSpearEntity>> NIRAS_SPEAR =
            ENTITY_TYPES.register("niras_spear",
                    () -> EntityType.Builder.<NirasSpearEntity>of(NirasSpearEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("niras_spear"))));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private SMOPEntities() {}
}
