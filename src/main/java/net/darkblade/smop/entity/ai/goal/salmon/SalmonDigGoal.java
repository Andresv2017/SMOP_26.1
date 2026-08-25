package net.darkblade.smop.entity.ai.goal.salmon;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.salmon.SalmonEntity;
import net.darkblade.smop.item.SMOPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends a salmon that has been shown a pufferfish to root through the river bed.
 *
 * <p>Picks a diggable block it can actually path to, swims there playing {@code sniff}, then plays
 * {@code dig} — and stops there. <b>The block is turned over by a frame event on the dig clip</b>
 * ({@code SalmonEntity#completeDig}), not by a counter in this goal — a counter running beside the
 * clip only stays in step while two numbers are kept written down twice.
 */
public class SalmonDigGoal extends Goal {

    /** How far out it will look for something to dig. */
    private static final int SEARCH_RANGE = 12;
    /**
     * How far <b>below</b> itself it looks, and it has to be this deep: a salmon cruises in midwater
     * and the river bed is routinely three or four blocks under it, so a shallow box never contains a
     * diggable block at all and the pufferfish is spent on a search that cannot succeed.
     */
    private static final int SEARCH_DOWN = 6;
    private static final int SEARCH_UP = 2;
    /** Cap on A* calls per scan: pathing is the expensive part, the block tests are not. */
    private static final int MAX_PATH_CHECKS = 8;
    /** Ticks between failed scans, so an impossible request does not re-run A* every tick. */
    private static final int RESCAN_COOLDOWN = 40;
    /** Failed scans before the request is dropped, rather than retrying for the rest of the fish's life. */
    private static final int MAX_FAILED_SCANS = 5;
    /** Give up on a block after this many failed approaches, so one unreachable spot is not retried forever. */
    private static final int MAX_RETRIES = 3;
    /** Hard ceiling on one dig trip, in case the target becomes unreachable mid-swim. */
    private static final int GIVE_UP_TICKS = 200;
    /**
     * Close enough to start digging, measured from the fish to the block's centre.
     *
     * <p>Generous on purpose: the navigator parks the fish in the water block <em>next to</em> the
     * target, so the centre-to-centre distance at the moment it arrives is already about 1.5, and
     * more when the approach is above or below. A tight radius here meant a fish that had arrived
     * and had nowhere left to swim still counted as "not there yet".
     */
    private static final double DIG_RANGE = 2.6D;
    /** Gap between re-issuing a move order, so a stalled path is retried but not spammed. */
    private static final int REPATH_COOLDOWN = 20;

    private final SalmonEntity salmon;
    private final double speed;

    /** Failed approaches per block, so a spot the navigator cannot reach drops out of the running. */
    private final Map<BlockPos, Integer> retries = new HashMap<>();

    @Nullable
    private BlockPos target;
    private int tryTicks;
    private int repathCooldown;
    private boolean digging;
    private int rescanCooldown;
    private int failedScans;

    /**
     * Traces every decision of the dig cycle to the server log under {@code [SALMON DIG]}. Off by
     * default; flip it on to find out which stage a request dies at — the scan lines alone tell you
     * whether there was no diggable material in range, material that was not exposed bed, or bed
     * that could not be pathed to.
     */
    public static final boolean DEBUG = false;

    public SalmonDigGoal(SalmonEntity salmon, double speed) {
        this.salmon = salmon;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private void debug(String message, Object... args) {
        if (DEBUG) {
            SMOP.LOGGER.info("[SALMON DIG] e{} " + message,
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of((Object) this.salmon.getId()),
                            java.util.Arrays.stream(args)).toArray());
        }
    }

    @Override
    public boolean canUse() {
        if (!this.salmon.wantsToDig() || this.salmon.isInSleepCycle()) {
            return false;
        }
        if (this.target != null) {
            return true;
        }
        // canUse runs every tick while the request stands, and a scan walks a few thousand block
        // states — throttle it, and eventually give the request up rather than searching forever
        // in a stretch of river that has no exposed bed at all.
        if (this.rescanCooldown > 0) {
            this.rescanCooldown--;
            return false;
        }
        this.target = this.findDigSite();
        if (this.target != null) {
            this.failedScans = 0;
            return true;
        }
        this.rescanCooldown = RESCAN_COOLDOWN;
        if (++this.failedScans >= MAX_FAILED_SCANS) {
            debug("giving up after {} failed scans — dig request dropped", MAX_FAILED_SCANS);
            this.failedScans = 0;
            this.retries.clear();
            this.salmon.setDigCommand(false);
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.tryTicks < GIVE_UP_TICKS && this.salmon.wantsToDig();
    }

    @Override
    public void start() {
        this.tryTicks = 0;
        this.repathCooldown = 0;
        this.digging = false;
        if (this.target == null) {
            return;
        }
        debug("START heading for {} (dist {})", this.target,
                String.format("%.1f", Math.sqrt(this.salmon.distanceToSqr(Vec3.atCenterOf(this.target)))));
        if (this.moveToTarget()) {
            this.salmon.animator().play(this.salmon.animator().getByName("sniff"));
        } else {
            this.abandonTarget();
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.digging = false;
        this.salmon.setDigTarget(null);
    }

    @Override
    public void tick() {
        this.tryTicks++;
        if (this.target == null) {
            return;
        }

        // Once the dig clip is running it owns the rest: its frame event breaks the block and clears
        // the dig command, which drops canContinueToUse and ends this goal.
        if (this.digging) {
            return;
        }

        // From the fish's real position to the block's centre. blockPosition() quantises both ends
        // to block corners, which threw the measurement off by up to a block and a half — enough,
        // for a target a level or two down, to sit permanently just outside any sane radius.
        double distance = Math.sqrt(this.salmon.distanceToSqr(Vec3.atCenterOf(this.target)));
        if (distance <= DIG_RANGE) {
            this.beginDig();
            return;
        }

        // One line every second while approaching, so a fish that is swimming but never arriving is
        // distinguishable from one that never set off.
        if (this.tryTicks % 20 == 0) {
            debug("approaching {} — dist {}, navInProgress={}", this.target,
                    String.format("%.1f", distance), this.salmon.getNavigation().isInProgress());
        }

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
        }
        if (this.salmon.getNavigation().isInProgress()) {
            return;
        }
        if (!this.moveToTarget()) {
            // No route left. If the block is close enough to work on anyway, dig from here — the
            // navigator refusing to plot a path to a spot the fish is already standing in is not a
            // reason to throw away a target it is sitting next to.
            if (distance <= DIG_RANGE * 1.5D) {
                debug("no path but within {} — digging from here", String.format("%.1f", distance));
                this.beginDig();
            } else {
                this.abandonTarget();
            }
        }
    }

    private void beginDig() {
        this.digging = true;
        this.salmon.getNavigation().stop();
        this.salmon.setDigTarget(this.target);
        this.salmon.animator().play(this.salmon.animator().getByName("dig"));
        debug("DIGGING {} ({}), clip started — block breaks on frame {}",
                this.target, this.salmon.level().getBlockState(this.target).getBlock().getName().getString(),
                35);
    }

    /**
     * Swims to the water block <b>on top of</b> the target, never into the target itself.
     *
     * <p>Aiming at the bed block's own centre cannot work: it is solid, so {@code SwimNodeEvaluator}
     * types it BLOCKED and A* never produces a node there. The navigator then hands back a partial
     * path that leads nowhere while {@code moveTo} still reports success — the path is non-null — so
     * the fish re-issues an order it cannot follow until the timeout. The open water above the bed is
     * both reachable and where the fish should hover to dig.
     */
    private boolean moveToTarget() {
        if (this.target == null) {
            return false;
        }
        this.repathCooldown = REPATH_COOLDOWN;
        BlockPos approach = approachFor(this.salmon.level(), this.target);
        if (approach == null) {
            debug("no water face left on {} — abandoning", this.target);
            return false;
        }
        boolean ok = this.salmon.getNavigation().moveTo(
                approach.getX() + 0.5D, approach.getY() + 0.5D, approach.getZ() + 0.5D, this.speed);
        debug("moveTo {} (approach for {}) -> {}", approach, this.target, ok ? "path accepted" : "NO PATH");
        return ok;
    }

    /**
     * The water block the fish parks in to work on {@code bed} — above it for a river bed, beside it
     * for a tank wall.
     *
     * <p>Never {@code bed} itself: that block is solid, so {@code SwimNodeEvaluator} types it
     * BLOCKED and A* cannot place a node there. Aiming at it returned a partial path that led
     * nowhere while {@code moveTo} still reported success, which is what left the fish re-issuing an
     * order it could not follow until the goal timed out.
     */
    @Nullable
    private static BlockPos approachFor(Level level, BlockPos bed) {
        if (isWater(level, bed.above())) {
            return bed.above();
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = bed.relative(dir);
            if (isWater(level, side)) {
                return side;
            }
        }
        return null;
    }

    private void abandonTarget() {
        if (this.target != null) {
            this.retries.merge(this.target, 1, Integer::sum);
        }
        this.target = null;
        this.salmon.setDigCommand(false);
    }

    /**
     * The nearest handful of diggable blocks, then one of them at random — so a shoal shown
     * pufferfish at the same time does not all converge on the single closest block.
     */
    @Nullable
    private BlockPos findDigSite() {
        BlockPos origin = this.salmon.blockPosition();
        Level level = this.salmon.level();
        List<BlockPos> candidates = new ArrayList<>();

        // Counted separately so a failed scan says WHY: no diggable material in range at all, or
        // plenty of it but none of it is exposed bed, or exposed bed that cannot be pathed to.
        int diggable = 0;
        int exhausted = 0;

        // Two cheap block-state lookups per position; nothing paths yet.
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RANGE, -SEARCH_DOWN, -SEARCH_RANGE),
                origin.offset(SEARCH_RANGE, SEARCH_UP, SEARCH_RANGE))) {
            if (!isDiggable(level.getBlockState(pos).getBlock())) {
                continue;
            }
            diggable++;
            if (this.retries.getOrDefault(pos, 0) >= MAX_RETRIES) {
                exhausted++;
                continue;
            }
            if (!isExposedBed(level, pos)) {
                continue;
            }
            candidates.add(pos.immutable());
        }

        if (candidates.isEmpty()) {
            debug("scan at {} FAILED: {} diggable blocks in range ({} retry-exhausted), none touching water."
                            + " Range is {} out / {} down / {} up.",
                    origin, diggable, exhausted, SEARCH_RANGE, SEARCH_DOWN, SEARCH_UP);
            this.debugNearestRejections(level, origin);
            return null;
        }

        // Nearest first, then path-check only a handful: A* is the expensive part, and the old code
        // ran one per candidate across the whole box.
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        int checks = Math.min(MAX_PATH_CHECKS, candidates.size());
        // Shuffle the shortlist so a shoal given pufferfish together does not all converge on the
        // single closest block.
        List<BlockPos> shortlist = new ArrayList<>(candidates.subList(0, checks));
        Collections.shuffle(shortlist, new java.util.Random(this.salmon.getRandom().nextLong()));

        debug("scan at {}: {} diggable, {} exposed, path-checking {}", origin, diggable, candidates.size(), checks);

        for (BlockPos pos : shortlist) {
            BlockPos approach = approachFor(level, pos);
            if (approach == null) {
                continue;
            }
            Path path = this.salmon.getNavigation().createPath(approach, 1);
            if (path != null && path.getNodeCount() > 0) {
                debug("chose {} via {} ({} nodes, canReach={})", pos, approach, path.getNodeCount(), path.canReach());
                return pos;
            }
            debug("  {} unreachable via {}", pos, approach);
        }
        debug("scan at {} FAILED: {} exposed candidates, none reachable", origin, candidates.size());
        return null;
    }

    /**
     * On a failed scan, dumps the five nearest diggable blocks with what is on each of their faces.
     * A count of rejections says nothing about <em>why</em>; this says it outright, so the next
     * layout that defeats the rule is one log line away from being understood instead of guessed at.
     */
    private void debugNearestRejections(Level level, BlockPos origin) {
        if (!DEBUG) {
            return;
        }
        List<BlockPos> nearest = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RANGE, -SEARCH_DOWN, -SEARCH_RANGE),
                origin.offset(SEARCH_RANGE, SEARCH_UP, SEARCH_RANGE))) {
            if (isDiggable(level.getBlockState(pos).getBlock())) {
                nearest.add(pos.immutable());
            }
        }
        nearest.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos pos : nearest.subList(0, Math.min(5, nearest.size()))) {
            StringBuilder faces = new StringBuilder("up=").append(name(level, pos.above()));
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                faces.append(' ').append(dir.getName().charAt(0)).append('=').append(name(level, pos.relative(dir)));
            }
            debug("  rejected {} ({}): {}", pos, name(level, pos), faces);
        }
    }

    private static String name(Level level, BlockPos pos) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).getPath();
    }

    private static boolean isDiggable(Block block) {
        return block == Blocks.SAND || block == Blocks.GRAVEL || block == Blocks.MUD || block == Blocks.DIRT;
    }

    /**
     * Diggable means <em>the fish can get its snout on it</em>: water touching the top face or any
     * of the four sides.
     *
     * <p>Both earlier versions were wrong in opposite directions, and each one produced a scan that
     * found thousands of candidate blocks and accepted none.
     *
     * <ul>
     *   <li>Horizontal-only failed on a real river: on a flat bed a block's
     *       horizontal neighbours are more bed, so only the rim of the channel qualified.</li>
     *   <li>Above-only failed on a dug-out tank: there the reachable faces are the <em>walls</em>,
     *       whose top face is capped by more wall or by the surface layer, so nothing qualified
     *       even with the fish swimming right against them.</li>
     * </ul>
     *
     * <p>Either face alone is enough, so both layouts work. The old {@code isSource()} requirement
     * is gone with them: flowing water is still water a fish can swim in, and demanding a full
     * source block rejected any pool filled by letting a bucket spread.
     */
    private static boolean isExposedBed(Level level, BlockPos pos) {
        if (isWater(level, pos.above())) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (isWater(level, pos.relative(dir))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /**
     * Drops whatever {@code pos}'s substrate was hiding. Called from the dig clip's frame event, so
     * it lives here beside the block list it belongs to rather than on the entity.
     */
    public static void dropFor(ServerLevel level, BlockPos pos) {
        List<Item> pool = poolFor(level.getBlockState(pos).getBlock());
        if (pool.isEmpty()) {
            return;
        }
        // Substrate the fish will actually turn over, so a relic is possible here. Rolled as a
        // replacement for the ordinary find rather than in addition to it: two drops from one dig
        // would read as a bug, and the point of a relic is that it came up INSTEAD of the silt.
        if (level.getRandom().nextFloat() < RELIC_CHANCE) {
            pool = SMOPItems.RELIC_DIG_DROPS;
        }
        ItemStack drop = new ItemStack(pool.get(level.getRandom().nextInt(pool.size())));
        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, drop));
    }

    /**
     * How often a dig turns up a relic instead of the substrate's ordinary contents.
     *
     * <p>Low on purpose. The fish digs on its own schedule with no player input, so this is a passive
     * trickle, not a farm — at one in eight it stays a thing you notice happening rather than a thing
     * you stand and wait for. Raise it if the dig turns out to be rarer in play than it looks here.
     */
    private static final float RELIC_CHANCE = 0.125F;

    private static List<Item> poolFor(Block block) {
        if (block == Blocks.SAND) return SMOPItems.SAND_DIG_DROPS;
        if (block == Blocks.GRAVEL) return SMOPItems.GRAVEL_DIG_DROPS;
        if (block == Blocks.MUD) return SMOPItems.MUD_DIG_DROPS;
        if (block == Blocks.DIRT) return SMOPItems.DIRT_DIG_DROPS;
        return List.of();
    }
}
