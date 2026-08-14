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

        // Savannas and swamps, one at a time. 1.20.1 shipped savanna only, at weight 15 in packs of
        // 1-3; neither number survives — a two-and-a-half-block animal arriving in threes reads as a
        // wall of hippo, and one is what this mob is for.
        //
        // Weight 4, anchored against what it is competing with rather than carried over. The savanna
        // already holds 52 points of CREATURE weight (sheep 12, pig 10, chicken 10, armadillo 10,
        // cow 8, horse 1, donkey 1) and the swamp 50 (the same farm animals plus frog 10). The
        // legacy's 15 would take 22% of every roll — making this the single most likely animal in
        // the biome, ahead of sheep, which is how it read in play. 4 puts it near the horse: the
        // large, biome-flavoured animal you come across now and then, not the one you wade through.
        //
        // Worth remembering when tuning: a female rolls a companion calf half the time
        // (CALF_COMPANION_CHANCE), so the head count runs about 1.25x whatever this weight suggests.
        //
        // An explicit biome list rather than BiomeTags.IS_SAVANNA, because the builder's biomeTag()
        // and biomes() clear each other and the swamps carry no tag the savannas share. Both swamps
        // are in: the mangrove is where an amphibious animal this size belongs most, even if its mud
        // floor makes it rarer there — see HellHippoEntity#checkHellHippoSpawnRules.
        DeluxeBiomeSpawns.builder(SMOPEntities.HELL_HIPPO::get, MobCategory.CREATURE)
                .spawnRate(4, 1, 1)
                .biomes(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
                        Biomes.SWAMP, Biomes.MANGROVE_SWAMP)
                .submit();
    }

    private SMOPSpawns() {}
}
