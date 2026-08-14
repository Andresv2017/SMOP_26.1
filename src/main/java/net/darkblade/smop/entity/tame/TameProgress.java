package net.darkblade.smop.entity.tame;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The bookkeeping behind a feeding ritual: how many offerings have been taken, how many this
 * particular animal wants, and who ends up owning it when the count is met.
 *
 * <pre>{@code
 * private final TameProgress tameProgress = new TameProgress(this, 3, 4);
 *
 * if (this.tameProgress.feed(thrower)) {
 *     this.startAction(ANIM_TAMED);   // the mob's own celebration stays the mob's own
 * }
 * }</pre>
 *
 * <p><b>Only the counting is shared, and that is deliberate.</b> The mod has three taming rituals and
 * they differ entirely in <em>how the animal reaches the food</em> — the Kriftognathus walks to a
 * scrap thrown on the ground ({@code TameFeedGoal}), the Nirasmosaurus takes bait from the hand while
 * keeping its distance, and the Hell Hippo is a plain {@code mobInteract}, not a goal at all. Forcing
 * those three into one shape would be inventing an abstraction the cases do not ask for. What they
 * genuinely have in common is this: count attempts toward a target, keep the count across a reload,
 * and hand ownership to whoever closed it.
 *
 * <p><b>The target is rolled on the first feeding, not at spawn.</b> Otherwise every animal of a kind
 * would carry its number from the moment it existed, and a player who tamed two of them would notice
 * they cost the same. Rolling late also means an animal nobody ever feeds never picks one.
 *
 * <p><b>Persistence keys are {@code FeedProgress} and {@code FeedGoal}</b>, unchanged from when this
 * lived as two private fields inside {@code KriftognathusEntity}. Renaming them would silently reset
 * every half-tamed animal in an existing world back to zero.
 */
public final class TameProgress {

    private final TamableAnimal mob;
    private final int goalMin;
    private final int goalMax;

    /** Offerings taken so far. Not synced — the rituals that read it are all server-side. */
    private int progress;

    /** Rolled in [{@link #goalMin}, {@link #goalMax}] on the first feeding. 0 until then. */
    private int goal;

    /**
     * @param goalMin fewest feedings this species ever asks for
     * @param goalMax most it ever asks for; pass the same value as {@code goalMin} for a fixed cost
     */
    public TameProgress(@NotNull TamableAnimal mob, int goalMin, int goalMax) {
        if (goalMin < 1 || goalMax < goalMin) {
            throw new IllegalArgumentException(
                    "goalMin must be >= 1 and goalMax >= goalMin, got " + goalMin + ".." + goalMax);
        }
        this.mob = mob;
        this.goalMin = goalMin;
        this.goalMax = goalMax;
    }

    /**
     * Logs one feeding and reports whether it closed the ritual.
     *
     * <p>A feeding whose {@code feeder} is not a player still counts. That is not an oversight: a
     * scrap of meat can be thrown by a dispenser or dropped by something that has since died, and
     * losing the progress in that case would punish the player for the mod's own bookkeeping. What
     * such a feeding cannot do is finish the ritual — there would be nobody to become the owner — so
     * the count simply sits at or past the target until a player provides the one that lands.
     *
     * @param feeder whoever offered this one, if anybody
     * @return {@code true} exactly once, on the feeding that tamed the animal
     */
    public boolean feed(@Nullable Entity feeder) {
        if (this.progress == 0) {
            this.goal = this.goalMin + this.mob.getRandom().nextInt(this.goalMax - this.goalMin + 1);
        }
        this.progress++;
        if (this.progress < this.goal || !(feeder instanceof Player player)) {
            return false;
        }
        this.mob.tame(player);
        // Vanilla's taming hearts. Part of "who owns it now", so it belongs here; the mob's own
        // reaction — a clip, a sound — does not, and stays with the caller.
        this.mob.level().broadcastEntityEvent(this.mob, (byte) 7);
        return true;
    }

    /** Offerings taken so far. */
    public int progress() {
        return this.progress;
    }

    /** How many this animal wants. Meaningless until the first {@link #feed} call has rolled it. */
    public int goal() {
        return this.goal;
    }

    /** Whether anything has been offered yet — i.e. whether {@link #goal()} means anything. */
    public boolean hasStarted() {
        return this.progress > 0;
    }

    public void save(@NotNull ValueOutput output) {
        output.putInt("FeedProgress", this.progress);
        output.putInt("FeedGoal", this.goal);
    }

    public void load(@NotNull ValueInput input) {
        this.progress = input.getIntOr("FeedProgress", 0);
        this.goal = input.getIntOr("FeedGoal", 0);
    }
}
