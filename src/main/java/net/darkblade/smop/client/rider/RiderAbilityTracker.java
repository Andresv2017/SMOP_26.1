package net.darkblade.smop.client.rider;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.network.packet.RiderAbilityStateClientPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * El estado de recarga que el HUD dibuja, descontado en local entre paquete y paquete.
 *
 * <p>Estático y de una sola montura porque el jugador local solo monta una cosa a la vez.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class RiderAbilityTracker {

    private static final int NO_MOUNT = -1;

    // Cuánto se le espera al paquete de pasajeros antes de dar el estado por huérfano. El servidor
    // tickea las entidades ANTES de la fase de seguimiento que manda los pasajeros, así que al entrar
    // al mundo montado el estado de habilidades puede llegar un tick por delante de la noticia de que
    // vas montado. Sin esta cortesía el tracker lo tiraría, y no habría un segundo envío.
    private static final int UNMATCHED_GRACE_TICKS = 40;

    private static int mountId = NO_MOUNT;
    private static List<Entry> entries = List.of();
    private static int unmatchedTicks;

    public static final class Entry {

        private final String id;
        private final int total;
        private final int tint;

        private int remaining;

        private Entry(RiderAbilityStateClientPacket.Entry source) {
            this.id = source.id();
            this.total = source.total();
            this.tint = source.tint();
            this.remaining = source.remaining();
        }

        public @NotNull String id() {
            return this.id;
        }

        public int tint() {
            return this.tint;
        }

        /**
         * Se llena mientras se recupera: una barra llena se lee como "lista", no como "gastada".
         */
        public float progress() {
            return this.total <= 0 ? 1.0F : 1.0F - (float) this.remaining / this.total;
        }
    }

    public static void accept(@NotNull RiderAbilityStateClientPacket packet) {
        List<Entry> received = new ArrayList<>(packet.entries().size());
        for (RiderAbilityStateClientPacket.Entry entry : packet.entries()) {
            received.add(new Entry(entry));
        }
        mountId = packet.mountId();
        entries = List.copyOf(received);
        unmatchedTicks = 0;
    }

    /**
     * Lo que el HUD debe dibujar ahora mismo: vacío salvo que el jugador vaya montado justo en la
     * montura del último paquete. Se comprueba aquí, en el momento de pintar, y no se confía en una
     * bandera del tick anterior.
     */
    public static @NotNull List<Entry> entries() {
        return isMounted() ? entries : List.of();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.@NotNull Post event) {
        if (mountId == NO_MOUNT) {
            return;
        }
        // Comprobar el vehículo cada tick es lo que hace innecesario un "ocultar" explícito:
        // desmontarse, morir, cambiar de mundo o desconectar dejan de coincidir con la montura del
        // último paquete, y el estado se cae solo.
        if (!isMounted()) {
            if (++unmatchedTicks > UNMATCHED_GRACE_TICKS) {
                clear();
            }
            return;
        }
        unmatchedTicks = 0;
        for (Entry entry : entries) {
            if (entry.remaining > 0) {
                entry.remaining--;
            }
        }
    }

    private static boolean isMounted() {
        LocalPlayer player = Minecraft.getInstance().player;
        @Nullable Entity vehicle = player == null ? null : player.getVehicle();
        return vehicle != null && vehicle.getId() == mountId;
    }

    private static void clear() {
        mountId = NO_MOUNT;
        entries = List.of();
        unmatchedTicks = 0;
    }

    private RiderAbilityTracker() {}
}
