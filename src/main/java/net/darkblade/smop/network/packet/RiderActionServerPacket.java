package net.darkblade.smop.network.packet;

import net.darkblade.deluxelib.network.AbstractNetworkPacket;
import net.darkblade.deluxelib.network.ExtendedFriendlyByteBuf;
import net.darkblade.deluxelib.network.PacketSide;
import net.darkblade.deluxelib.network.ServerPacketContext;
import net.darkblade.deluxelib.network.Side;
import net.darkblade.smop.entity.RiderControllable;
import org.jetbrains.annotations.NotNull;

/**
 * Client → server: the rider pressed one of the mount keybinds (see {@link RiderControllable}).
 *
 * <p>No entity id travels with it — the action always targets whatever the sender is currently
 * riding, which the server reads authoritatively from {@code player.getVehicle()}. That also makes
 * the packet unspoofable: a client cannot name a mount it is not on.
 */
@PacketSide(side = Side.SERVER)
public final class RiderActionServerPacket extends AbstractNetworkPacket<RiderActionServerPacket> {

    private RiderControllable.RiderAction action;

    /** Required by the packet decoder — fields are filled in by {@link #read}. */
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
