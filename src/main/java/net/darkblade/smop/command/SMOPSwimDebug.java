package net.darkblade.smop.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPAnimal;
import net.darkblade.smop.entity.SMOPWaterAnimal;
import net.darkblade.smop.entity.niras.NirasmosaurusEntity;
import net.darkblade.smop.entity.SwimTilt;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPSwimDebug {

    private static final double RADIUS = 48.0D;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("swim")
                .executes(ctx -> dump(ctx.getSource()))
                .then(Commands.literal("watch").executes(ctx -> watch(ctx.getSource()))));
    }

    // ───────────────────────────────────────────────────── TIME-DOMAIN WATCH ─────

    private static final Logger LOGGER = LoggerFactory.getLogger("smop-swim");
    private static final int WATCH_TICKS = 60;

    private static @Nullable SMOPWaterAnimal watched;
    private static int watchLeft;

    private static int watch(CommandSourceStack source) {
        Vec3 at = source.getPosition();
        watched = source.getLevel()
                .getEntitiesOfClass(SMOPWaterAnimal.class, new AABB(at, at).inflate(RADIUS))
                .stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(at)))
                .orElse(null);
        if (watched == null) {
            source.sendFailure(Component.literal("No SMOP swimmer within " + (int) RADIUS + " blocks."));
            return 0;
        }
        String name = name(watched);
        watchLeft = WATCH_TICKS;
        LOGGER.info("watching {} ({})", name, watched instanceof SwimTilt ? "has tilt" : "no tilt");
        LOGGER.info("tick | xRot     netYaw   swimPitch swimRoll  yRot     dY");
        source.sendSuccess(() -> Component
                .literal("Sampling " + name + " for " + WATCH_TICKS + " ticks to the log.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.@NotNull Post event) {
        if (watchLeft <= 0 || event.getEntity() != watched) {
            return;
        }
        SMOPWaterAnimal mob = watched;
        String tilt = mob instanceof SwimTilt t
                ? String.format("%9.3f %8.3f", t.swimPitch(), t.swimRoll())
                : String.format("%9s %8s", "--", "--");
        LOGGER.info(String.format("%4d | %8.3f %8.3f %s %8.3f %7.4f",
                WATCH_TICKS - watchLeft, mob.getXRot(), Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot),
                tilt, mob.getYRot(), mob.getDeltaMovement().y));
        if (--watchLeft <= 0) {
            watched = null;
        }
    }

    private static int dump(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        // Every SMOP mob, not only the swimmers: a land mob in the same dump is a useful control for
        // the fields that are not about water at all (speed, goals, the move control being ticked).
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(RADIUS),
                        m -> m.getType().getDescriptionId().contains("smop"))
                .stream()
                .sorted(Comparator.comparingDouble(m -> m.distanceToSqr(at)))
                .limit(6)
                .toList();

        if (mobs.isEmpty()) {
            source.sendFailure(Component.literal("No SMOP mobs within " + (int) RADIUS + " blocks."));
            return 0;
        }
        for (Mob mob : mobs) {
            for (Component line : describe(mob)) {
                source.sendSuccess(() -> line, false);
            }
        }
        return mobs.size();
    }

    private static List<Component> describe(Mob mob) {
        Path path = mob.getNavigation().getPath();
        Vec3 v = mob.getDeltaMovement();

        String running = mob.goalSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(w -> w.getGoal().getClass().getSimpleName())
                .collect(Collectors.joining(", "));

        return List.of(
                Component.literal("── " + name(mob)
                        + " @" + (int) mob.getX() + "," + (int) mob.getY() + "," + (int) mob.getZ())
                        .withStyle(ChatFormatting.AQUA),
                line("medium", "inWater=" + mob.isInWater()
                        + " underWater=" + mob.isUnderWater()
                        + " onGround=" + mob.onGround()),
                line("nav", mob.getNavigation().getClass().getSimpleName()
                        + " done=" + mob.getNavigation().isDone()
                        + " path=" + (path == null ? "null"
                        : path.getNodeCount() + " nodes, at " + path.getNextNodeIndex()
                        + ", reach=" + path.canReach())),
                // Legs longer than this cannot be pathed at all: PathFinder drops every node whose
                // walked distance reaches the cap, and then quietly returns a closest-approach stub
                // instead of failing. That is what turned the Nirasmosaurus's long legs into hops.
                line("range", "follow=" + fmt(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))
                        + " (path cap, unless setRequiredPathLength raised it)"),
                line("malus", "WATER=" + mob.getPathfindingMalus(PathType.WATER)
                        + " WATER_BORDER=" + mob.getPathfindingMalus(PathType.WATER_BORDER)),
                line("drive", "speed=" + fmt(mob.getSpeed())
                        + " delta=(" + fmt(v.x) + ", " + fmt(v.y) + ", " + fmt(v.z) + ")"
                        + " zza=" + fmt(mob.zza) + " yya=" + fmt(mob.yya)
                        + " xRot=" + fmt(mob.getXRot()) + " yRot=" + fmt(mob.getYRot())),
                line("want", "(" + fmt(mob.getMoveControl().getWantedX())
                        + ", " + fmt(mob.getMoveControl().getWantedY())
                        + ", " + fmt(mob.getMoveControl().getWantedZ()) + ")"
                        + " dist=" + fmt(Math.sqrt(mob.distanceToSqr(
                                mob.getMoveControl().getWantedX(),
                                mob.getMoveControl().getWantedY(),
                                mob.getMoveControl().getWantedZ())))
                        + " speedMod=" + fmt(mob.getMoveControl().getSpeedModifier())),
                line("state", "noActionTime=" + mob.getNoActionTime()
                        + " move=" + mob.getMoveControl().getClass().getSimpleName()
                        + " look=" + mob.getLookControl().getClass().getSimpleName()
                        + " hasWanted=" + mob.getMoveControl().hasWanted()),
                line("tilt", mob instanceof SwimTilt t
                        ? "swimPitch=" + fmt(t.swimPitch()) + " swimRoll=" + fmt(t.swimRoll())
                          + "   (pitch tracks climb/dive, roll tracks TURN RATE — both read ~0 on a"
                          + " straight steady course, which is correct)"
                        : "not implemented — this mob renders level"),
                line("sprint", mob instanceof SMOPWaterAnimal swimmer
                        ? "fast=" + swimmer.isSwimmingFast() + " cruise=" + swimmer.isSwimmingCruise()
                          + " target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getName().getString())
                        : "n/a"),
                // The sleep cycle and the clutch, because both are state you would otherwise have to
                // infer from what the animal happens to be doing. The medium here is the LATCH, not
                // where the animal is now: that is the whole point of it, and a mismatch between this
                // and the `medium` line above on a sleeping animal is correct rather than a bug.
                line("sleep", mob instanceof SMOPAnimal sleeper
                        ? "phase=" + sleeper.sleepPhase()
                          + " night=" + sleeper.sleepUrge().isNight()
                          + " wantsToSleep=" + sleeper.sleepUrge().wantsToSleep()
                          + (mob instanceof NirasmosaurusEntity niras
                                ? " latchedInWater=" + niras.isSleepingInWater() : "")
                        : "n/a"),
                line("nest", mob instanceof SMOPAnimal layer
                        ? "hasEgg=" + layer.hasEgg() + " mammal=" + layer.isMammal()
                          + (layer instanceof net.darkblade.smop.entity.Gendered g
                                ? " male=" + g.isMale() : "")
                        : "n/a"),
                line("goals", running.isEmpty() ? "(none running)" : running));
    }

    private static String name(Mob mob) {
        String id = mob.getType().getDescriptionId();
        return id.substring(id.lastIndexOf('.') + 1);
    }

    private static Component line(String key, String value) {
        return Component.literal("   " + key + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static String fmt(double d) {
        return String.format("%.3f", d);
    }

    private SMOPSwimDebug() {}
}
