package net.darkblade.smop.entity.rider;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One thing a rider can trigger on its mount, with a cooldown the rider can see.
 *
 * <pre>{@code
 * private final RiderAbility fear = new RiderAbility(this, "Fear", 200, BossBarColor.PURPLE);
 *
 * if (this.fear.tryUse()) {
 *     ...   // only runs when it was actually off cooldown
 * }
 * }</pre>
 *
 * <p><b>Reusable on purpose.</b> The Hell Hippo alone needs two of these — the intimidation pulse and
 * the mounted strike — and the Nirasmosaurus is rideable too. What they share is not the effect but
 * the bookkeeping around it: refuse while hot, count down, show the rider how long is left, and stop
 * showing anyone the moment they dismount.
 *
 * <p><b>The bar belongs to the rider, not the world.</b> A {@link ServerBossEvent} with exactly one
 * player added to it — whoever is currently in the saddle. That is the whole reason this is not just
 * an int: getting the bar to appear, follow the right player, and disappear on dismount is the fiddly
 * part, and doing it once here is better than twice per mount.
 *
 * <p>Server-side throughout. {@link #tick(Player)} is expected once per server tick from the mount,
 * with whoever is riding it or {@code null}.
 */
public final class RiderAbility {

    private final Mob mount;
    private final Component title;
    private final int cooldownTicks;
    private final ServerBossEvent bar;

    /** Ticks left before it can be used again. 0 means ready. */
    private int remaining;

    /** Who the bar is currently shown to, so it can be taken away from exactly them. */
    @Nullable
    private ServerPlayer watcher;

    /**
     * @param title         shown on the bar; also the persistence key prefix
     * @param cooldownTicks how long it stays hot after a use
     */
    public RiderAbility(@NotNull Mob mount, @NotNull String title, int cooldownTicks,
                        BossEvent.@NotNull BossBarColor color) {
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must not be negative, got " + cooldownTicks);
        }
        this.mount = mount;
        this.title = Component.literal(title);
        this.cooldownTicks = cooldownTicks;
        this.bar = new ServerBossEvent(UUID.randomUUID(), this.title, color, BossEvent.BossBarOverlay.PROGRESS);
        this.bar.setProgress(1.0F);
    }

    public boolean isReady() {
        return this.remaining <= 0;
    }

    /**
     * Spends the ability if it is ready.
     *
     * @return {@code true} when the caller should go ahead and do the thing — the cooldown has been
     *         started. {@code false} means it was still hot and nothing has changed.
     */
    public boolean tryUse() {
        if (!this.isReady()) {
            return false;
        }
        this.remaining = this.cooldownTicks;
        return true;
    }

    /**
     * Counts down and keeps the bar pointed at the right person.
     *
     * <p><b>The dismount case is why the rider is a parameter</b> rather than something read from the
     * mount: the bar has to be removed from a player who is no longer riding, and by the time the
     * mount notices, {@code getControllingPassenger()} already returns null. Passing it in each tick
     * means this can compare against who it showed it to last time.
     *
     * @param rider whoever is currently in the saddle, or {@code null} if nobody is
     */
    public void tick(@Nullable Player rider) {
        if (this.remaining > 0) {
            this.remaining--;
        }
        ServerPlayer viewer = this.remaining > 0 && rider instanceof ServerPlayer serverRider ? serverRider : null;
        if (viewer != this.watcher) {
            if (this.watcher != null) {
                this.bar.removePlayer(this.watcher);
            }
            if (viewer != null) {
                this.bar.addPlayer(viewer);
            }
            this.watcher = viewer;
        }
        if (viewer != null) {
            // Fills as it recovers, so a full bar reads as "ready" rather than "spent".
            this.bar.setProgress(1.0F - (float) this.remaining / this.cooldownTicks);
        }
    }

    /**
     * Takes the bar off everyone. Call when the mount dies or leaves the world — a boss bar outlives
     * its entity otherwise, and the player is left with a sliver of UI nothing can remove.
     */
    public void hide() {
        this.bar.removeAllPlayers();
        this.watcher = null;
    }

    /**
     * Persisted so quitting and rejoining is not a way to skip the wait.
     *
     * @param key distinguishes this ability from the others on the same mount
     */
    public void save(@NotNull ValueOutput output, @NotNull String key) {
        output.putInt(key, this.remaining);
    }

    public void load(@NotNull ValueInput input, @NotNull String key) {
        this.remaining = Math.clamp(input.getIntOr(key, 0), 0, this.cooldownTicks);
    }

    /** Convenience for the common shape: the mount's current controller, if it is a player. */
    public static @Nullable Player controllerOf(@NotNull Mob mount) {
        Entity controller = mount.getControllingPassenger();
        return controller instanceof Player player ? player : null;
    }
}
