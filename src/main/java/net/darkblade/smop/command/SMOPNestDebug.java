package net.darkblade.smop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.smop.entity.Gendered;
import net.darkblade.smop.entity.SMOPAnimal;
import net.darkblade.smop.entity.ai.goal.egg.GenericLayEggGoal;
import net.darkblade.smop.tag.SMOPTags;
import net.darkblade.smop.entity.ai.goal.SeekNestSiteGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.darkblade.smop.SMOP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPNestDebug {

    private static final double RADIUS = 48.0D;

    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_HEIGHT = 6;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("nest")
                .executes(ctx -> dump(ctx.getSource()))
                .then(Commands.literal("watch")
                        .executes(ctx -> watch(ctx.getSource(), DEFAULT_WATCH_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 900))
                                .executes(ctx -> watch(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("stop").executes(ctx -> stopWatching(ctx.getSource()))));
    }

    // ───────────────────────────────────────────────────── LIVE WATCH ─────

    private static final Logger LOGGER = LoggerFactory.getLogger("smop-nest");
    private static final int DEFAULT_WATCH_SECONDS = 180;

    private record Stage(boolean inLove, boolean hasEgg, boolean breeding,
                         boolean seeking, boolean arrived, @Nullable BlockPos target) {}

    private static final Map<Integer, Stage> stages = new HashMap<>();
    @Nullable
    private static ServerPlayer watcher;
    private static int watchTicksLeft;

    private static int watch(CommandSourceStack source, int seconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player: the watch follows what is around you."));
            return 0;
        }
        watcher = player;
        watchTicksLeft = seconds * 20;
        stages.clear();
        source.sendSuccess(() -> Component.literal("Watching breeding and nesting for " + seconds
                        + "s. Feed a pair and let it run — /smop debug nest stop ends it early.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int stopWatching(CommandSourceStack source) {
        watchTicksLeft = 0;
        watcher = null;
        stages.clear();
        source.sendSuccess(() -> Component.literal("Nest watch stopped.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.@NotNull Post event) {
        if (watchTicksLeft <= 0 || watcher == null) {
            return;
        }
        if (--watchTicksLeft <= 0) {
            report(Component.literal("[nest] watch finished").withStyle(ChatFormatting.GRAY));
            watcher = null;
            stages.clear();
            return;
        }
        Vec3 at = watcher.position();
        for (SMOPAnimal mob : watcher.level().getEntitiesOfClass(SMOPAnimal.class,
                new AABB(at, at).inflate(RADIUS))) {
            track(mob);
        }
    }

    private static void track(SMOPAnimal mob) {
        SeekNestSiteGoal seek = findSeekGoal(mob);
        Stage now = new Stage(
                mob.isInLove(),
                mob.hasEgg(),
                isRunning(mob, BreedGoal.class),
                isRunning(mob, SeekNestSiteGoal.class),
                seek != null && seek.hasArrived(),
                seek == null ? null : seek.target());

        Stage before = stages.put(mob.getId(), now);
        String who = label(mob);
        if (before == null) {
            // First sight of this animal. Announce it only if it is already mid-chain, so starting
            // the watch after feeding a pair does not look like the watch is dead.
            if (now.inLove() || now.hasEgg()) {
                report(event(who, "already tracking: inLove=" + now.inLove() + " hasEgg=" + now.hasEgg(),
                        ChatFormatting.GRAY));
            }
            return;
        }

        if (!before.inLove() && now.inLove()) {
            report(event(who, "in love", ChatFormatting.LIGHT_PURPLE));
        }
        if (!before.breeding() && now.breeding()) {
            report(event(who, "breeding goal started — partner found", ChatFormatting.LIGHT_PURPLE));
        }
        if (before.breeding() && !now.breeding() && !now.hasEgg()) {
            // The distance is the whole diagnosis when a courtship fails: mating needs the pair
            // closer than half their two bodies plus a metre, and two wide animals can be flush
            // against each other and still be measured too far apart centre to centre.
            SMOPAnimal partner = nearestOfSameSpecies(mob, mob.level().getEntitiesOfClass(SMOPAnimal.class,
                    mob.getBoundingBox().inflate(24.0D)));
            String gap = partner == null ? "no partner in range"
                    : "partner " + fmt(Math.sqrt(mob.distanceToSqr(partner))) + " blocks away, mating needs < "
                      + fmt(Math.max(3.0D, (mob.getBbWidth() + partner.getBbWidth()) * 0.5D + 1.0D));
            report(event(who, "breeding goal stopped WITHOUT an egg — " + gap, ChatFormatting.YELLOW));
        }
        if (!before.hasEgg() && now.hasEgg()) {
            report(event(who, "GRAVID — carrying the egg", ChatFormatting.GREEN));
        }
        if (!Objects.equals(before.target(), now.target()) && now.target() != null) {
            BlockPos target = now.target();
            report(event(who, "nest site chosen at " + target.getX() + "," + target.getY() + ","
                    + target.getZ() + " (" + fmt(Math.sqrt(mob.blockPosition().distSqr(target)))
                    + " blocks away)", ChatFormatting.AQUA));
        }
        if (before.target() != null && now.target() == null && now.hasEgg() && !now.arrived()) {
            report(event(who, "nest site abandoned — unreachable, re-choosing", ChatFormatting.YELLOW));
        }
        if (!before.arrived() && now.arrived()) {
            report(event(who, "arrived at the nest, holding still for the laying countdown",
                    ChatFormatting.AQUA));
        }
        if (before.hasEgg() && !now.hasEgg()) {
            BlockPos egg = findEggNear(mob);
            report(event(who, egg == null
                    ? "egg gone but no egg block found nearby — laying FAILED or it was broken"
                    : "EGG LAID at " + egg.getX() + "," + egg.getY() + "," + egg.getZ(),
                    egg == null ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }

    @Nullable
    private static BlockPos findEggNear(SMOPAnimal mob) {
        BlockPos here = mob.blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-4, -3, -4), here.offset(4, 3, 4))) {
            if (!mob.level().getBlockState(pos).is(SMOPTags.Blocks.EGG_BLOCKS)) {
                continue;
            }
            if (best == null || pos.distSqr(here) < best.distSqr(here)) {
                best = pos.immutable();
            }
        }
        return best;
    }

    private static String label(SMOPAnimal mob) {
        String sex = mob instanceof Gendered g ? (g.isMale() ? "male" : "female") : "";
        return name(mob) + " " + sex + "#" + mob.getId();
    }

    private static Component event(String who, String what, ChatFormatting colour) {
        return Component.literal("[nest] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(who + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(what).withStyle(colour));
    }

    private static void report(Component message) {
        LOGGER.info(message.getString());
        if (watcher != null) {
            watcher.sendSystemMessage(message);
        }
    }

    private static int dump(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<SMOPAnimal> mobs = level.getEntitiesOfClass(SMOPAnimal.class, new AABB(at, at).inflate(RADIUS))
                .stream()
                .sorted(Comparator.comparingDouble(m -> m.distanceToSqr(at)))
                .limit(4)
                .toList();

        if (mobs.isEmpty()) {
            source.sendFailure(Component.literal("No SMOP animals within " + (int) RADIUS + " blocks."));
            return 0;
        }
        for (SMOPAnimal mob : mobs) {
            for (Component line : describe(mob, mobs)) {
                source.sendSuccess(() -> line, false);
            }
        }
        return mobs.size();
    }

    private static List<Component> describe(SMOPAnimal mob, List<SMOPAnimal> nearby) {
        List<Component> out = new ArrayList<>();
        Level level = mob.level();
        BlockPos here = mob.blockPosition();

        String sex = mob instanceof Gendered g ? (g.isMale() ? "male" : "female") : "sexless";
        out.add(Component.literal("── " + name(mob) + " " + sex + (mob.isBaby() ? " baby" : " adult")
                        + " @" + here.getX() + "," + here.getY() + "," + here.getZ())
                .withStyle(ChatFormatting.AQUA));

        // ── Can it fall in love at all, and is it in love now.
        out.add(line("love", "inLove=" + mob.isInLove()
                + " canFallInLove=" + mob.canFallInLove()
                + " age=" + mob.getAge()
                + " inWater=" + mob.isInWater()));

        // ── The pairing itself, asked of the mobs rather than reasoned about.
        SMOPAnimal partner = nearestOfSameSpecies(mob, nearby);
        if (partner == null) {
            out.add(line("mate", "no other " + name(mob) + " in range"));
        } else {
            double distance = Math.sqrt(mob.distanceToSqr(partner));
            List<String> blockers = new ArrayList<>();
            if (!mob.isInLove()) {
                blockers.add("self not in love");
            }
            if (!partner.isInLove()) {
                blockers.add("partner not in love");
            }
            if (mob instanceof Gendered a && partner instanceof Gendered b && a.isMale() == b.isMale()) {
                blockers.add("same sex");
            }
            if (mob.isInWater()) {
                blockers.add("self in water");
            }
            if (partner.isInWater()) {
                blockers.add("partner in water");
            }
            if (mob.isBaby() || partner.isBaby()) {
                blockers.add("baby");
            }
            // Vanilla's BreedGoal only ever looks 8 blocks for a partner, so a pair further apart
            // than that is not refusing to breed — it has not found the other one yet.
            if (distance > 8.0D) {
                blockers.add("out of BreedGoal's 8-block search");
            }
            out.add(line("mate", "partner " + (partner instanceof Gendered p && p.isMale() ? "male" : "female")
                    + " at " + fmt(distance) + " blocks"
                    + " canMate=" + mob.canMate(partner)
                    + (blockers.isEmpty() ? "" : "  blocked by: " + String.join(", ", blockers))));
        }

        // ── Whether the goals are actually running, and whether the animal can path anywhere. A pair
        // that is in love, in range and still apart is a PATHING problem, not a breeding one: this
        // body is three blocks wide, so the navigator needs a three-wide corridor and a terraced
        // beach does not always have one.
        {
            Path path = mob.getNavigation().getPath();
            out.add(line("goals", "breed=" + (isRunning(mob, BreedGoal.class) ? "RUNNING" : "idle")
                    + " lay=" + (isRunning(mob, GenericLayEggGoal.class) ? "RUNNING" : "idle")
                    + " | nav done=" + mob.getNavigation().isDone()
                    + " path=" + (path == null ? "null"
                    : path.getNodeCount() + " nodes, at " + path.getNextNodeIndex()
                      + ", reach=" + path.canReach())));
        }

        // ── The clutch, and the goal that is supposed to be carrying it somewhere.
        SeekNestSiteGoal seek = findSeekGoal(mob);
        boolean seekRunning = isRunning(mob, SeekNestSiteGoal.class);
        String nest = "hasEgg=" + mob.hasEgg() + " mammal=" + mob.isMammal();
        if (seek == null) {
            nest += " | no SeekNestSiteGoal on this species";
        } else {
            BlockPos target = seek.target();
            nest += " | seekGoal=" + (seekRunning ? "RUNNING" : "idle")
                    + " arrived=" + seek.hasArrived()
                    + " rejected=" + seek.rejectedCount()
                    + " target=" + (target == null ? "none"
                    : target.getX() + "," + target.getY() + "," + target.getZ()
                      + " (" + fmt(Math.sqrt(here.distSqr(target))) + " blocks)");
        }
        out.add(line("nest", nest));

        // ── Is the animal standing somewhere it could lay, and if not, what fails. The verdict is
        // the species'; the four facts beside it are raw block reads.
        out.add(line("site", "here=" + (mob.isNestSiteAt(here) ? "OK" : "NO")
                + "  [air=" + level.getBlockState(here).isAir()
                + " airAbove=" + level.getBlockState(here.above()).isAir()
                + " sky=" + level.canSeeSky(here)
                + " below=" + name(level.getBlockState(here.below()).getBlock().getDescriptionId()) + "]"));

        // ── And whether there is anywhere to go, which is the difference between "cannot find a
        // beach" and "found one and cannot reach it".
        int[] sites = countSites(mob, here);
        out.add(line("sites", "within " + SCAN_RADIUS + ": " + sites[0] + " legal, "
                + sites[1] + " of them 8+ blocks away (the ones it will actually pick)"));

        // ── Eggs already on the ground. The reason this is worth a line of its own: the nesting walk
        // is deliberately eight blocks or more, so a successful laying happens away from where the
        // pair mated and reads exactly like a failure from where the player is standing —
        // hasEgg=false, no egg in sight, nothing obviously wrong.
        BlockPos nearestEgg = null;
        int eggs = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                here.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS),
                here.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
            if (!level.getBlockState(pos).is(SMOPTags.Blocks.EGG_BLOCKS)) {
                continue;
            }
            eggs++;
            if (nearestEgg == null || pos.distSqr(here) < nearestEgg.distSqr(here)) {
                nearestEgg = pos.immutable();
            }
        }
        out.add(line("eggs", eggs + " laid within " + SCAN_RADIUS
                + (nearestEgg == null ? "" : ", nearest at " + nearestEgg.getX() + "," + nearestEgg.getY()
                  + "," + nearestEgg.getZ() + " (" + fmt(Math.sqrt(nearestEgg.distSqr(here))) + " blocks)")));

        return out;
    }

    @Nullable
    private static SMOPAnimal nearestOfSameSpecies(SMOPAnimal mob, List<SMOPAnimal> nearby) {
        return nearby.stream()
                .filter(other -> other != mob && other.getClass() == mob.getClass())
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private static SeekNestSiteGoal findSeekGoal(SMOPAnimal mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SeekNestSiteGoal seek) {
                return seek;
            }
        }
        return null;
    }

    private static boolean isRunning(SMOPAnimal mob, Class<? extends Goal> type) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private static int[] countSites(SMOPAnimal mob, BlockPos origin) {
        int legal = 0;
        int distant = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
            if (!mob.isNestSiteAt(pos)) {
                continue;
            }
            legal++;
            if (pos.distSqr(origin) >= 64.0D) {
                distant++;
            }
        }
        return new int[] {legal, distant};
    }

    private static String name(net.minecraft.world.entity.Entity mob) {
        String id = mob.getType().getDescriptionId();
        return id.substring(id.lastIndexOf('.') + 1);
    }

    private static String name(String descriptionId) {
        return descriptionId.substring(descriptionId.lastIndexOf('.') + 1);
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }

    private static Component line(String key, String value) {
        return Component.literal("   " + key + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private SMOPNestDebug() {}
}
