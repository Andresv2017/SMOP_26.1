package net.darkblade.smop.entity.sleep;

import org.jetbrains.annotations.Nullable;

/**
 * One step of the sleep cycle, and the clip that plays for it.
 *
 * <p>The cycle runs this list in order, and <b>a phase exists for a mob only if that mob registered
 * its clip</b>. Nothing is declared anywhere else: author the clips and the cycle assembles itself.
 * That is the rule {@code SMOPAnimal} already applied to phase durations ("0 — no clip registered —
 * disables the phase"), generalised to the phases themselves, so there is no second place where the
 * same fact could be written down differently.
 *
 * <p>A mob with only {@code preparing_sleep}/{@code sleep}/{@code awakening} — the Tangoftero — runs
 * the three-phase cycle it always did. One that also authors {@code sitting}, {@code sit} and
 * {@code standing_up} — the Krifto — sits down first and stands back up afterwards, with no code
 * change on either side.
 *
 * <p>The three optional phases are independent of each other; there are no invalid combinations to
 * detect. {@code sitting} without {@code sit} is a mob that crouches and lies straight down;
 * {@code sit} without {@code standing_up} is one that rises in a snap at the end. Both are animation
 * decisions, not errors.
 */
public enum SleepPhase {

    /** Not in the cycle at all. */
    NONE(null),

    /** Lowering into the sitting pose. */
    SITTING_DOWN("sitting"),

    /** Sitting, held for {@code ISleepingEntity#getSittingDuration()} before lying down. */
    SITTING("sit"),

    /** Settling from sitting (or from standing, on a three-phase mob) into sleep. */
    PREPARING_SLEEP("preparing_sleep"),

    /** Asleep. Held until something wakes the mob. */
    SLEEPING("sleep"),

    /** Waking: back up to the sitting pose. */
    AWAKENING("awakening"),

    /** Rising from sitting back to standing. */
    STANDING_UP("standing_up");

    private static final SleepPhase[] BY_ID = values();

    @Nullable
    private final String clipName;

    SleepPhase(@Nullable String clipName) {
        this.clipName = clipName;
    }

    /** Name the mob registers this phase's clip under, or {@code null} for {@link #NONE}. */
    @Nullable
    public String clipName() {
        return this.clipName;
    }

    /**
     * Whether the cycle may skip this phase when its clip is missing.
     *
     * <p>Everything except {@link #SLEEPING} may: the rest are transitions and holds that only exist
     * to be watched. Sleeping is the cycle's entire point — a species with no {@code sleep} clip still
     * sleeps, it just does it without an animation, exactly as it does today.
     */
    public boolean isSkippable() {
        return this != SLEEPING;
    }

    /** Safe lookup for the synced ordinal. Out-of-range falls back to {@link #NONE}. */
    public static SleepPhase byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }
}
