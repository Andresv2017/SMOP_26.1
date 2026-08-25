package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Sleeping, as an activity the goal selector arbitrates.
 *
 * <p>Holds MOVE, LOOK and JUMP for the whole cycle, so every other movement goal is preempted by
 * the selector instead of each one having to ask "am I asleep?" — the same shape vanilla's fox uses.
 * Register it above the locomotion and combat goals (priority 1 is the usual spot, leaving 0 for
 * {@code FloatGoal} so a sleeping mob still floats instead of drowning).
 *
 * <p><b>It releases itself.</b> A threat walking up, dawn breaking, or a wake request from
 * {@code hurt()} moves the cycle into its exit phases; once those finish the goal stops and the
 * combat goals take over. That is why sitting at a high priority is safe — sleep never deadlocks the
 * mob out of reacting.
 *
 * <p>Whether the mob <em>wants</em> to sleep is not decided here: {@link SleepUrge} keeps that clock
 * on the entity, because a goal's {@code canUse()} is skipped entirely while its flags are held by
 * something else.
 *
 * <p><b>The cycle's shape comes from the mob.</b> This walks {@link SleepPhase} in order and skips
 * any phase the mob has no clip for, so the same goal runs the Tangoftero's three phases and the
 * Krifto's six without being told which it is dealing with.
 */
public class SleepGoal<T extends Mob & ISleepingEntity> extends Goal {

    /** Radius scanned for entities that should keep this mob awake. */
    private static final double THREAT_RADIUS = 4.0D;

    /** Threat sweeps are throttled — one box query per sleeping mob per tick adds up. */
    private static final int THREAT_SCAN_INTERVAL = 10;

    /** Descending into sleep, in order. */
    private static final SleepPhase[] ENTRY = {
            SleepPhase.SITTING_DOWN, SleepPhase.SITTING, SleepPhase.PREPARING_SLEEP, SleepPhase.SLEEPING
    };

    /**
     * Coming back out at leisure: wake to sitting, sit a few seconds, then rise. The pause is the
     * whole point — a creature that wakes on its own does not spring straight to its feet.
     */
    private static final SleepPhase[] EXIT_CALM = {
            SleepPhase.AWAKENING, SleepPhase.SITTING, SleepPhase.STANDING_UP
    };

    /** Same, minus the pause: for a mob that was startled awake and has somewhere else to be. */
    private static final SleepPhase[] EXIT_STARTLED = {
            SleepPhase.AWAKENING, SleepPhase.STANDING_UP
    };

    private final T mob;
    private final SleepUrge urge;

    private int phaseTimer;
    private int threatScanCooldown;
    /** True once the mob is on its way out, so {@link #nextAfter} reads {@link #exitPath}. */
    private boolean leaving;
    /** Which way out is being walked. Chosen once, in {@link #beginLeaving}. */
    private SleepPhase[] exitPath = EXIT_CALM;
    /**
     * Whether the wake came from {@link SleepUrge#requestWake()} — which has exactly one caller,
     * {@code SMOPAnimal#hurtServer}. So this means "was hit", and it is why a struck mob skips the
     * sit-and-blink on the way up.
     */
    private boolean startled;
    /** Ticks spent in {@link SleepPhase#SLEEPING}, counted so the wake lands on the loop's seam. */
    private int sleptTicks;
    /**
     * A wake that has been decided but not yet acted on, because the {@code sleep} loop is mid-cycle.
     * Latched rather than re-asked each tick: {@link #shouldWakeUp()} consumes the wake request and
     * resets the threat-scan cooldown, so asking twice would lose the very "was hit" signal that
     * {@link #startled} is read from.
     */
    private boolean wakePending;

    public SleepGoal(T mob, SleepUrge urge) {
        this.mob = mob;
        this.urge = urge;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isInSleepCycle() || !this.urge.wantsToSleep()) {
            return false;
        }
        this.urge.consumeWakeRequest();
        // A forced sleep ignores who is standing over the mob — see SleepUrge#forceSleep. For the
        // Hell Hippo the player IS the threat that would block it, and also the one holding the
        // potion that caused it.
        return this.urge.isForced() || this.findThreats().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isInSleepCycle();
    }

    /**
     * Every tick, not every other one — and for this goal that is a correctness requirement, not a
     * smoothness one.
     *
     * <p>{@code Mob#serverAiStep} only runs the full goal selector on alternate ticks and hands the
     * rest to {@code tickRunningGoals(false)}, which skips any goal that does not ask for this. So
     * without it {@link #phaseTimer} counts one goal-tick per two game ticks, and <b>every phase runs
     * for exactly twice its clip's length</b>. The clip finishes halfway through its own phase and
     * whatever sits below it in the blend — the standing idle — shows for the remainder, which reads
     * as the mob popping upright mid-ceremony and then sitting back down.
     *
     * <p>It stayed hidden while the declared phase lengths were longer than the clips: the mob was
     * held on a last frame either way. Matching them exposed it.
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.leaving = false;
        this.startled = false;
        this.threatScanCooldown = 0;
        this.enter(this.firstFrom(ENTRY, 0));
    }

    @Override
    public void tick() {
        this.mob.getNavigation().stop();

        // Sleeping is the one phase with no timer: it lasts until something ends it.
        if (this.mob.sleepPhase() == SleepPhase.SLEEPING) {
            this.sleptTicks++;
            if (!this.wakePending && this.shouldWakeUp()) {
                this.wakePending = true;
            }
            if (this.wakePending && this.atSleepLoopSeam()) {
                this.beginLeaving();
            }
            return;
        }

        // On the way down, a threat cuts the descent short — no point finishing a yawn to then get up.
        if (!this.leaving && this.shouldWakeUp()) {
            this.beginLeaving();
            return;
        }

        // The pause on the way up is a courtesy, not a commitment: a blow landing during it cuts
        // straight to standing. Same rule as choosing EXIT_STARTLED in the first place — being hit
        // never earns a mob a moment to sit and come round — applied to a hit that arrives late.
        if (this.leaving && this.mob.sleepPhase() == SleepPhase.SITTING && this.urge.consumeWakeRequest()) {
            this.startled = true;
            this.exitPath = EXIT_STARTLED;
            this.enter(this.firstFrom(EXIT_STARTLED, 1));
            return;
        }

        if (--this.phaseTimer > 0) {
            return;
        }
        this.enter(this.nextAfter(this.mob.sleepPhase()));
    }

    /**
     * Whether the {@code sleep} loop is at the frame it started on, which is the only frame the
     * wake may begin from.
     *
     * <p><b>Same rule that whole-loop sitting already follows, and for the same reported symptom.</b>
     * Sleeping ends whenever dawn or a threat says so, so without this the loop is cut wherever it
     * happened to be and {@code awakening} is handed that pose instead of the one it was authored
     * from. Measured on the Grand Tyrant, a mid-cycle cut hands over a head 15.0° away from where
     * {@code awakening} starts, arms 14.2° and {@code tail1} 12.5°; on the seam the same numbers are
     * 10.1°, 0° and 0°. The blend then has to swallow the difference inside its own ramp, which does
     * not merely soften it — it plays the incoming clip through a rising weight, so the pose lurches
     * to well over the authored speed halfway through. Cutting on the seam is what stops that.
     *
     * <p>The wait is bounded by one cycle of the clip — 4.4 s on the Grand Tyrant — and only ever
     * delays the <em>start</em> of a leisurely wake. A mob that was struck leaves at once:
     * {@link #startled} is the "was hit" signal, and nothing about being hit deserves to wait for a
     * breath to finish. A species with no {@code sleep} clip has no loop to align to and is likewise
     * never held.
     */
    private boolean atSleepLoopSeam() {
        if (this.startled) {
            return true;
        }
        int loopTicks = this.mob.sleepPhaseDuration(SleepPhase.SLEEPING);
        return loopTicks <= 0 || this.sleptTicks % loopTicks == 0;
    }

    /**
     * Moves into {@code phase}, skipping any the mob has no clip for, and ends the cycle on
     * {@link SleepPhase#NONE}.
     */
    private void enter(SleepPhase phase) {
        if (phase == SleepPhase.NONE) {
            this.mob.setSleepPhase(SleepPhase.NONE);
            return;
        }
        if (phase == SleepPhase.SLEEPING) {
            // The clip starts on this very tick (onSleepPhaseBegin, below), so counting from zero
            // here is what makes sleptTicks and the loop's own cycle the same clock.
            this.sleptTicks = 0;
            this.wakePending = false;
        }
        this.mob.setSleepPhase(phase);
        this.phaseTimer = this.mob.sleepPhaseDuration(phase);
        this.mob.onSleepPhaseBegin(phase);
    }

    /**
     * Leaves the cycle from wherever it currently is, by whichever of the two ways out fits.
     *
     * <p>Two independent questions, and they compose:
     *
     * <ul>
     *   <li><b>Was it actually asleep?</b> If not — interrupted while still sitting down, or partway
     *       into lying down — {@link SleepPhase#AWAKENING} is skipped. That clip is the transition
     *       from lying to sitting, and a mob that never lay down has nothing to wake from; playing it
     *       would animate a sleep that did not happen.</li>
     *   <li><b>Was it struck?</b> {@link #startled} drops the sitting pause from the way out. Waking
     *       up on your own earns a moment to sit and come round; being hit does not.</li>
     * </ul>
     */
    private void beginLeaving() {
        this.leaving = true;
        boolean wasAsleep = this.mob.sleepPhase() == SleepPhase.SLEEPING;
        this.exitPath = this.startled ? EXIT_STARTLED : EXIT_CALM;
        // Index 1 is STANDING_UP on the startled path and SITTING on the calm one, so a mob that was
        // never asleep takes the startled path regardless — it has no wake to animate and no sleep to
        // come round from.
        if (!wasAsleep) {
            this.exitPath = EXIT_STARTLED;
        }
        this.enter(this.firstFrom(this.exitPath, wasAsleep ? 0 : 1));
    }

    /**
     * The phase after {@code current} in whichever list is being walked.
     *
     * <p>{@link SleepPhase#SITTING} appears in both lists, which is exactly why {@link #leaving} has
     * to pick the list rather than the phase implying it: sitting on the way down is followed by
     * lying down, and sitting on the way up by standing.
     */
    private SleepPhase nextAfter(SleepPhase current) {
        SleepPhase[] list = this.leaving ? this.exitPath : ENTRY;
        for (int i = 0; i < list.length; i++) {
            if (list[i] == current) {
                return this.firstFrom(list, i + 1);
            }
        }
        return SleepPhase.NONE;
    }

    /**
     * First phase at or after {@code from} that this mob actually has, or {@link SleepPhase#NONE}.
     *
     * <p>A phase is present when {@link ISleepingEntity#sleepPhaseDuration} reports a length for it;
     * 0 means the clip was never registered. Sleeping is exempt — it has no timer, and a species with
     * no {@code sleep} clip still sleeps, just without an animation.
     */
    private SleepPhase firstFrom(SleepPhase[] list, int from) {
        for (int i = from; i < list.length; i++) {
            SleepPhase phase = list[i];
            if (!phase.isSkippable() || this.mob.sleepPhaseDuration(phase) > 0) {
                return phase;
            }
        }
        return SleepPhase.NONE;
    }

    private boolean shouldWakeUp() {
        // Checked first and on its own, rather than folded into the || below, because consuming the
        // request is also how the exit path is chosen: this is the "was hit" signal.
        if (this.urge.consumeWakeRequest()) {
            this.startled = true;
            return true;
        }
        // A forced sleep ends only when whoever forced it says so, which the wake request above is.
        // Neither daylight nor a bystander gets a vote — the mob was put under, it did not doze off.
        if (this.urge.isForced()) {
            return false;
        }
        if (!this.urge.isNight()) {
            return true;
        }
        if (--this.threatScanCooldown > 0) {
            return false;
        }
        this.threatScanCooldown = THREAT_SCAN_INTERVAL;
        return !this.findThreats().isEmpty();
    }

    @Override
    public void stop() {
        // Also covers being preempted outright by a higher-priority goal, which skips the exit
        // phases — better a snap than a mob left stuck half asleep.
        this.mob.setSleepPhase(SleepPhase.NONE);
        this.leaving = false;
        this.startled = false;
        this.wakePending = false;
        this.urge.noteWokeUp();
    }

    /**
     * Nearby entities worth waking for. A {@link Player} counts if they are playing for real —
     * neither creative nor spectator — unless the mob opts out via {@link ISleepAwareness};
     * otherwise {@link ISleepThreatEvaluator} decides, falling back to the mob's
     * {@link ISleepingEntity#getInterruptingEntityTypes()} set.
     */
    private List<LivingEntity> findThreats() {
        return this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(THREAT_RADIUS),
                this::isThreat);
    }

    private boolean isThreat(LivingEntity nearby) {
        if (nearby == this.mob || !nearby.isAlive()) {
            return false;
        }
        // Only a player who can actually be hurt is worth waking for. Creative and spectator players
        // are excluded together, which is the same test targeting already uses across the mod — and
        // it has to stay the same test, or a mob would ignore you while asleep and then hunt you the
        // moment it woke. Adventure counts as survival here for exactly that reason.
        //
        // Spectator was already excluded; creative was not, so anyone flying past in creative woke
        // every sleeping mob in the mod, which made the sleep cycle impossible to watch while
        // building it.
        if (nearby instanceof Player player && !player.isSpectator() && !player.isCreative()) {
            return !(this.mob instanceof ISleepAwareness aware) || aware.shouldWakeOnPlayerProximity();
        }
        if (this.mob instanceof ISleepThreatEvaluator evaluator) {
            return evaluator.shouldInterruptSleepDueTo(nearby);
        }
        return this.mob.getInterruptingEntityTypes().contains(nearby.getType());
    }
}
