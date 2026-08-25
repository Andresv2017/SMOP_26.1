package net.darkblade.smop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPSpawnDebug {

    private static final Logger LOGGER = LoggerFactory.getLogger("SMOPSpawnDebug");

    private static final int MAGIC_NUMBER = 17 * 17;

    private static final int SPAWN_RADIUS_CHUNKS = 8;

    private static final int CREATURE_PERIOD = 400;

    private static final int DEFAULT_PASSES = 15;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("spawn")
                .then(Commands.literal("state")
                        .executes(ctx -> state(ctx.getSource())))
                .then(Commands.literal("sim")
                        .executes(ctx -> simulate(ctx.getSource(), DEFAULT_PASSES))
                        .then(Commands.argument("passes", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> simulate(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "passes")))))
                .then(Commands.literal("watch")
                        .executes(ctx -> watch(ctx.getSource(), 60))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(ctx -> watch(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    // ───────────────────────────────────────────────────── STATE ─────

    private static int state(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        List<String> out = new ArrayList<>();

        out.add("=== spawn state @ " + at.toShortString() + " ===");

        Holder<Biome> biome = level.getBiome(at);
        out.add("biome: " + biome.getRegisteredName());

        long gameTime = level.getGameTime();
        long untilCreature = (CREATURE_PERIOD - gameTime % CREATURE_PERIOD) % CREATURE_PERIOD;
        out.add(String.format("gameTime %d — CREATURE cycle in %d ticks (%.1fs); "
                        + "non-persistent categories run EVERY tick",
                gameTime, untilCreature, untilCreature / 20.0F));

        NaturalSpawner.SpawnState spawnState = level.getChunkSource().getLastSpawnState();
        if (spawnState == null) {
            out.add("no SpawnState yet — has the server ticked with a player loaded?");
        } else {
            int chunks = spawnState.getSpawnableChunkCount();
            out.add("spawnableChunkCount " + chunks + "  (cap = max * chunks / " + MAGIC_NUMBER + ")");
            for (MobCategory category : new MobCategory[]{MobCategory.CREATURE, MobCategory.WATER_CREATURE,
                    MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE}) {
                int count = spawnState.getMobCategoryCounts().getInt(category);
                int cap = category.getMaxInstancesPerChunk() * chunks / MAGIC_NUMBER;
                out.add(String.format("  %-28s %3d / %3d %s", category.getName(), count, cap,
                        count < cap ? "" : "  <<< FULL, whole category refused"));
            }
        }

        // The pool as the spawner sees it here, biome modifiers already folded in.
        for (MobCategory category : new MobCategory[]{MobCategory.CREATURE, MobCategory.WATER_CREATURE,
                MobCategory.WATER_AMBIENT}) {
            WeightedList<MobSpawnSettings.SpawnerData> pool = poolAt(level, biome, category, at);
            if (pool.isEmpty()) {
                out.add(category.getName() + " pool: (empty)");
                continue;
            }
            int total = pool.unwrap().stream().mapToInt(Weighted::weight).sum();
            out.add(category.getName() + " pool (total weight " + total + "):");
            for (Weighted<MobSpawnSettings.SpawnerData> entry : pool.unwrap()) {
                MobSpawnSettings.SpawnerData data = entry.value();
                out.add(String.format("  %-34s w%-4d %d-%d   %.0f%%",
                        BuiltInRegistries.ENTITY_TYPE.getKey(data.type()), entry.weight(),
                        data.minCount(), data.maxCount(), 100.0 * entry.weight() / total));
            }
        }

        out.add(census(level));
        out.add(waterColumn(level, at));
        emit(source, out);
        return 1;
    }

    private static String census(ServerLevel level) {
        Object2IntLinkedOpenHashMap<String> counted = new Object2IntLinkedOpenHashMap<>();
        Object2IntLinkedOpenHashMap<String> free = new Object2IntLinkedOpenHashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            String key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            if (!key.startsWith(SMOP.MOD_ID + ":")) {
                continue;
            }
            if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
                free.addTo(key, 1);
            } else {
                counted.addTo(key, 1);
            }
        }
        if (counted.isEmpty() && free.isEmpty()) {
            return "SMOP mobs loaded: none";
        }
        StringBuilder sb = new StringBuilder("SMOP mobs loaded (counted against cap / exempt):");
        for (String key : counted.keySet()) {
            sb.append(' ').append(key).append(' ').append(counted.getInt(key))
                    .append('/').append(free.getInt(key));
        }
        for (String key : free.keySet()) {
            if (!counted.containsKey(key)) {
                sb.append(' ').append(key).append(" 0/").append(free.getInt(key));
            }
        }
        return sb.toString();
    }

    private static String waterColumn(ServerLevel level, BlockPos at) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) + 1;
        int floor = level.getMinY();
        int waterLo = Integer.MAX_VALUE;
        int waterHi = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = floor; y <= surface; y++) {
            pos.set(at.getX(), y, at.getZ());
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                waterLo = Math.min(waterLo, y);
                waterHi = Math.max(waterHi, y);
            }
        }
        int span = surface - floor + 1;
        if (waterHi < waterLo) {
            return String.format("column: no water; Y is rolled uniformly over %d blocks [%d..%d]",
                    span, floor, surface);
        }
        int water = waterHi - waterLo + 1;
        return String.format("column: water Y %d..%d (%d blocks) of %d rolled [%d..%d] -> %.1f%% of "
                        + "Y rolls can land in water",
                waterLo, waterHi, water, span, floor, surface, 100.0 * water / span);
    }

    // ───────────────────────────────────────────────────── SIM ─────

    private enum Gate {
        CAP_GLOBAL("category cap full (global)"),
        CAP_LOCAL("category cap full (local, approx)"),
        CHUNK_UNLOADED("chunk not loaded"),
        Y_IN_SOLID("Y roll landed in a solid block (early return)"),
        NO_PLAYER("no player found for position"),
        TOO_CLOSE("within 24 blocks of a player"),
        EMPTY_POOL("biome pool empty at this position"),
        OTHER_MOB("weighted roll picked a different mob"),
        FAR_FROM_PLAYER("beyond category despawn distance"),
        PLACEMENT("placement type refused the block"),
        RULES("checkSpawnRules refused"),
        COLLISION("noCollision refused (spawn AABB does not fit)"),
        CREATE_FAILED("EntityType.create returned null"),
        POSITION_CHECK("checkSpawnRules/checkSpawnObstruction refused"),
        SPAWNED("SPAWNED");

        private final String label;

        Gate(String label) {
            this.label = label;
        }
    }

    private static int simulate(CommandSourceStack source, int passes) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        EntityType<?> watched = SMOPEntities.NIRASMOSAURUS.get();
        MobCategory category = watched.getCategory();
        RandomSource random = level.getRandom();

        Object2IntLinkedOpenHashMap<Gate> tally = new Object2IntLinkedOpenHashMap<>();
        for (Gate gate : Gate.values()) {
            tally.put(gate, 0);
        }

        NaturalSpawner.SpawnState spawnState = level.getChunkSource().getLastSpawnState();
        int chunkCount = spawnState == null ? MAGIC_NUMBER : spawnState.getSpawnableChunkCount();
        int globalCount = spawnState == null ? 0 : spawnState.getMobCategoryCounts().getInt(category);
        int globalCap = category.getMaxInstancesPerChunk() * chunkCount / MAGIC_NUMBER;
        boolean globalOk = globalCount < globalCap;
        int localCount = approxLocalCount(level, origin, category);
        boolean localOk = localCount < category.getMaxInstancesPerChunk();

        ChunkPos center = ChunkPos.containing(origin);
        List<Integer> spawnYs = new ArrayList<>();
        Object2IntLinkedOpenHashMap<String> biomeTally = new Object2IntLinkedOpenHashMap<>();
        int yInWater = 0;
        int yInAir = 0;

        for (int pass = 0; pass < passes; pass++) {
            for (int dx = -SPAWN_RADIUS_CHUNKS; dx <= SPAWN_RADIUS_CHUNKS; dx++) {
                for (int dz = -SPAWN_RADIUS_CHUNKS; dz <= SPAWN_RADIUS_CHUNKS; dz++) {
                    if (!globalOk) {
                        tally.addTo(Gate.CAP_GLOBAL, 1);
                        continue;
                    }
                    if (!localOk) {
                        tally.addTo(Gate.CAP_LOCAL, 1);
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
                    if (chunk == null) {
                        tally.addTo(Gate.CHUNK_UNLOADED, 1);
                        continue;
                    }

                    // getRandomPosWithin, verbatim.
                    ChunkPos chunkPos = chunk.getPos();
                    int x = chunkPos.getMinBlockX() + random.nextInt(16);
                    int z = chunkPos.getMinBlockZ() + random.nextInt(16);
                    int topEmptyY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                    int y = Mth.randomBetweenInclusive(random, level.getMinY(), topEmptyY);
                    if (y < level.getMinY() + 1) {
                        tally.addTo(Gate.Y_IN_SOLID, 1);
                        continue;
                    }

                    BlockPos start = new BlockPos(x, y, z);
                    BlockState startState = chunk.getBlockState(start);
                    if (startState.isRedstoneConductor(chunk, start)) {
                        tally.addTo(Gate.Y_IN_SOLID, 1);
                        continue;
                    }
                    if (level.getFluidState(start).is(FluidTags.WATER)) {
                        yInWater++;
                    } else if (startState.isAir()) {
                        yInAir++;
                    }

                    biomeTally.addTo(level.getBiome(start).getRegisteredName(), 1);
                    Gate result = attemptAt(level, chunk, start, category, watched, random);
                    tally.addTo(result, 1);
                    if (result == Gate.SPAWNED) {
                        spawnYs.add(y);
                    }
                }
            }
        }

        List<String> out = new ArrayList<>();
        out.add("=== spawn sim: " + BuiltInRegistries.ENTITY_TYPE.getKey(watched) + " ("
                + category.getName() + ") ===");
        // A pass is one sweep of every spawnable chunk, which is one cycle for this category. How much
        // REAL time that is depends entirely on MobCategory#isPersistent: the persistent categories are
        // gated behind ServerChunkCache's gameTime % 400 test, the rest run on every single tick. Four
        // hundred times apart, so the conversion has to be read off the category rather than assumed —
        // this line said "CREATURE cycles" while the Nirasmosaurus was still CREATURE, and would have
        // over-reported the simulated span by 400x the moment it moved.
        int periodTicks = category.isPersistent() ? CREATURE_PERIOD : 1;
        out.add(String.format("%d passes x %d^2 chunks  ~= %.1fs of real %s cycles (%s, %d ticks apart)",
                passes, 2 * SPAWN_RADIUS_CHUNKS + 1, passes * periodTicks / 20.0F, category.getName(),
                category.isPersistent() ? "persistent" : "every tick", periodTicks));
        out.add(String.format("global cap %d/%d %s   local (approx) %d/%d %s",
                globalCount, globalCap, globalOk ? "ok" : "FULL",
                localCount, category.getMaxInstancesPerChunk(), localOk ? "ok" : "FULL"));
        out.add(String.format("Y roll landed: %d in water, %d in air, rest in solid", yInWater, yInAir));

        int total = tally.values().intStream().sum();
        out.add("--- where each of the " + total + " attempts died ---");
        for (Gate gate : Gate.values()) {
            int n = tally.getInt(gate);
            if (n == 0) {
                continue;
            }
            out.add(String.format("  %-50s %6d  %5.2f%%", gate.label, n, 100.0 * n / total));
        }
        if (!spawnYs.isEmpty()) {
            out.add("spawned at Y: " + spawnYs);
        }
        // Which biomes the surviving attempts actually landed in. Without this the histogram is
        // ambiguous when testing a thin biome: "no beach spawns" reads the same whether the beach is
        // refusing them or whether the sample never reached a beach chunk in the first place.
        if (!biomeTally.isEmpty()) {
            out.add("--- biome of the " + biomeTally.values().intStream().sum()
                    + " attempts that got past the caps and the Y roll ---");
            biomeTally.keySet().stream()
                    .sorted(Comparator.comparingInt(biomeTally::getInt).reversed())
                    .limit(6)
                    .forEach(key -> out.add(String.format("  %-40s %6d", key, biomeTally.getInt(key))));
        }
        out.add(waterColumn(level, origin));

        emit(source, out);
        return 1;
    }

    private static Gate attemptAt(ServerLevel level, LevelChunk chunk, BlockPos start,
                                  MobCategory category, EntityType<?> watched, RandomSource random) {
        double xx = start.getX() + 0.5;
        double zz = start.getZ() + 0.5;
        int y = start.getY();

        Player nearest = level.getNearestPlayer(xx, y, zz, -1.0, false);
        if (nearest == null) {
            return Gate.NO_PLAYER;
        }
        double playerDistSqr = nearest.distanceToSqr(xx, y, zz);
        if (playerDistSqr <= 576.0) {
            return Gate.TOO_CLOSE;
        }

        Holder<Biome> biome = level.getBiome(start);
        WeightedList<MobSpawnSettings.SpawnerData> pool = poolAt(level, biome, category, start);
        if (pool.isEmpty()) {
            return Gate.EMPTY_POOL;
        }
        Optional<MobSpawnSettings.SpawnerData> rolled = pool.getRandom(random);
        if (rolled.isEmpty()) {
            return Gate.EMPTY_POOL;
        }
        EntityType<?> type = rolled.get().type();
        if (type != watched) {
            return Gate.OTHER_MOB;
        }

        // isValidSpawnPostitionForType, in order.
        if (!type.canSpawnFarFromPlayer()
                && playerDistSqr > (double) category.getDespawnDistance() * category.getDespawnDistance()) {
            return Gate.FAR_FROM_PLAYER;
        }
        if (!SpawnPlacements.isSpawnPositionOk(type, level, start)) {
            return Gate.PLACEMENT;
        }
        if (!SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, start, random)) {
            return Gate.RULES;
        }
        if (!level.noCollision(type.getSpawnAABB(xx, y, zz))) {
            return Gate.COLLISION;
        }

        // isValidPositionForMob — needs a real instance, which is never added to the world.
        Entity created = type.create(level, EntitySpawnReason.NATURAL);
        if (!(created instanceof Mob mob)) {
            return Gate.CREATE_FAILED;
        }
        mob.snapTo(xx, y, zz, random.nextFloat() * 360.0F, 0.0F);
        boolean ok = mob.checkSpawnRules(level, EntitySpawnReason.NATURAL) && mob.checkSpawnObstruction(level);
        mob.discard();
        return ok ? Gate.SPAWNED : Gate.POSITION_CHECK;
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> poolAt(ServerLevel level, Holder<Biome> biome,
                                                                    MobCategory category, BlockPos pos) {
        return level.getChunkSource().getGenerator()
                .getMobsAt(biome, level.structureManager(), category, pos);
    }

    private static int approxLocalCount(ServerLevel level, BlockPos origin, MobCategory category) {
        int radius = SPAWN_RADIUS_CHUNKS * 16;
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
                continue;
            }
            if (mob.getType().getCategory() != category) {
                continue;
            }
            if (Math.abs(mob.getBlockX() - origin.getX()) <= radius
                    && Math.abs(mob.getBlockZ() - origin.getZ()) <= radius) {
                count++;
            }
        }
        return count;
    }

    // ───────────────────────────────────────────────────── WATCH ─────

    private static int watchTicksLeft;
    private static int placementChecked;
    private static int placementPassed;
    private static int positionChecked;
    private static int positionPassed;
    private static int finalized;
    private static int chunkGenFinalized;

    private static int watch(CommandSourceStack source, int seconds) {
        watchTicksLeft = seconds * 20;
        placementChecked = 0;
        placementPassed = 0;
        positionChecked = 0;
        positionPassed = 0;
        finalized = 0;
        chunkGenFinalized = 0;
        LOGGER.info("=== spawn watch armed for {}s ===", seconds);
        source.sendSuccess(() -> Component
                .literal("Watching the real spawn pipeline for " + seconds + "s. Roam, then read the log.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static boolean ours(EntityType<?> type) {
        return type == SMOPEntities.NIRASMOSAURUS.get() || type == SMOPEntities.SALMON.get();
    }

    @SubscribeEvent
    public static void onPlacementCheck(MobSpawnEvent.@NotNull SpawnPlacementCheck event) {
        if (watchTicksLeft <= 0 || !ours(event.getEntityType())) {
            return;
        }
        placementChecked++;
        if (event.getPlacementCheckResult()) {
            placementPassed++;
        }
        LOGGER.info("placementCheck {} at {} reason={} -> {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType()),
                event.getPos().toShortString(), event.getSpawnType(), event.getPlacementCheckResult());
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.@NotNull PositionCheck event) {
        if (watchTicksLeft <= 0 || !ours(event.getEntity().getType())) {
            return;
        }
        positionChecked++;
        Mob mob = event.getEntity();
        boolean dflt = mob.checkSpawnRules(event.getLevel(), event.getSpawnType())
                && mob.checkSpawnObstruction(event.getLevel());
        if (dflt) {
            positionPassed++;
        }
        LOGGER.info("positionCheck {} at {},{},{} reason={} -> {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
                (int) event.getX(), (int) event.getY(), (int) event.getZ(), event.getSpawnType(), dflt);
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(@NotNull FinalizeSpawnEvent event) {
        if (watchTicksLeft <= 0 || !ours(event.getEntity().getType())) {
            return;
        }
        finalized++;
        if (event.getSpawnType() == EntitySpawnReason.CHUNK_GENERATION) {
            chunkGenFinalized++;
        }
        LOGGER.info("SPAWNED {} at {},{},{} reason={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()),
                (int) event.getX(), (int) event.getY(), (int) event.getZ(), event.getSpawnType());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.@NotNull Post event) {
        if (watchTicksLeft <= 0) {
            return;
        }
        if (--watchTicksLeft == 0) {
            LOGGER.info("=== spawn watch over ===");
            LOGGER.info("  placementCheck reached {} times, passed {}", placementChecked, placementPassed);
            LOGGER.info("  positionCheck  reached {} times, passed {}", positionChecked, positionPassed);
            LOGGER.info("  finalized {} ({} from chunk generation, {} from the periodic cycle)",
                    finalized, chunkGenFinalized, finalized - chunkGenFinalized);
            if (placementChecked == 0) {
                LOGGER.info("  ZERO placement checks: every attempt died BEFORE checkSpawnRules —"
                        + " category cap, Y roll, player distance, biome pool or the placement type."
                        + " Run /smop debug spawn sim to see which.");
            }
        }
    }

    private static void emit(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            LOGGER.info(line);
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
    }

    private SMOPSpawnDebug() {}
}
