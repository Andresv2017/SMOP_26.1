package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.group.GroupType;
import net.darkblade.smop.entity.group.GroupUtil;
import net.darkblade.smop.entity.group.IGroupBehaviour;
import net.darkblade.smop.entity.group.IHasLeader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Keeps a {@link GroupType#PACK} member near its leader, electing a new one if the leader is gone. */
public class FollowGroupLeaderGoal<T extends Mob & IGroupBehaviour & IHasLeader> extends Goal {

    private final T follower;
    private final double speedModifier;
    private final float minDistance;
    private final float maxDistance;

    private LivingEntity leader;
    private int repathCooldown;

    public FollowGroupLeaderGoal(T follower, double speedModifier, float minDistance, float maxDistance) {
        this.follower = follower;
        this.speedModifier = speedModifier;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.follower.getGroupType() != GroupType.PACK || this.follower.getTarget() != null) {
            return false;
        }

        this.leader = this.follower.getGroupLeader();
        if (this.leader == null || this.leader.isRemoved()) {
            GroupUtil.reassignLeaderIfNeeded(this.follower);
            this.leader = this.follower.getGroupLeader();
        }
        return this.leader != null && this.follower.distanceTo(this.leader) > this.maxDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return this.leader != null
                && !this.leader.isRemoved()
                && this.follower.distanceTo(this.leader) > this.minDistance;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.leader == null || --this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = 10;
        this.follower.getNavigation().moveTo(this.leader, this.speedModifier);
    }
}
