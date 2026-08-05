package net.darkblade.smop.entity.ai.goal.egg;

import net.darkblade.smop.tag.SMOPTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Nest guarding: keep station near an egg, optionally attack whatever comes close, and react when
 * the nest is destroyed.
 *
 * <p>Subclasses decide <em>which</em> egg is being guarded — the one this mob laid
 * ({@link ProtectOwnEggGoal}) or the nearest one it can find ({@link ProtectNearestEggGoal}).
 *
 * <p>Tamed mobs skip this entirely: a pet following its owner around should not anchor itself to a
 * nest it happens to walk past.
 */
public abstract class ProtectEggBaseGoal extends Goal {

    /** How the mother answers her nest being destroyed. */
    public enum EggBreakReaction {
        /** Bolt away from the nest site. */
        FLEE,
        /** Carry on as if nothing happened. */
        IGNORE
    }

    /** Gap between threat sweeps, and between re-targeting after picking a victim. */
    private static final int THREAT_SCAN_INTERVAL = 20;
    private static final int RETARGET_COOLDOWN = 40;

    protected final Animal mob;
    protected final int stayNearEggRadius;
    protected final int defenseRadius;
    protected final boolean attackOnApproach;
    protected final boolean attackOnBreak;
    protected final Predicate<LivingEntity> enemySelector;
    protected final EggBreakReaction eggBreakReaction;

    @Nullable
    protected BlockPos targetEggPos;

    private int threatScanCooldown;
    private int retargetCooldown;

    protected ProtectEggBaseGoal(Animal mob, int stayNearEggRadius, int defenseRadius,
                                 boolean attackOnApproach, boolean attackOnBreak,
                                 Predicate<LivingEntity> enemySelector, EggBreakReaction eggBreakReaction) {
        this.mob = mob;
        this.stayNearEggRadius = stayNearEggRadius;
        this.defenseRadius = defenseRadius;
        this.attackOnApproach = attackOnApproach;
        this.attackOnBreak = attackOnBreak;
        this.enemySelector = enemySelector;
        this.eggBreakReaction = eggBreakReaction;
    }

    @Nullable
    protected abstract BlockPos findTargetEgg();

    @Override
    public boolean canUse() {
        if (this.mob instanceof TamableAnimal tamable && (tamable.isTame() || tamable.isOrderedToSit())) {
            return false;
        }
        this.targetEggPos = this.findTargetEgg();
        return this.targetEggPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetEggPos == null || !this.mob.level().isLoaded(this.targetEggPos)) {
            return false;
        }
        return this.mob.level().getBlockState(this.targetEggPos).is(SMOPTags.Blocks.EGG_BLOCKS);
    }

    @Override
    public void stop() {
        this.targetEggPos = null;
    }

    @Override
    public void tick() {
        if (this.targetEggPos == null) {
            return;
        }
        if (this.retargetCooldown > 0) {
            this.retargetCooldown--;
        }

        // Chasing something: let it run, but do not be lured beyond the nest's defence radius.
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            if (this.mob.distanceToSqr(target) > this.defenseRadius * this.defenseRadius) {
                this.mob.setTarget(null);
            }
            return;
        }

        Vec3 nest = Vec3.atCenterOf(this.targetEggPos);
        if (this.mob.distanceToSqr(nest) > this.stayNearEggRadius * this.stayNearEggRadius) {
            this.mob.getNavigation().moveTo(nest.x, nest.y, nest.z, 1.1D);
            return;
        }

        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(nest);
        if (--this.threatScanCooldown <= 0) {
            this.threatScanCooldown = THREAT_SCAN_INTERVAL;
            if (this.attackOnApproach) {
                this.targetNearestThreat(this.targetEggPos);
            }
        }
    }

    /** Called from the block-break event for every guarding mob in range. */
    public void notifyEggBroken(BlockPos brokenPos) {
        if (this.targetEggPos == null || !this.targetEggPos.equals(brokenPos)) {
            return;
        }

        if (this.eggBreakReaction == EggBreakReaction.FLEE) {
            Vec3 escape = DefaultRandomPos.getPosAway(this.mob, 16, 7, Vec3.atCenterOf(brokenPos));
            if (escape != null) {
                this.mob.getNavigation().moveTo(escape.x, escape.y, escape.z, 1.2D);
            }
        }
        if (this.attackOnBreak) {
            this.targetNearestThreat(brokenPos);
        }
    }

    private void targetNearestThreat(BlockPos around) {
        if (this.retargetCooldown > 0) {
            return;
        }
        List<LivingEntity> threats = this.mob.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(around).inflate(this.defenseRadius),
                candidate -> candidate != this.mob && this.enemySelector.test(candidate));
        if (threats.isEmpty()) {
            return;
        }
        this.mob.setTarget(threats.getFirst());
        this.retargetCooldown = RETARGET_COOLDOWN;
    }
}
