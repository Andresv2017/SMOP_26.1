package net.darkblade.smop.entity.rider;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

/**
 * La recarga de una habilidad de montura, y nada más.
 *
 * <p>Antes esta clase también era su propia presentación: llevaba dentro un {@code ServerBossEvent}
 * y decidía a quién enseñárselo. Eso ataba la única forma de ver un cooldown a una barra de jefe en
 * lo alto de la pantalla — dos de ellas cuando Fear y Charge recargan a la vez. Ahora el estado se
 * manda al jinete con {@link RiderAbilities#sync} y lo dibuja el HUD del mod.
 */
public final class RiderAbility {

    private final String id;
    private final int cooldownTicks;
    private final int tint;

    private int remaining;

    /**
     * @param id            identidad estable en el paquete de estado; no es clave de traducción,
     *                      el HUD no escribe texto
     * @param cooldownTicks ticks de recarga tras un uso
     * @param tint          ARGB por el que el HUD multiplica el relleno. Es lo que distingue una
     *                      habilidad de otra en pantalla, ya que la barra no lleva nombre
     */
    public RiderAbility(@NotNull String id, int cooldownTicks, int tint) {
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must not be negative, got " + cooldownTicks);
        }
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.tint = tint;
    }

    public @NotNull String id() {
        return this.id;
    }

    public int cooldownTicks() {
        return this.cooldownTicks;
    }

    public int remaining() {
        return this.remaining;
    }

    public int tint() {
        return this.tint;
    }

    public boolean isReady() {
        return this.remaining <= 0;
    }

    /**
     * El servidor sigue siendo la única puerta: el cliente descuenta en local para que la barra vaya
     * suave, pero quien decide si una habilidad se puede usar es esto.
     *
     * <p>No envía el estado. Una habilidad no conoce a sus hermanas y el paquete las lleva todas, así
     * que quien reenvía es la montura tras ver que esto devolvió {@code true}.
     */
    public boolean tryUse() {
        if (!this.isReady()) {
            return false;
        }
        this.remaining = this.cooldownTicks;
        return true;
    }

    public void tick() {
        if (this.remaining > 0) {
            this.remaining--;
        }
    }

    public void save(@NotNull ValueOutput output, @NotNull String key) {
        output.putInt(key, this.remaining);
    }

    public void load(@NotNull ValueInput input, @NotNull String key) {
        this.remaining = Math.clamp(input.getIntOr(key, 0), 0, this.cooldownTicks);
    }
}
