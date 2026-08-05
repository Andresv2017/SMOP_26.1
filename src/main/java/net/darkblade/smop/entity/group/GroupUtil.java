package net.darkblade.smop.entity.group;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.List;

/** Leader election for {@link GroupType#PACK} groups. */
public final class GroupUtil {

    /** How far apart pack members can be and still count as the same pack. */
    private static final double PACK_RADIUS = 22.0D;

    /**
     * Picks a new leader when the current one is gone, and tells the whole pack about it.
     *
     * <p>Every member runs this, so the work is claimed by the lowest-entity-id member of the pack
     * and the rest return immediately — otherwise each member would elect its own leader from its
     * own slightly different neighbour list and the pack would splinter.
     */
    public static <T extends Mob & IGroupBehaviour & IHasLeader> void reassignLeaderIfNeeded(T self) {
        LivingEntity current = self.getGroupLeader();
        if (current != null && current.isAlive()) {
            return;
        }

        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) self.getClass();
        List<T> pack = self.level().getEntitiesOfClass(type,
                self.getBoundingBox().inflate(PACK_RADIUS),
                member -> member.isAlive() && member.getGroupType() == GroupType.PACK);

        T coordinator = pack.stream().min(Comparator.comparingInt(Entity::getId)).orElse(self);
        if (coordinator != self) {
            return;
        }

        if (pack.isEmpty()) {
            self.setGroupLeader(null);
            return;
        }
        T newLeader = pack.getFirst();
        for (T member : pack) {
            member.setGroupLeader(newLeader);
        }
    }

    private GroupUtil() {}
}
