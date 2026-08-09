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

/**
 * The theft: a wild adult Krifto already cruising spots an unwary player, circles them once at a wide
 * radius, then makes a single continuous pass — {@code swoop} in, take a random hotbar slot at the
 * closest point, and climb straight out along the same line, dropping the loot once it is clear.
 *
 * <p>Deliberately one unbroken movement. There is no pause on top of the victim to play a grab
 * animation: the authored {@code steal} clip is not used, and the {@code swoop} started at the top of
 * the dive runs across the whole pass. A raptor does not stop mid-strike, and stopping here also
 * destroyed the through-line the escape depends on (see {@link #tickFlee}).
 *
 * <p>Never requests a take-off for this — it only ever engages while already flying. {@code
 * TameFeedGoal}'s class note covers why a goal that needs the flight lifecycle to cooperate is
 * dangerous to get wrong; this goal sidesteps that class of bug by never asking the lifecycle for
 * anything. What it does need is to not fight {@code TakeoffGoal}/{@code LandingGoal} for MOVE/LOOK
 * while they are running, so — like {@code FollowOwnerFlyingGoal} — it is registered strictly below
 * both (a larger priority number) so they can always steal the flag back, and it stands down on its
 * own via {@link #flightSettled()} rather than relying purely on being forced off.
 */
public class StealFromPlayerGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;
    /** One-in-N chance per eligible tick — an event, not a tax. @see #canUse() */
    private static final int ATTEMPT_CHANCE = 100;
    /** Minutes-scale, per mob, after a heist ends however it ends. */
    private static final int STEAL_COOLDOWN_TICKS = 20 * 60 * 5;

    private static final double ORBIT_RADIUS = 7.0D;
    private static final double ORBIT_HEIGHT = 4.0D;
    private static final float ORBIT_ANGULAR_SPEED = 3.0F;
    private static final int ORBIT_DURATION_TICKS = 100;

    private static final double DIVE_ARRIVAL_SQ = 2.0D * 2.0D;
    /** Safety net: the victim outflew the dive (or simply ran indoors). */
    private static final int DIVE_GIVE_UP_TICKS = 200;

    private static final double FLEE_DISTANCE = 25.0D;
    private static final double FLEE_DISTANCE_SQ = FLEE_DISTANCE * FLEE_DISTANCE;
    /**
     * Height above the victim the escape climbs to. Comfortably over
     * {@code KriftognathusEntity#getMinFlightAltitude()} (6), so the mob ends the heist back at a
     * believable cruising height instead of at the altitude the dive left it.
     */
    private static final double FLEE_CLIMB_HEIGHT = 10.0D;
    /** Safety net if the victim somehow keeps pace forever. */
    private static final int FLEE_GIVE_UP_TICKS = 400;

    private final KriftognathusEntity mob;
    /**
     * Position gain and damping are {@code FollowOwnerFlyingGoal}'s proven pair — that class's note
     * explains why they are not independent knobs, and detuning them was making the approach ring.
     * Only the speed cap is raised over the escort's, so a heist reads as faster without the flight
     * itself becoming less stable.
     */
    private final OrbitFlightController controller = new OrbitFlightController(0.04D, 0.4D, 0.6D, 10.0F);

    private enum Phase { ORBIT, DIVE, FLEE }

    @Nullable
    private Player victim;
    private Phase phase = Phase.ORBIT;
    private float orbitAngle;
    private int phaseTicks;
    private int cooldownUntilTick;
    /** Horizontal heading of the dive, held so the escape carries through it. @see #tickFlee */
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

    /** Same purpose as {@code FollowOwnerFlyingGoal}'s — see that class for why it matters. */
    private boolean flightSettled() {
        return !this.mob.isTakingOff() && !this.mob.isLanding();
    }

    /**
     * Every tick, not every other one. {@code Mob#serverAiStep} only runs the full goal selector on
     * alternate ticks and gives the rest to {@code tickRunningGoals(false)}, which skips any goal
     * that does not ask for this — so without it the PD loop below writes velocity at 10 Hz while
     * the physics integrates at 20, and the flight visibly stutters between corrections.
     */
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

    /**
     * The grab: instantaneous, at the closest point of the pass. There is no clip and no hold — the
     * whole heist is one continuous stoop that carries through into the climb-out, so pausing on top
     * of the victim to play something would break the very line {@link #tickFlee} exists to preserve.
     * The {@code swoop} started back at the dive is still running over all of this.
     */
    private void performSnatch(Player victim) {
        ItemStack taken = takeRandomHotbarStack(victim);
        this.mob.setStolenItem(taken);
    }

    /**
     * Carries the stoop through and climbs out of it, the way a raptor leaves a strike — straight on
     * along {@link #stoopHeading}, gaining height.
     *
     * <p>Two earlier versions of this were wrong in different ways. Fleeing along the raw 3D vector
     * from victim to mob skimmed the ground, because the dive has just put the mob at the player's own
     * height and that vector is nearly flat. Fleeing along the <em>horizontal</em> version of it fixed
     * the altitude but pointed backwards: right after the snatch the mob is on top of the victim, so
     * "away from the victim" is the direction it just came from, and the escape read as reversing out.
     * Holding the dive's own heading is what makes it read as one continuous pass.
     */
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

    /** Unit vector in the XZ plane, falling back to +X for a degenerate input. */
    private static Vec3 horizontalDirection(double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return new Vec3(dx / length, 0.0D, dz / length);
    }

    /** Bails without paying out — used when the dive drags on too long to ever connect. */
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

    /** Empties a random non-empty hotbar slot into the returned stack, whole. {@link ItemStack#EMPTY} if none. */
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
