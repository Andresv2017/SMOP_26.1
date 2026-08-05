package net.darkblade.smop.entity;

import net.darkblade.deluxelib.spawn.DeluxeBiomeSpawns;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;

/**
 * Which biomes each mob is added to, declared through DeluxeLib's {@link DeluxeBiomeSpawns}.
 *
 * <p>Registering a spawn placement (see {@code SMOPEntityAttributes}) only says <em>where within a
 * biome</em> a mob may appear — it does not put the mob into any biome's spawner list. That second
 * half is a {@code neoforge:add_spawns} biome modifier, which 1.20.1 shipped as a hand-written
 * {@code data/smop/forge/biome_modifier/add_tangoftero.json}. Entries submitted here are held in
 * memory and written out as that datapack file by {@code DeluxeBiomeSpawnProvider} during server
 * datagen, so the file cannot drift away from the registry.
 *
 * <p>Called from {@code SMOP}'s constructor: the builder demands to run during mod initialisation,
 * and the entity type is captured as a supplier so the {@code DeferredHolder} is only resolved once
 * the registry is populated.
 */
public final class SMOPSpawns {

    /** Weight, pack size and biome carried over unchanged from the 1.20.1 biome modifier. */
    public static void register() {
        DeluxeBiomeSpawns.builder(SMOPEntities.TANGOFTERO::get, MobCategory.CREATURE)
                .spawnRate(10, 2, 4)
                .biomes(Biomes.PLAINS)
                .submit();

        // The Kriftognathus wears the colours of the biome it hatched in, so it is seeded across every
        // family it actually has a coat for — otherwise the jungle, arid and frosty variants would
        // only ever be seen by hatching eggs somewhere on purpose.
        //
        // One entry, not three: DeluxeBiomeSpawnProvider names its output file after the ENTITY, so a
        // second entry for the same mob would overwrite the first rather than add to it. Hence an
        // explicit biome list instead of one tag per family.
        DeluxeBiomeSpawns.builder(SMOPEntities.KRIFTOGNATHUS::get, MobCategory.CREATURE)
                .spawnRate(8, 1, 3)
                .biomes(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
                        Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS,
                        Biomes.SNOWY_TAIGA, Biomes.GROVE)
                .submit();

        // Rivers, by tag rather than by name so every river variant is covered at once. The salmon
        // is WATER_AMBIENT, a category with its own (small) spawn cap, so a generous weight here
        // still does not crowd the water out.
        DeluxeBiomeSpawns.builder(SMOPEntities.SALMON::get, MobCategory.WATER_AMBIENT)
                .spawnRate(12, 2, 5)
                .biomeTag(BiomeTags.IS_RIVER)
                .submit();
    }

    private SMOPSpawns() {}
}
