package net.darkblade.smop.network;

import net.darkblade.deluxelib.network.NetworkCreator;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.network.packet.RiderActionServerPacket;
import net.darkblade.smop.network.packet.StompCrackFxClientPacket;
import net.neoforged.bus.api.IEventBus;

/**
 * SMOP's packet channel, built on DeluxeLib's {@link NetworkCreator}.
 *
 * <p>{@code NetworkCreator} wraps NeoForge's payload system behind a
 * {@code regPacket}/{@code sendToServer}/{@code sendToClient} shape.
 *
 * <p>Three of the four original packets are gone, absorbed by DeluxeLib:
 * <ul>
 *   <li>{@code ShakeCameraPacket} → {@code ScreenShake}/{@code ScreenShakes} (fBm-driven, stackable).</li>
 *   <li>{@code StompDustFXPacket} → {@code ParticleFx}.</li>
 *   <li>No flight-sync packet: {@code AbstractFlyingEntity} keeps flight state in a synced
 *       {@code EntityDataAccessor}, so clients already agree.</li>
 * </ul>
 */
public final class SMOPNetwork {

    public static final NetworkCreator INSTANCE = NetworkCreator.create(SMOP.MOD_ID, 1);

    public static void register(IEventBus modEventBus) {
        INSTANCE.regPacket(RiderActionServerPacket.class);
        INSTANCE.regPacket(StompCrackFxClientPacket.class);
        modEventBus.addListener(INSTANCE::register); // RegisterPayloadHandlersEvent
    }

    private SMOPNetwork() {}
}
