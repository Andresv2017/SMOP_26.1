package net.darkblade.smop.entity.group;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Group membership and how the group reacts when one of its own is attacked. */
public interface IGroupBehaviour {

    GroupType getGroupType();

    GroupReaction getGroupReaction();

    default boolean isInGroup() {
        return this.getGroupType() != GroupType.NONE;
    }

    /** Same species, same group type, within {@code radius} — excluding {@code self}. */
    default List<LivingEntity> getNearbyGroupMembers(LivingEntity self, double radius) {
        return self.level().getEntitiesOfClass(LivingEntity.class,
                self.getBoundingBox().inflate(radius),
                other -> other != self
                        && other.getType() == self.getType()
                        && other instanceof IGroupBehaviour group
                        && group.getGroupType() == this.getGroupType());
    }

    default boolean shouldFleeAsGroup() {
        return this.getGroupReaction() == GroupReaction.EVASIVE;
    }

    default boolean shouldDefendAsGroup() {
        return this.getGroupReaction() == GroupReaction.DEFENSIVE;
    }

    default boolean hasNeutralGroupResponse() {
        return this.getGroupReaction() == GroupReaction.NEUTRAL;
    }
}
