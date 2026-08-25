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
 * half is a {@code neoforge:add_spawns} biome modifier. Entries submitted here are held in memory
 * and written out as that datapack file by {@code DeluxeBiomeSpawnProvider} during server datagen, so
 * the file cannot drift away from the registry.
 *
 * <p>Called from {@code SMOP}'s constructor: the builder demands to run during mod initialisation,
 * and the entity type is captured as a supplier so the {@code DeferredHolder} is only resolved once
 * the registry is populated.
 */
public final class SMOPSpawns {

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

        // Savannas and swamps, ONE AT A TIME: a two-and-a-half-block animal arriving in threes reads
        // as a wall of hippo, and one is what this mob is for.
        //
        // Weight 4, anchored against what it is competing with. The savanna
        // already holds 52 points of CREATURE weight (sheep 12, pig 10, chicken 10, armadillo 10,
        // cow 8, horse 1, donkey 1) and the swamp 50 (the same farm animals plus frog 10). The
        // A weight of 15 would take 22% of every roll — the single most likely animal in the biome,
        // ahead of sheep, which is how it read in play. 4 puts it near the horse: the
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

        // Warm and temperate open water plus the beach it hauls out onto. Cold and deep oceans are out
        // on purpose: a basking reptile belongs in warm shallows.
        //
        // The beach entry was inert until the placement type was widened — see SMOPSpawnPlacementTypes.
        // Worth knowing what it buys: minecraft:beach is the strip of SAND, not the water beside it
        // (that is ocean biome), and vanilla's beach.json carries no water entries at all for exactly
        // that reason. So this entry only ever produces hauled-out animals, via the periodic cycle
        // landing its Y roll on the sand.
        //
        // WATER_CREATURE, not CREATURE — see SMOPEntities for the measurements, which are the whole
        // story. The short version is that CREATURE's cap is a global land-animal budget that is
        // permanently three to eight times over and never recovers, so the animal simply never spawned
        // at sea.
        //
        // Weight 8, up from 3, and the number is set against the pool it actually shares rather than
        // carried over. Warm ocean's WATER_CREATURE pool is squid 10, nautilus 5, dolphin 2; adding 8
        // makes 25 and takes just under a third of the rolls. Three would have taken an eighth, and an
        // eighth is too thin here for a reason the CREATURE pool never had: the squid entry is 4-4, so
        // one squid roll consumes four of the five slots in the category cap and the next opening is a
        // while coming. The weight has to buy its way past that, not just past the other animals.
        //
        // Pack 1-2, and the group is always a horizontal cluster at ONE depth:
        // spawnCategoryForPosition reads yStart once, outside both loops, and only jitters x and z by
        // plus-or-minus six per member. So pack size buys width, never depth — the depth spread comes
        // from separate spawn events, each of which draws its own Y. Two is also the real ceiling only
        // because NirasmosaurusEntity#getMaxSpawnClusterSize says so; the outer loop would otherwise
        // run this entry three times over for up to four animals.
        DeluxeBiomeSpawns.builder(SMOPEntities.NIRASMOSAURUS::get, MobCategory.WATER_CREATURE)
                .spawnRate(8, 1, 2)
                .biomes(Biomes.BEACH, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN)
                .submit();

        // Grand Tyrant: weight 5, alone, plains and desert. CREATURE by explicit decision.
        //
        // A warning rather than an argument: instrumentation written for the Nirasmosaurus measured
        // CREATURE not as full but as saturated by a factor of three to eight, and not recovering —
        // 27 to 79 against a cap of 10 in four places of a fresh world, with 100% of 4,335 attempts
        // dying at that gate. NaturalSpawner counts getAllEntities() for the whole level, so every
        // loaded cow spends the same budget. Expect this mob never to appear in survival.
        //
        // Check with /smop debug spawn while one is alive. If it is confirmed, MONSTER is a one-line
        // change in SMOPEntities. Do not change anything without that measurement.
        DeluxeBiomeSpawns.builder(SMOPEntities.GT::get, MobCategory.CREATURE)
                .spawnRate(5, 1, 1)
                .biomes(Biomes.PLAINS, Biomes.DESERT)
                .submit();
    }

    private SMOPSpawns() {}
}
