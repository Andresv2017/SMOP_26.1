package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.ai.goal.flying.OrbitFlightController;
import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class StealFromPlayerGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;
    private static final int ATTEMPT_CHANCE = 100;
    private static final int STEAL_COOLDOWN_TICKS = 20 * 60 * 5;

    private static final double ORBIT_RADIUS = 7.0D;
    private static final double ORBIT_HEIGHT = 4.0D;
    private static final float ORBIT_ANGULAR_SPEED = 3.0F;
    private static final int ORBIT_DURATION_TICKS = 100;

    private static final double DIVE_ARRIVAL_SQ = 2.0D * 2.0D;
    private static final int DIVE_GIVE_UP_TICKS = 200;

    private static final double FLEE_DISTANCE = 25.0D;
    private static final double FLEE_DISTANCE_SQ = FLEE_DISTANCE * FLEE_DISTANCE;
    private static final double FLEE_CLIMB_HEIGHT = 10.0D;
    private static final int FLEE_GIVE_UP_TICKS = 400;

    private final KriftognathusEntity mob;
    private final OrbitFlightController controller = new OrbitFlightController(0.04D, 0.4D, 0.6D, 10.0F);

    private enum Phase { ORBIT, DIVE, FLEE }

    @Nullable
    private Player victim;
    private Phase phase = Phase.ORBIT;
    private float orbitAngle;
    private int phaseTicks;
    private int cooldownUntilTick;
    private Vec3 stoopHeading = Vec3.ZERO;

    public StealFromPlayerGoal(KriftognathusEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide() || this.mob.isBaby() || this.mob.isTame()
                || !this.mob.isFlying() || !this.flightSettled()
                || this.mob.getTarget() != null || this.mob.tickCount < this.cooldownUntilTick
                || !this.mob.getStolenItem().isEmpty()) {
            return false;
        }
        if (this.mob.getRandom().nextInt(ATTEMPT_CHANCE) != 0) {
            return false;
        }
        this.victim = findVictim(this.mob);
        return this.victim != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.victim != null && this.victim.isAlive() && !this.victim.isSpectator()
                && !this.victim.isCreative() && !this.mob.isTame() && this.mob.getTarget() == null
                && this.mob.isFlying() && this.flightSettled();
    }

    private boolean flightSettled() {
        return !this.mob.isTakingOff() && !this.mob.isLanding();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.phase = Phase.ORBIT;
        this.phaseTicks = 0;
        // Enter the circle wherever the mob already is, rather than at a fixed angle it would have
        // to cut across the middle to reach — the orbit point advances from here, so the mob trails
        // it tangentially instead of chasing a dot around a ring it is not on yet.
        Player currentVictim = this.victim;
        if (currentVictim != null) {
            Vec3 offset = this.mob.position().subtract(currentVictim.position());
            this.orbitAngle = (float) Math.toDegrees(Math.atan2(offset.z, offset.x));
        } else {
            this.orbitAngle = 0.0F;
        }
    }

    @Override
    public void stop() {
        // A heist cut short still has to let go — a landing (which outranks this goal) or a fight
        // breaking out mid-flight would otherwise leave the mob carrying the loot indefinitely, and
        // canUse() refuses to start again while something is held, so it could never steal twice.
        this.dropStolenItem();
        this.victim = null;
    }

    @Override
    public void tick() {
        Player currentVictim = this.victim;
        if (currentVictim == null) {
            return;
        }
        switch (this.phase) {
            case ORBIT -> this.tickOrbit(currentVictim);
            case DIVE -> this.tickDive(currentVictim);
            case FLEE -> this.tickFlee(currentVictim);
        }
    }

    private void tickOrbit(Player victim) {
        this.mob.getLookControl().setLookAt(victim, 20.0F, this.mob.getMaxHeadXRot());
        this.orbitAngle += ORBIT_ANGULAR_SPEED;
        double radians = Math.toRadians(this.orbitAngle);
        Vec3 point = victim.position()
                .add(Math.cos(radians) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(radians) * ORBIT_RADIUS);
        this.controller.step(this.mob, point, null);

        if (++this.phaseTicks >= ORBIT_DURATION_TICKS) {
            this.phase = Phase.DIVE;
            this.phaseTicks = 0;
            // The power dive. Fired once on the transition, not per tick: it is a one-shot gesture
            // for committing to the run, and this goal is the only thing that plays it.
            this.mob.playSwoopClip();
            // The line the whole run is flown along, captured before the mob is on top of the victim
            // and the direction becomes meaningless. tickFlee carries straight through it.
            this.stoopHeading = horizontalDirection(
                    victim.getX() - this.mob.getX(), victim.getZ() - this.mob.getZ());
        }
    }

    private void tickDive(Player victim) {
        this.mob.getLookControl().setLookAt(victim, 30.0F, this.mob.getMaxHeadXRot());
        // stepFacing, not step: the body must stay pointed at the victim right through contact. The
        // heading-facing version turns the mob to face wherever its velocity points, and on the last
        // few blocks of a dive that vector goes small, jittery, and — on any overshoot past the
        // player — backwards, which is what made some heists happen with the beak facing away.
        this.controller.stepFacing(this.mob, victim.position().add(0.0D, 0.6D, 0.0D), victim.position());

        if (this.mob.distanceToSqr(victim) <= DIVE_ARRIVAL_SQ) {
            this.performSnatch(victim);
            this.phase = Phase.FLEE;
            this.phaseTicks = 0;
            return;
        }
        if (++this.phaseTicks >= DIVE_GIVE_UP_TICKS) {
            this.abortHeist();
        }
    }

    private void performSnatch(Player victim) {
        ItemStack taken = takeRandomHotbarStack(victim);
        this.mob.setStolenItem(taken);
    }

    private void tickFlee(Player victim) {
        Vec3 heading = this.stoopHeading;
        Vec3 fleeTarget = new Vec3(
                this.mob.getX() + heading.x * FLEE_DISTANCE,
                victim.getY() + FLEE_CLIMB_HEIGHT,
                this.mob.getZ() + heading.z * FLEE_DISTANCE);
        // Heading-facing is right here: the mob is travelling, and where it is going is where it
        // should be looking.
        this.controller.step(this.mob, fleeTarget, null);

        boolean farEnough = this.mob.distanceToSqr(victim) >= FLEE_DISTANCE_SQ;
        if (farEnough || ++this.phaseTicks >= FLEE_GIVE_UP_TICKS) {
            this.dropStolenItem();
            this.victim = null;
            this.cooldownUntilTick = this.mob.tickCount + STEAL_COOLDOWN_TICKS;
        }
    }

    private static Vec3 horizontalDirection(double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return new Vec3(dx / length, 0.0D, dz / length);
    }

    private void abortHeist() {
        this.mob.setStolenItem(ItemStack.EMPTY);
        this.victim = null;
        this.cooldownUntilTick = this.mob.tickCount + STEAL_COOLDOWN_TICKS;
    }

    private void dropStolenItem() {
        ItemStack stolen = this.mob.getStolenItem();
        if (!stolen.isEmpty() && this.mob.level() instanceof ServerLevel serverLevel) {
            this.mob.spawnAtLocation(serverLevel, stolen);
        }
        this.mob.setStolenItem(ItemStack.EMPTY);
    }

    private static ItemStack takeRandomHotbarStack(Player victim) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (!victim.getInventory().getItem(slot).isEmpty()) {
                occupied.add(slot);
            }
        }
        if (occupied.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int slot = occupied.get(victim.getRandom().nextInt(occupied.size()));
        ItemStack taken = victim.getInventory().getItem(slot);
        victim.getInventory().setItem(slot, ItemStack.EMPTY);
        return taken;
    }

    @Nullable
    private static Player findVictim(KriftognathusEntity mob) {
        List<Player> candidates = mob.level().getEntitiesOfClass(Player.class,
                mob.getBoundingBox().inflate(SEARCH_RADIUS), StealFromPlayerGoal::isValidVictim);
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Player candidate : candidates) {
            double distSq = mob.distanceToSqr(candidate);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isValidVictim(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && hasAnyHotbarItem(player);
    }

    private static boolean hasAnyHotbarItem(Player player) {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
