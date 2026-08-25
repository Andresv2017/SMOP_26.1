package net.darkblade.smop.network.packet;

import net.darkblade.deluxelib.network.AbstractNetworkPacket;
import net.darkblade.deluxelib.network.ExtendedFriendlyByteBuf;
import net.darkblade.deluxelib.network.PacketSide;
import net.darkblade.deluxelib.network.ServerPacketContext;
import net.darkblade.deluxelib.network.Side;
import net.darkblade.smop.entity.RiderControllable;
import org.jetbrains.annotations.NotNull;

@PacketSide(side = Side.SERVER)
public final class RiderActionServerPacket extends AbstractNetworkPacket<RiderActionServerPacket> {

    private RiderControllable.RiderAction action;

    public RiderActionServerPacket() {}

    public RiderActionServerPacket(RiderControllable.RiderAction action) {
        this.action = action;
    }

    @Override
    protected void read(@NotNull ExtendedFriendlyByteBuf buf) {
        this.action = buf.readEnum(RiderControllable.RiderAction.class);
    }

    @Override
    protected void write(@NotNull ExtendedFriendlyByteBuf buf) {
        buf.writeEnum(this.action);
    }

    @Override
    protected void executeServer(@NotNull ServerPacketContext context) {
        if (context.player == null) {
            return;
        }
        if (context.player.getVehicle() instanceof RiderControllable mount) {
            mount.onRiderAction(context.player, this.action);
        }
    }
}
