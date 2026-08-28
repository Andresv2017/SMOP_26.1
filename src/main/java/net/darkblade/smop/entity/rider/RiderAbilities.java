package net.darkblade.smop.entity.rider;

import net.darkblade.smop.entity.RiderControllable;
import net.darkblade.smop.network.SMOPNetwork;
import net.darkblade.smop.network.packet.RiderAbilityStateClientPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * El único sitio que sabe cómo se empaqueta el estado de las habilidades de una montura.
 *
 * <p>Existe para que una montura futura no reimplemente el armado del paquete: declara sus
 * habilidades en {@link RiderControllable#riderAbilities()} y llama aquí en los dos momentos que
 * importan — cuando alguien toma el control, y cuando una habilidad se gasta.
 */
public final class RiderAbilities {

    /**
     * Manda al jinete el estado completo de las habilidades de su montura.
     *
     * <p>Estado completo y no delta a propósito: son dos entradas y unas decenas de bytes, y a cambio
     * desaparece de raíz la clase de bugs en la que el cliente acumula desfase porque se perdió un
     * incremento.
     */
    public static void sync(@NotNull Entity mount, @NotNull ServerPlayer rider) {
        if (!(mount instanceof RiderControllable controllable)) {
            return;
        }
        List<RiderAbility> abilities = controllable.riderAbilities();
        if (abilities.isEmpty()) {
            return;
        }
        SMOPNetwork.INSTANCE.sendToClient(rider, new RiderAbilityStateClientPacket(mount.getId(), abilities));
    }

    private RiderAbilities() {}
}
