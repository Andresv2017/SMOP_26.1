package net.darkblade.smop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.niras.NirasmosaurusEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /smop debug bite} — why a hunt does not connect.
 *
 * <p><b>Why a third watcher, beside swim and nest.</b> Those two follow their own chains and are good
 * at them. A hunt is a fourth chain that happens to run on the same animals: something has to be
 * marked, a path has to exist to it, the body has to come round onto it, the animal has to arrive
 * rather than park, and only then does a window open where the jaws are. Every one of those links
 * failed at least once in 1c, and all five failed with the same symptom from outside — the animal is
 * clearly interested and nothing lands.
 *
 * <p><b>The three distances are printed separately, and that is the point.</b> Three different pieces
 * of code test three different things and it is invisible which one is refusing: {@code reach}
 * measures centre to centre in 3D, {@code stopDistance} measures the HORIZONTAL only, and the
 * navigation cut-off additionally wants the vertical gap under 1.5. An animal hovering above its prey
 * satisfies one, fails another, and looks simply broken.
 *
 * <p><b>And the two yaws, because they are not the same field.</b> {@code yBodyRot} is written by the
 * MoveControl and turns at the swim control's rate; {@code yHeadRot} is written by the LookControl and
 * tracks the target. A bite aimed off the wrong one fires into open water while the head visibly
 * points at the prey — which is not something a player can see, and is exactly what happened.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPBiteDebug {

    private static final double RADIUS = 48.0D;
    private static final int DEFAULT_WATCH_SECONDS = 120;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("bite")
                .executes(ctx -> dump(ctx.getSource()))
                .then(Commands.literal("watch")
                        .executes(ctx -> watch(ctx.getSource(), DEFAULT_WATCH_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 900))
                                .executes(ctx -> watch(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("stop").executes(ctx -> stopWatching(ctx.getSource()))));
    }

    // ───────────────────────────────────────────────────── LIVE WATCH ─────

    private static final Logger LOGGER = LoggerFactory.getLogger("smop-bite");

    /**
     * One hunter's last known position along the chain. Compared each tick; only <b>changes</b> are
     * reported, except for the swing, which is a single-tick event and is always worth a line.
     */
    private record Stage(int targetId, boolean chasing, boolean navDone, boolean swinging,
                         boolean mobInWater, boolean targetInWater, boolean pathNull) {}

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
        source.sendSuccess(() -> Component.literal("Watching hunts for " + seconds
                        + "s. Provoke one or drop prey in — /smop debug bite stop ends it early.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int stopWatching(CommandSourceStack source) {
        watchTicksLeft = 0;
        watcher = null;
        stages.clear();
        source.sendSuccess(() -> Component.literal("Bite watch stopped.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.@NotNull Post event) {
        if (watchTicksLeft <= 0 || watcher == null) {
            return;
        }
        if (--watchTicksLeft <= 0) {
            report(Component.literal("[bite] watch finished").withStyle(ChatFormatting.GRAY));
            watcher = null;
            stages.clear();
            return;
        }
        Vec3 at = watcher.position();
        for (NirasmosaurusEntity mob : watcher.level().getEntitiesOfClass(NirasmosaurusEntity.class,
                new AABB(at, at).inflate(RADIUS))) {
            track(mob);
        }
    }

    private static void track(NirasmosaurusEntity mob) {
        LivingEntity target = mob.getTarget();
        Path path = mob.getNavigation().getPath();
        Stage now = new Stage(
                target == null ? -1 : target.getId(),
                isRunning(mob, AnimatableMeleeAttackGoal.class),
                mob.getNavigation().isDone(),
                mob.swinging,
                mob.isInWater(),
                target != null && target.isInWater(),
                path == null);

        Stage before = stages.put(mob.getId(), now);
        String who = "niras#" + mob.getId();
        if (before == null) {
            return;
        }

        if (before.targetId() != now.targetId()) {
            if (now.targetId() == -1) {
                report(event(who, "target LOST", ChatFormatting.YELLOW));
            } else if (target != null) {
                report(event(who, "target acquired: " + name(target) + "  " + gaps(mob, target),
                        ChatFormatting.LIGHT_PURPLE));
            }
        }

        if (target == null) {
            return;
        }

        // Whether the chase goal is even running is the first fork of the whole diagnosis: a marked
        // target the goal refuses is a PATHING answer, and a running goal that never closes is a
        // steering one.
        if (!before.chasing() && now.chasing()) {
            report(event(who, "chase goal STARTED  " + pathOf(mob), ChatFormatting.AQUA));
        }
        if (before.chasing() && !now.chasing()) {
            // canUse() needs a non-null path (or vanilla melee range) and is rate-limited to one
            // check per 20 ticks, so a goal that stops while the target lives is nearly always the
            // navigator declining to route there.
            report(event(who, "chase goal STOPPED with the target still alive  " + pathOf(mob)
                    + "  " + gaps(mob, target), ChatFormatting.YELLOW));
        }

        // The medium crossing, called out on both sides because the failure is asymmetric: the ground
        // navigator will happily path into water, and the swimming one only produces water nodes.
        if (before.targetInWater() && !now.targetInWater()) {
            report(event(who, "target LEFT THE WATER — mob is " + (now.mobInWater() ? "still swimming" : "ashore")
                    + "  " + pathOf(mob), ChatFormatting.GOLD));
        }
        if (before.mobInWater() != now.mobInWater()) {
            report(event(who, "mob crossed the shoreline — now " + (now.mobInWater() ? "in water" : "ashore")
                    + ", navigator swapped  " + pathOf(mob), ChatFormatting.GOLD));
        }
        if (!before.pathNull() && now.pathNull()) {
            report(event(who, "path went NULL while chasing — nothing routable to the target from here",
                    ChatFormatting.RED));
        }

        // Parked: navigation finished with a live target still out of contact. This is the
        // "it stops above them" report, and the vertical gap printed here is the answer to it.
        if (!before.navDone() && now.navDone()) {
            report(event(who, "navigation DONE (parked) with target alive  " + gaps(mob, target)
                    + "  " + pathOf(mob), ChatFormatting.YELLOW));
        }

        // The swing itself. Everything that matters about a miss is in this one line: where the prey
        // was, and where the two yaws were pointing when the jaws closed.
        if (!before.swinging() && now.swinging()) {
            report(event(who, "SWING  " + gaps(mob, target) + "  " + aim(mob, target), ChatFormatting.WHITE));
        }
    }

    /**
     * Called from each bite's {@link net.darkblade.deluxelib.combat.HitWindow} as it sweeps.
     *
     * <p><b>This is the fork the watch could not resolve on its own.</b> Everything else here is read
     * from outside the animal; whether a swing actually happened is not visible from there, and the
     * first run produced a hunt that closed on a squid, parked inside its own reach, and never
     * appeared to bite. Two outcomes are possible and they need opposite fixes: no line at all means
     * the goal never fired the swing and the gate is at fault, while a line saying no contact means
     * the swing fired and the geometry missed.
     */
    public static void reportSweep(@NotNull LivingEntity attacker, @NotNull Vec3 origin,
                                   @NotNull Vec3 facing, int hits) {
        if (watchTicksLeft <= 0 || watcher == null) {
            return;
        }
        LivingEntity target = attacker instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
        String range = target == null ? "no target"
                : "target at " + fmt(Math.sqrt(attacker.distanceToSqr(target)));
        report(event("niras#" + attacker.getId(),
                "WINDOW swept: " + (hits > 0 ? hits + " HIT" : "NO CONTACT")
                        + "  " + range
                        + "  origin=" + fmt(origin.x) + "," + fmt(origin.y) + "," + fmt(origin.z)
                        + "  facing=" + fmt(facing.x) + "," + fmt(facing.y) + "," + fmt(facing.z),
                hits > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    // ───────────────────────────────────────────────────── READINGS ─────

    /**
     * The three distances, each beside the threshold that actually tests it.
     *
     * <p>{@code reach} is what decides whether to swing and is 3D; {@code stop} is horizontal only;
     * the vertical gate is a flat 1.5 inside the goal. Printing all three together is the only way to
     * see which of them an animal is sitting on the wrong side of.
     */
    private static String gaps(@NotNull NirasmosaurusEntity mob, @NotNull LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = target.getY() - mob.getY();
        double full = Math.sqrt(mob.distanceToSqr(target));
        return "d3D=" + fmt(full) + " (reach 3.8)"
                + " dH=" + fmt(horizontal) + " (stop 3.2)"
                + " dV=" + fmt(vertical) + " (nav gate 1.5)";
    }

    /**
     * Where the body points, where the head points, and where the prey actually is.
     *
     * <p>The head error is what the bite volume is aimed by; the body error is what it USED to be
     * aimed by, and the gap between the two columns is the bug that made the water bite miss. The rig
     * only bends the neck 35 degrees of yaw, so a head error near zero with a large body error means
     * the aim is honest but the model is leaning as far as it can.
     */
    private static String aim(@NotNull NirasmosaurusEntity mob, @NotNull LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        float bearing = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float bodyError = Mth.wrapDegrees(bearing - mob.yBodyRot);
        float headError = Mth.wrapDegrees(bearing - mob.getYHeadRot());
        return "bodyYawErr=" + fmt(bodyError) + "deg headYawErr=" + fmt(headError) + "deg"
                + " xRot=" + fmt(mob.getXRot()) + "deg"
                + " speed=" + fmt(mob.getDeltaMovement().length());
    }

    private static String pathOf(@NotNull NirasmosaurusEntity mob) {
        Path path = mob.getNavigation().getPath();
        if (path == null) {
            return "path=null";
        }
        Node end = path.getEndNode();
        String where = end == null ? "no end node"
                : "ends " + end.x + "," + end.y + "," + end.z
                  + (mob.level().getFluidState(new BlockPos(end.x, end.y, end.z)).isEmpty() ? " (dry)" : " (water)");
        return "path=" + path.getNodeCount() + " nodes at " + path.getNextNodeIndex()
               + ", canReach=" + path.canReach() + ", " + where;
    }

    // ───────────────────────────────────────────────────── SNAPSHOT ─────

    private static int dump(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<NirasmosaurusEntity> mobs = level.getEntitiesOfClass(NirasmosaurusEntity.class,
                        new AABB(at, at).inflate(RADIUS))
                .stream()
                .sorted(Comparator.comparingDouble(m -> m.distanceToSqr(at)))
                .limit(4)
                .toList();

        if (mobs.isEmpty()) {
            source.sendFailure(Component.literal("No Nirasmosaurus within " + (int) RADIUS + " blocks."));
            return 0;
        }
        for (NirasmosaurusEntity mob : mobs) {
            for (Component line : describe(mob)) {
                source.sendSuccess(() -> line, false);
            }
        }
        return mobs.size();
    }

    private static List<Component> describe(@NotNull NirasmosaurusEntity mob) {
        List<Component> out = new ArrayList<>();
        BlockPos here = mob.blockPosition();
        out.add(Component.literal("── niras#" + mob.getId() + (mob.isBaby() ? " baby" : " adult")
                        + " @" + here.getX() + "," + here.getY() + "," + here.getZ()
                        + (mob.isInWater() ? " (in water)" : " (ashore)"))
                .withStyle(ChatFormatting.AQUA));

        // Whether it is allowed to start a fight at all, spelled out rather than as one boolean:
        // three separate conditions gate it and any of them reads as "it just ignores me".
        out.add(line("temper", "aggressive=" + mob.isAggressive()
                + " baby=" + mob.isBaby()
                + " asleep=" + mob.isInSleepCycle()
                + " tame=" + mob.isTame()));

        LivingEntity target = mob.getTarget();
        if (target == null) {
            out.add(line("target", "none"));
        } else {
            out.add(line("target", name(target) + (target.isInWater() ? " (in water)" : " (ashore)")
                    + "  " + gaps(mob, target)));
            out.add(line("aim", aim(mob, target)));
        }
        out.add(line("chase", (isRunning(mob, AnimatableMeleeAttackGoal.class) ? "RUNNING" : "idle")
                + " | nav done=" + mob.getNavigation().isDone()
                + " " + pathOf(mob)));
        return out;
    }

    // ───────────────────────────────────────────────────── PLUMBING ─────

    private static boolean isRunning(@NotNull NirasmosaurusEntity mob, @NotNull Class<? extends Goal> type) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private static String name(@NotNull LivingEntity entity) {
        return entity.getType().toShortString() + "#" + entity.getId();
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    private static Component line(String tag, String body) {
        return Component.literal("  " + tag + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(body).withStyle(ChatFormatting.WHITE));
    }

    private static Component event(String who, String what, ChatFormatting colour) {
        return Component.literal("[bite] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(who + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(what).withStyle(colour));
    }

    private static void report(Component message) {
        LOGGER.info(message.getString());
        if (watcher != null) {
            watcher.sendSystemMessage(message);
        }
    }

    private SMOPBiteDebug() {}
}
