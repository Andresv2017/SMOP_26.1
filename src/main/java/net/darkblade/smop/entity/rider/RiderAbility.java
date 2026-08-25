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

public final class RiderAbility {

    private final Mob mount;
    private final Component title;
    private final int cooldownTicks;
    private final ServerBossEvent bar;

    private int remaining;

    @Nullable
    private ServerPlayer watcher;

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

    public boolean tryUse() {
        if (!this.isReady()) {
            return false;
        }
        this.remaining = this.cooldownTicks;
        return true;
    }

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

    public void hide() {
        this.bar.removeAllPlayers();
        this.watcher = null;
    }

    public void save(@NotNull ValueOutput output, @NotNull String key) {
        output.putInt(key, this.remaining);
    }

    public void load(@NotNull ValueInput input, @NotNull String key) {
        this.remaining = Math.clamp(input.getIntOr(key, 0), 0, this.cooldownTicks);
    }

    public static @Nullable Player controllerOf(@NotNull Mob mount) {
        Entity controller = mount.getControllingPassenger();
        return controller instanceof Player player ? player : null;
    }
}
