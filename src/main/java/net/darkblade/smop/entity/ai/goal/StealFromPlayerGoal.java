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
 * radius, dives in, snatches a random hotbar slot with the {@code steal} clip, and flees with it
 * before dropping it out of easy reach.
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
    /** Safety net if the victim somehow keeps pace forever. */
    private static final int FLEE_GIVE_UP_TICKS = 400;

    private final KriftognathusEntity mob;
    private final OrbitFlightController controller = new OrbitFlightController(0.05D, 0.4D, 0.6D, 12.0F);

    private enum Phase { ORBIT, DIVE, SNATCH, FLEE }

    @Nullable
    private Player victim;
    private Phase phase = Phase.ORBIT;
    private float orbitAngle;
    private int phaseTicks;
    private int cooldownUntilTick;

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

    @Override
    public void start() {
        this.phase = Phase.ORBIT;
        this.orbitAngle = 0.0F;
        this.phaseTicks = 0;
    }

    @Override
    public void stop() {
        if (this.mob.isPerforming(KriftognathusEntity.ANIM_STEAL)) {
            this.mob.stopAction();
        }
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
            case SNATCH -> this.tickSnatch();
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
        }
    }

    private void tickDive(Player victim) {
        this.mob.getLookControl().setLookAt(victim, 30.0F, this.mob.getMaxHeadXRot());
        this.controller.step(this.mob, victim.position().add(0.0D, 0.6D, 0.0D), null);

        if (this.mob.distanceToSqr(victim) <= DIVE_ARRIVAL_SQ) {
            this.performSnatch(victim);
            this.phase = Phase.SNATCH;
            this.phaseTicks = 0;
            return;
        }
        if (++this.phaseTicks >= DIVE_GIVE_UP_TICKS) {
            this.abortHeist();
        }
    }

    /**
     * Grabbed the instant the dive lands rather than on a mid-clip frame timer: {@code steal} is
     * short (0.65 s) and front-loaded — the head dip and jaw snap are already most of the way through
     * by the time a frame callback would fire, so waiting for one would visibly lag the grab behind
     * the beak reaching the player.
     */
    private void performSnatch(Player victim) {
        ItemStack taken = takeRandomHotbarStack(victim);
        this.mob.setStolenItem(taken);
        this.mob.startAction(KriftognathusEntity.ANIM_STEAL);
    }

    private void tickSnatch() {
        this.mob.getNavigation().stop();
        if (!this.mob.isPerforming(KriftognathusEntity.ANIM_STEAL)) {
            this.phase = Phase.FLEE;
            this.phaseTicks = 0;
        }
    }

    private void tickFlee(Player victim) {
        Vec3 away = this.mob.position().subtract(victim.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 fleeTarget = this.mob.position().add(away.normalize().scale(FLEE_DISTANCE));
        this.mob.getLookControl().setLookAt(
                this.mob.getX() + away.x, this.mob.getY(), this.mob.getZ() + away.z,
                20.0F, this.mob.getMaxHeadXRot());
        this.controller.step(this.mob, fleeTarget, null);

        boolean farEnough = this.mob.distanceToSqr(victim) >= FLEE_DISTANCE_SQ;
        if (farEnough || ++this.phaseTicks >= FLEE_GIVE_UP_TICKS) {
            this.dropStolenItem();
            this.victim = null;
            this.cooldownUntilTick = this.mob.tickCount + STEAL_COOLDOWN_TICKS;
        }
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
