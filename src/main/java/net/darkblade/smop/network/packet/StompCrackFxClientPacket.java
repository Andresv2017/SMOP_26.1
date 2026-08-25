package net.darkblade.smop.network.packet;

import net.darkblade.deluxelib.network.AbstractNetworkPacket;
import net.darkblade.deluxelib.network.ClientPacketContext;
import net.darkblade.deluxelib.network.ExtendedFriendlyByteBuf;
import net.darkblade.deluxelib.network.PacketSide;
import net.darkblade.deluxelib.network.Side;
import net.darkblade.smop.client.gt.GroundCrackFx;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

@PacketSide(side = Side.CLIENT)
public final class StompCrackFxClientPacket extends AbstractNetworkPacket<StompCrackFxClientPacket> {

    private int cx;
    private int cy;
    private int cz;
    private int radius;

    public StompCrackFxClientPacket() {}

    public StompCrackFxClientPacket(@NotNull BlockPos center, int radius) {
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.radius = radius;
    }

    @Override
    protected void read(@NotNull ExtendedFriendlyByteBuf buf) {
        this.cx = buf.readInt();
        this.cy = buf.readInt();
        this.cz = buf.readInt();
        this.radius = buf.readVarInt();
    }

    @Override
    protected void write(@NotNull ExtendedFriendlyByteBuf buf) {
        buf.writeInt(this.cx);
        buf.writeInt(this.cy);
        buf.writeInt(this.cz);
        buf.writeVarInt(this.radius);
    }

    @Override
    protected void executeClient(@NotNull ClientPacketContext context) {
        GroundCrackFx.stomp(new BlockPos(this.cx, this.cy, this.cz), this.radius);
    }
}
