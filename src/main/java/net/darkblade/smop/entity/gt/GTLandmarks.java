package net.darkblade.smop.entity.gt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.darkblade.smop.SMOP;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

// Where a Grand Tyrant has already taken a place, written to the level's own save data.
//
// The obvious way to keep one to an area is to ask the level for nearby GTs, and it does not work
// here for two reasons that only show up in play. The first is that no GT is ever born with a level
// to ask: they all arrive during chunk generation, and WorldGenRegion#getEntities returns an empty
// list unconditionally. The second is subtler and is what a first attempt got wrong — deferring the
// question to the first tick does give you a real level, but a level only knows about LOADED
// entities, and two that generated three hundred blocks apart are never loaded at the same moment.
// Measured that way: three in one plains and three in one desert, all of them mutually invisible.
//
// A claim list has neither problem. It is consulted on the server thread, it survives the chunk
// being unloaded, and it does not care whether the neighbour it is protecting is in memory.
//
// Claims are never released, not even when the animal dies. Nothing would fill the gap anyway —
// CREATURE is saturated to the point that the periodic cycle has never once produced a GT — so a
// freed claim would only mean the next pass of chunk generation could drop a second one where you
// just killed the first.
public class GTLandmarks extends SavedData {

    public static final Codec<GTLandmarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf().optionalFieldOf("Claims", List.of()).forGetter(data -> data.claims)
    ).apply(instance, GTLandmarks::new));

    public static final SavedDataType<GTLandmarks> TYPE =
            new SavedDataType<>(SMOP.id("gt_landmarks"), GTLandmarks::new, CODEC);

    private final List<BlockPos> claims;

    public GTLandmarks() {
        this(List.of());
    }

    public GTLandmarks(List<BlockPos> claims) {
        this.claims = new ArrayList<>(claims);
    }

    public static GTLandmarks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // Horizontal distance only. This is a rule about the map, and a GT on a plateau is not a
    // different animal from the one in the valley below it.
    public boolean claim(BlockPos pos, int radius) {
        long limit = (long) radius * (long) radius;
        for (BlockPos claim : this.claims) {
            long dx = claim.getX() - pos.getX();
            long dz = claim.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= limit) {
                return false;
            }
        }
        this.claims.add(pos.immutable());
        this.setDirty();
        return true;
    }
}
