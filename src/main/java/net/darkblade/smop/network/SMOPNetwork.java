package net.darkblade.smop.network;

import net.darkblade.deluxelib.network.NetworkCreator;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.network.packet.RiderActionServerPacket;
import net.darkblade.smop.network.packet.StompCrackFxClientPacket;
import net.neoforged.bus.api.IEventBus;

public final class SMOPNetwork {

    public static final NetworkCreator INSTANCE = NetworkCreator.create(SMOP.MOD_ID, 1);

    public static void register(IEventBus modEventBus) {
        INSTANCE.regPacket(RiderActionServerPacket.class);
        INSTANCE.regPacket(StompCrackFxClientPacket.class);
        modEventBus.addListener(INSTANCE::register); // RegisterPayloadHandlersEvent
    }

    private SMOPNetwork() {}
}
