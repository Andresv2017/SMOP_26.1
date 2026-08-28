package net.darkblade.smop.network.packet;

import net.darkblade.deluxelib.network.AbstractNetworkPacket;
import net.darkblade.deluxelib.network.ClientPacketContext;
import net.darkblade.deluxelib.network.ExtendedFriendlyByteBuf;
import net.darkblade.deluxelib.network.PacketSide;
import net.darkblade.deluxelib.network.Side;
import net.darkblade.smop.client.rider.RiderAbilityTracker;
import net.darkblade.smop.entity.rider.RiderAbility;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor→jinete: el estado completo de las habilidades de la montura que va montando.
 *
 * <p>Solo viaja en dos momentos — al tomar el control y al gastar una habilidad. Entre medias el
 * cliente descuenta solo, así que esto no se manda por tick.
 */
@PacketSide(side = Side.CLIENT)
public final class RiderAbilityStateClientPacket extends AbstractNetworkPacket<RiderAbilityStateClientPacket> {

    public record Entry(String id, int remaining, int total, int tint) {}

    private int mountId;
    private List<Entry> entries = List.of();

    public RiderAbilityStateClientPacket() {}

    public RiderAbilityStateClientPacket(int mountId, @NotNull List<RiderAbility> abilities) {
        this.mountId = mountId;
        this.entries = abilities.stream()
                .map(ability -> new Entry(ability.id(), ability.remaining(), ability.cooldownTicks(), ability.tint()))
                .toList();
    }

    public int mountId() {
        return this.mountId;
    }

    public @NotNull List<Entry> entries() {
        return this.entries;
    }

    @Override
    protected void read(@NotNull ExtendedFriendlyByteBuf buf) {
        this.mountId = buf.readVarInt();
        int size = buf.readVarInt();
        List<Entry> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            read.add(new Entry(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));
        }
        this.entries = List.copyOf(read);
    }

    @Override
    protected void write(@NotNull ExtendedFriendlyByteBuf buf) {
        buf.writeVarInt(this.mountId);
        buf.writeVarInt(this.entries.size());
        for (Entry entry : this.entries) {
            buf.writeUtf(entry.id());
            buf.writeVarInt(entry.remaining());
            buf.writeVarInt(entry.total());
            buf.writeInt(entry.tint());
        }
    }

    @Override
    protected void executeClient(@NotNull ClientPacketContext context) {
        RiderAbilityTracker.accept(this);
    }
}
