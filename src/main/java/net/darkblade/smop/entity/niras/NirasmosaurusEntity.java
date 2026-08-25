package net.darkblade.smop.entity.niras;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.combat.AttackAnchor;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.smop.command.SMOPBiteDebug;
import net.darkblade.smop.client.niras.NirasBabyLandAnimations;
import net.darkblade.smop.client.niras.NirasBabyWaterAnimations;
import net.darkblade.smop.client.niras.NirasLandAnimations;
import net.darkblade.smop.client.niras.NirasNeckOverlay;
import net.darkblade.smop.client.niras.NirasWaterAnimations;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.entity.SMOPWaterAnimal;
import net.darkblade.smop.entity.SwimTilt;
import net.darkblade.smop.entity.ai.goal.GenericBreedGoal;
import net.darkblade.smop.entity.ai.goal.HaulOutGoal;
import net.darkblade.smop.entity.ai.goal.SMOPFollowParentGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.ai.control.SwimSteerControl;
import net.darkblade.smop.entity.ai.goal.SwimWanderGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.egg.CustomEggBorn;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.DifficultyInstance;
import net.darkblade.smop.entity.ai.goal.SeekNestSiteGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.darkblade.smop.entity.ai.navigation.SmartSwimmingNavigation;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.darkblade.smop.entity.ai.control.SmoothSwimLookControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class NirasmosaurusEntity extends SMOPWaterAnimal implements SwimTilt, CustomEggBorn {

    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.COOKED_COD, Items.COOKED_SALMON);

    private static final double SWIM_SPRINT_THRESHOLD = 0.105D;

    private static final double FOLLOW_PARENT_DISTANCE = 6.0D;

    private static final double LAND_SPEED_SCALE = 0.25D;

    private static boolean isPrey(@NotNull LivingEntity candidate) {
        EntityType<?> type = candidate.getType();
        return type == EntityType.COD
                || type == EntityType.TROPICAL_FISH
                || type == EntityType.PUFFERFISH
                || type == EntityType.SQUID
                || type == EntityType.TURTLE;
    }


    private static final TargetingConditions.Selector PREY_SELECTOR =
            (target, level) -> isPrey(target);

    private static final Predicate<LivingEntity> NEST_ENEMIES =
            entity -> entity instanceof Player || isPrey(entity);

    private static final int BITE_WINDOW_START = 8;
    private static final int BITE_WINDOW_END = 9;

    private static final String ANIM_BITE = "bite";
    private static final String ANIM_WATER_BITE = "water_bite";

    private static final float LAND_BITE_SECONDS = 1.1F;
    private static final float WATER_BITE_SECONDS = 1.2F;

    private static final float ATTACK_REACH = 3.8F;
    private static final float ATTACK_STOP_DISTANCE = 3.2F;

    private static final int ATTACK_COOLDOWN = 26;
    private static final double CHASE_SPEED = 3.5D;
    private static final double LAND_CHASE_SPEED = 1.4D;

    private static final double HAUL_OUT_RANGE = 16.0D;

    private final SmartSwimmingNavigation waterNavigation;
    private final GroundPathNavigation groundNavigation;
    private final MoveControl swimControl;
    private final MoveControl walkControl;

    public NirasmosaurusEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.waterNavigation = new SmartSwimmingNavigation(this, level)
                .setLookahead(7.0D)
                .setNodeAcceptRadius(3.0D);
        this.waterNavigation.setRequiredPathLength(40.0F);
        this.groundNavigation = new GroundPathNavigation(this, level) {
            @Override
            public boolean moveTo(@Nullable Path path, double speed) {
                double ashore = NirasmosaurusEntity.this.isAggressive() ? LAND_CHASE_SPEED : speed;
                return super.moveTo(path, ashore * LAND_SPEED_SCALE);
            }
        };
        this.swimControl = new SwimSteerControl(this, 2.2F, 45.0F, 4.0F, 0.01F)
                .verticalGain(2.0F)
                .combatTurnSpeed(30.0F);
        this.moveControl = this.swimControl;
        this.lookControl = new SmoothSwimLookControl(this);
        this.walkControl = new DirectionalMoveControl<>(this).setTurnSpeed(5.0F).setCombatTurnSpeed(30.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(1, this.createSleepGoal());

        this.goalSelector.addGoal(2, new HaulOutGoal(this, HAUL_OUT_RANGE,
                () -> !this.isBaby() && !this.isInSleepCycle()));

        this.goalSelector.addGoal(3, new NirasBiteGoal()
                .reach(ATTACK_REACH)
                .stopDistance(ATTACK_STOP_DISTANCE)
                .cooldown(ATTACK_COOLDOWN)
                .attackCondition(target -> !this.isBaby())
                .onAttack((target, animator) -> animator.play(animator.getByName(
                        this.isInSwimmingMedium() ? ANIM_WATER_BITE : ANIM_BITE))));

        this.goalSelector.addGoal(4, new GenericBreedGoal<>(this, 1.15D, 16.0D));
        this.goalSelector.addGoal(6, new SMOPFollowParentGoal(this, 1.1D, FOLLOW_PARENT_DISTANCE));

        EggGoalRegistry.registerWithOwnGoal(this, SMOPBlocks.NIRAS_EGG,
                6, 8, true, true,
                ProtectEggBaseGoal.EggBreakReaction.IGNORE, NEST_ENEMIES, 7);

        this.nestGoal = new SeekNestSiteGoal(this, 1.0D, pos -> this.canPlaceEggAt(this.level(), pos));
        this.goalSelector.addGoal(5, this.nestGoal);

        this.goalSelector.addGoal(9, new SwimWanderGoal(this, 1.0D,
                () -> !this.isMovementLocked()));
        this.goalSelector.addGoal(10, new SMOPRandomStrollGoal(this, 1.0D, 160,
                () -> !this.isInWater() && !this.isMovementLocked()));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.10F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !NirasmosaurusEntity.this.isInSleepCycle() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return NirasmosaurusEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<Mob>(this, Mob.class, false, PREY_SELECTOR) {
            @Override
            public boolean canUse() {
                return NirasmosaurusEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
    }

    private boolean picksItsOwnFights() {
        return !this.isBaby() && !this.isInSleepCycle() && !this.isTame();
    }

    private class NirasBiteGoal extends AnimatableMeleeAttackGoal {

        NirasBiteGoal() {
            super(NirasmosaurusEntity.this, CHASE_SPEED, true);
        }

        @Override
        public boolean canUse() {
            return !NirasmosaurusEntity.this.isBaby() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !NirasmosaurusEntity.this.isBaby() && super.canContinueToUse();
        }
    }

    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    @Override
    public int getHeadRotSpeed() {
        return 3;
    }

    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    public float swimPitch;
    public float prevSwimPitch;
    public float swimRoll;
    public float prevSwimRoll;

    @Override
    public float swimPitch() {
        return this.swimPitch;
    }

    @Override
    public float swimRoll() {
        return this.swimRoll;
    }

    private static final float MAX_SWIM_PITCH = 30.0F;
    private static final float MAX_SWIM_ROLL = 35.0F;

    private static final float TILT_DEAD_ZONE = 0.006F;

    private static final float TILT_SMOOTHING = 0.06F;

    private void tickSwimTilt() {
        this.prevSwimPitch = this.swimPitch;
        this.prevSwimRoll = this.swimRoll;

        if (!this.isInWater() || this.isDeadOrDying() || this.isInSleepCycle()) {
            this.swimPitch = Mth.lerp(0.1F, this.swimPitch, 0.0F);
            this.swimRoll = Mth.lerp(0.1F, this.swimRoll, 0.0F);
            return;
        }

        boolean client = this.level().isClientSide();
        float vertical = client ? (float) (this.getY() - this.yo) : (float) this.getDeltaMovement().y;
        double horizontal = client
                ? Math.hypot(this.getX() - this.xo, this.getZ() - this.zo)
                : this.getDeltaMovement().horizontalDistance();

        vertical = Math.signum(vertical) * Math.max(0.0F, Math.abs(vertical) - TILT_DEAD_ZONE);

        float targetPitch = Mth.clamp(
                (float) Math.toDegrees(Mth.atan2(vertical, Math.max(horizontal, 1.0E-4D))),
                -MAX_SWIM_PITCH, MAX_SWIM_PITCH);
        this.swimPitch = Mth.lerp(TILT_SMOOTHING, this.swimPitch, targetPitch);

        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
        float speedFactor = (float) Math.min(1.0D, horizontal * 6.0D);
        float targetRoll = Mth.clamp(-yawDelta * 12.0F * speedFactor, -MAX_SWIM_ROLL, MAX_SWIM_ROLL);
        this.swimRoll = Mth.lerp(0.06F, this.swimRoll, targetRoll);
    }

    // ───────────────────────────────────────────────────── WATER ─────

    private void syncControlsToMedium() {
        boolean swimming = this.isInWater();
        boolean changed = swimming != this.wasSwimmingLastTick;
        this.wasSwimmingLastTick = swimming;

        this.navigation = swimming ? this.waterNavigation : this.groundNavigation;
        this.moveControl = swimming ? this.swimControl : this.walkControl;
        this.setSwimming(swimming);

        if (changed && !this.level().isClientSide()) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                this.navigation.moveTo(target, CHASE_SPEED);
            }
        }
    }

    private boolean wasSwimmingLastTick;

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.syncControlsToMedium();
        }
        this.tickSwimTilt();
    }

    @Override
    protected boolean shouldFlopOnLand() {
        return false;
    }

    @Override
    protected boolean shouldTakeDryDamage() {
        return false;
    }

    @Override
    protected double getSwimSpeedThreshold() {
        return SWIM_SPRINT_THRESHOLD;
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle",
                () -> NirasLandAnimations.lidle, () -> NirasBabyLandAnimations.l_idle,
                Loop.REPEATING, 3, 4.0F);
        StandardAnimation walk = clip("walk",
                () -> NirasLandAnimations.walking, () -> NirasBabyLandAnimations.walking,
                Loop.REPEATING, 3, 2.5F);
        StandardAnimation sprint = clip("sprint",
                () -> NirasLandAnimations.lsprint, () -> NirasBabyLandAnimations.l_sprint,
                Loop.REPEATING, 3, 1.2F);

        StandardAnimation waterIdle = clip("water_idle",
                () -> NirasWaterAnimations.widle, () -> NirasBabyWaterAnimations.w_idle,
                Loop.REPEATING, 3, 3.0F);
        StandardAnimation swimAdult = adultClip("swim",
                () -> NirasWaterAnimations.swim, Loop.REPEATING, 3, 1.9F);
        StandardAnimation swimBaby = babyClip("swim_baby",
                () -> NirasBabyWaterAnimations.swim, Loop.REPEATING, 3, 5.0F);
        StandardAnimation waterSprint = clip("water_sprint",
                () -> NirasWaterAnimations.wsprint, () -> NirasBabyWaterAnimations.w_sprint,
                Loop.REPEATING, 3, 2.0F);

        // The sleep cycle twice over: three phases ashore and the same three under water, each under
        // its own name, with onSleepPhaseBegin picking the pair.
        //
        // Priority 1, under the one-shots and above locomotion, so the idle and the swim cycle cannot
        // compete for a sleeping animal's frame.
        //
        // preparing_sleep is registered at 3.15 and NOT the 3.55 the clip declares: both land clips
        // stop keyframing at 3.15, and since a phase lasts exactly as long as its registered clip,
        // the remaining 0.4 s would be the animal held on its last frame. Look for that gap between
        // `withLength` and the last keyframe — nothing else checks it. The water pair has none.
        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> NirasLandAnimations.lsleep_preparing, () -> NirasBabyLandAnimations.l_sleep_preparing,
                Loop.PLAY_ONCE, 1, 3.15F);
        StandardAnimation sleep = clip("sleep",
                () -> NirasLandAnimations.lsleep, () -> NirasBabyLandAnimations.l_sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakening = clip("awakening",
                () -> NirasLandAnimations.lawakening, () -> NirasBabyLandAnimations.l_awakening,
                Loop.PLAY_ONCE, 1, 2.3F);

        StandardAnimation preparingSleepWater = clip("preparing_sleep_water",
                () -> NirasWaterAnimations.wsleep_preparing, () -> NirasBabyWaterAnimations.w_sleep_preparing,
                Loop.PLAY_ONCE, 1, 4.0F);
        StandardAnimation sleepWater = clip("sleep_water",
                () -> NirasWaterAnimations.wsleep, () -> NirasBabyWaterAnimations.w_sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakeningWater = clip("awakening_water",
                () -> NirasWaterAnimations.wawakening, () -> NirasBabyWaterAnimations.w_awakening,
                Loop.PLAY_ONCE, 1, 3.0F);

        // The land bite REPLACES: layer 0, priority 0, winning the frame over locomotion, which keeps
        // running underneath — a finished PLAY_ONCE clip falls back to the BIND pose and not to its
        // own last frame, so the cycle has to be there to catch it.
        StandardAnimation bite = clip(ANIM_BITE,
                () -> NirasLandAnimations.lbite, () -> NirasBabyLandAnimations.l_bite,
                Loop.PLAY_ONCE, 0, LAND_BITE_SECONDS);

        // The water bite OVERLAYS: layer 1, and cut down to the gNeck subtree.
        //
        // On layer 0 it would replace the swim cycle outright — a same-layer clip drops every bone it
        // does not author back to the bind pose — leaving the animal striking with its neck and dead
        // still from the chest back. On layer 1 the rig composites it additively, so the flippers and
        // tail keep swimming through the bite.
        //
        // The clip therefore must not author the body, or the two sources fight over the tail — hence
        // NirasNeckOverlay, which keeps the neck, head and jaws and drops the rest.
        //
        // The overlay composites over whatever locomotion clip is CURRENT, which is deliberate: the
        // body carries on doing whatever it was doing.
        StandardAnimation waterBite = overlayClip(ANIM_WATER_BITE,
                () -> NirasNeckOverlay.WATER_BITE, () -> NirasNeckOverlay.BABY_WATER_BITE,
                Loop.PLAY_ONCE, 0, WATER_BITE_SECONDS);

        // Two deaths, by medium: drowning on the shore and sinking in open water are different
        // silhouettes, and the export has a clip for each.
        StandardAnimation landDeath = clip("land_death",
                () -> NirasLandAnimations.ldeath, () -> NirasBabyLandAnimations.l_death,
                Loop.PLAY_ONCE, 0, 1.5F);
        StandardAnimation waterDeath = clip("water_death",
                () -> NirasWaterAnimations.wdeath, () -> NirasBabyWaterAnimations.w_death,
                Loop.PLAY_ONCE, 0, 2.5F);

        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && this.isMoving() && !this.isSprinting());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && this.isMoving() && this.isSprinting());

        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && !this.isMoving());
        // Chasing something, NOT swimming quickly. The base's isSwimmingFast() is a speed threshold,
        // and a cruising Nirasmosaurus is already past any sensible cut, so the sprint clip becomes
        // the only one ever seen. Sprinting should read as intent, so it keys off having prey.
        //
        // isAggressive() and NOT getTarget(): the target is not synced to clients and play conditions
        // run on both sides, so reading it directly puts the two on different clips. Aggression rides
        // the synced flag byte, which MeleeAttackGoal raises in start() and clears in stop().
        swimAdult.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && !this.isBaby());
        swimBaby.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && this.isBaby());
        waterSprint.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && this.isAggressive());

        // Spelled out rather than left on the rig's 220 ms default. The seam that matters is the last
        // one: the waking clip ends on exactly the pose the idle starts from, verified channel by
        // channel, so the only thing that can show there is the cross-fade itself.
        preparingSleep.blendInMs(300).blendOutMs(250);
        preparingSleepWater.blendInMs(300).blendOutMs(250);
        sleep.blendInMs(300).blendOutMs(300);
        sleepWater.blendInMs(300).blendOutMs(300);
        awakening.blendInMs(250).blendOutMs(350);
        awakeningWater.blendInMs(250).blendOutMs(350);

        // The one-shots are NOT started by these conditions — the animator's auto-start loop only
        // starts REPEATING clips, so SleepGoal starts them through onSleepPhaseBegin. The conditions
        // buy the reverse: a clip whose condition goes false is CUT, which is what makes a wake-up
        // mid-transition end the transition instead of playing it out.
        preparingSleep.setPlayCondition(a -> this.isPreparingSleep() && !this.isSleepingInWater());
        preparingSleepWater.setPlayCondition(a -> this.isPreparingSleep() && this.isSleepingInWater());
        // The loop covers PREPARING_SLEEP too, so the settling clip has the sleeping pose underneath
        // it to hand over to rather than a gap. setNextAnimation chains the two.
        sleep.setPlayCondition(a -> (this.isSleeping() || this.isPreparingSleep()) && !this.isSleepingInWater());
        sleepWater.setPlayCondition(a -> (this.isSleeping() || this.isPreparingSleep()) && this.isSleepingInWater());
        preparingSleep.setNextAnimation("sleep");
        preparingSleepWater.setNextAnimation("sleep_water");
        awakening.setPlayCondition(a -> this.isAwakening() && !this.isSleepingInWater());
        awakeningWater.setPlayCondition(a -> this.isAwakening() && this.isSleepingInWater());

        landDeath.setPlayCondition(a -> !this.isInSwimmingMedium());
        waterDeath.setPlayCondition(a -> this.isInSwimmingMedium());
        landDeath.blockAdditive();
        waterDeath.blockAdditive();

        // Snappy either side: a bite that fades in reads as a yawn.
        bite.blendInMs(80).blendOutMs(150);
        waterBite.blendInMs(80).blendOutMs(150);

        // ── The two bites ─────────────────────────────────────────────────────────────────────
        //
        // Separate HitWindow instances, and that is not tidiness: each carries its own
        // hitThisSwing / lastSweepAngle state and cannot be shared between two clips.
        //
        // The land bite: a frontal cuboid across the frames the jaws shut. box3d and NOT box —
        // AttackShape's interface note says box ignores the Y axis outright, which on a beach means
        // biting a player standing on an overhang two blocks up.
        //
        // Anchor 2.0 forward and 0.9 up, box reaching another 2.0: the muzzle tip sits 3.5 blocks
        // ahead (see ATTACK_REACH), so the volume brackets it rather than starting at it. All six are
        // look numbers — tune them against the render with /deluxelib debug hitboxes.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(2.0F, 1.1F, 1.2F))
                .anchor(AttackAnchor.of(2.0F, 0.0F, 0.9F))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                // Low on purpose: the heavier moves need prey that stays in the jaws, not prey that
                // gets launched.
                .knockback(0.2F)
                .filter(target -> target == this.getTarget() || !(target instanceof NirasmosaurusEntity))
                // Diagnostic only, and silent unless /smop debug bite watch is running.
                .onSweep((attacker, origin, facing, shape, hits) ->
                        SMOPBiteDebug.reportSweep(attacker, origin, facing, hits.size()))
                .applyTo(bite);

        // The water bite is aimed in 3D where the land one is flattened to the ground plane.
        // Underwater a target is as often above or below as beside, and a flattened box would leave a
        // Nirasmosaurus under a swimming player snapping at nothing for as long as the player stayed
        // there — the goal measures range in 3D and would keep firing.
        //
        // ── IT AIMS WITH THE HEAD, and aimAlongLook() would be wrong here. ──
        //
        // That helper is getViewVector(1.0F), which reads getXRot() and getYRot() — and on a mob
        // getYRot() is the BODY yaw, written by SwimSteerControl at 2.2 degrees a tick. What tracks
        // the target is yHeadRot, written by the LookControl that MeleeAttackGoal points at the prey
        // every tick. Aim off the body and the volume follows the lagging part of the animal, so the
        // bite lands nowhere near its target. The pitch in that view vector IS clean — the swim
        // control never writes xRot — which is what makes the fault easy to miss.
        //
        // The anchor stays in look space but its forward drops from 2.4 to 0.6, which is the neck's
        // base rather than the middle of the snout — AttackAnchor has no head space, so any forward
        // offset there still swings with the body yaw, and 0.6 makes that error small enough not to
        // matter (0.6 x sin 40 is under half a block). The box then does the reaching, 3.2 long,
        // along the head. Origin at the shoulder, volume along the neck: that IS the rig.
        //
        // The -0.5 of height is not a nicety either. AttackAnchor.look starts at the EYES, and the
        // default eye height of a 1.6-tall entity is 0.85 x 1.6 = 1.36, while the rig's head sits
        // about 0.8 above the feet. Without it the bite is born half a block above the mouth.
        //
        // Known limit: the rig bends the neck at most 35 degrees of yaw and 30 of pitch, so past that
        // the volume goes where the head is AIMED and the model can only lean part of the way.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(3.2F, 1.0F, 1.0F))
                .anchor(AttackAnchor.look(0.6F, 0.0F, -0.5F))
                .facing(e -> Vec3.directionFromRotation(e.getXRot(), e.getYHeadRot()))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .knockback(0.2F)
                // Same escape hatch as the land bite: the blanket exclusion spares bystanders, but
                // without `target == getTarget()` it also vetoes the one it is fighting — in the
                // broad-phase predicate, before the shape test runs, so it reads as a geometry miss
                // that no amount of retuning fixes.
                .filter(target -> target == this.getTarget() || !(target instanceof NirasmosaurusEntity))
                .onSweep((attacker, origin, facing, shape, hits) ->
                        SMOPBiteDebug.reportSweep(attacker, origin, facing, hits.size()))
                .applyTo(waterBite);

        this.animator().register(idle, walk, sprint, waterIdle, swimAdult, swimBaby, waterSprint,
                preparingSleep, sleep, awakening,
                preparingSleepWater, sleepWater, awakeningWater,
                bite, waterBite);
        this.animator().registerDeath(waterDeath, landDeath);
    }

    // ───────────────────────────────────────────────────── NESTING ─────

    /**
     * The goal that walks her to the nest, held so {@link #isSettledToLay()} can ask whether she has
     * got there yet.
     *
     * <p><b>Declared without an initialiser on purpose.</b> {@code registerGoals()} runs from
     * {@code Mob}'s constructor, and a subclass's field initialisers run only after the superclass
     * constructor returns — so writing {@code = null} here would emit an assignment that wipes the
     * instance the constructor already stored. Same trap as {@code SMOPAnimal.sleepUrge}.
     */
    private SeekNestSiteGoal nestGoal;

    @Override
    protected boolean isSettledToLay() {
        return super.isSettledToLay() && this.nestGoal != null && this.nestGoal.hasArrived();
    }

    @Override
    protected boolean nestsAshore() {
        return true;
    }

    @Override
    protected @NotNull List<BlockPos> eggPlacementPositions() {
        Vec3 flank = Vec3.directionFromRotation(0.0F, this.yBodyRot + 90.0F).scale(2.0D);
        return List.of(
                BlockPos.containing(this.position().add(flank)),
                BlockPos.containing(this.position().subtract(flank)),
                this.blockPosition());
    }

    @Override
    protected boolean canPlaceEggAt(@NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.canSeeSky(pos)
                && (level.getBlockState(pos.below()).is(BlockTags.SAND)
                || level.getBlockState(pos.below()).is(Blocks.GRAVEL));
    }

    @Override
    public boolean canMate(@NotNull Animal other) {
        return super.canMate(other) && !this.isInWater() && !other.isInWater();
    }

    private boolean isInSwimmingMedium() {
        return this.isUnderWater() || (this.isInWater() && !this.onGround());
    }

    private boolean canPlayLocomotion() {
        return !this.isPerformingAction() && !this.isDeadOrDying();
    }

    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    private StandardAnimation overlayClip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                          Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 1, priority, seconds);
    }

    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    private StandardAnimation babyClip(String name, Supplier<Object> baby, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(baby), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────


    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }


    private static final EntityDataAccessor<Boolean> SLEEP_IN_WATER =
            SynchedEntityData.defineId(NirasmosaurusEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_IN_WATER, false);
    }

    public boolean isSleepingInWater() {
        return this.entityData.get(SLEEP_IN_WATER);
    }


    @Override
    public void onSleepPhaseBegin(@NotNull SleepPhase phase) {
        if (phase == SleepPhase.SITTING_DOWN || phase == SleepPhase.PREPARING_SLEEP) {
            this.entityData.set(SLEEP_IN_WATER, this.isInSwimmingMedium());
        }
        String clip = this.sleepClipName(phase);
        if (clip != null) {
            this.playIfRegistered(clip);
        }
    }


    @Override
    public int sleepPhaseDuration(@NotNull SleepPhase phase) {
        String clip = this.sleepClipName(phase);
        if (clip == null) {
            return 0;
        }
        int clipTicks = this.clipDurationTicks(clip);
        if (phase == SleepPhase.SITTING) {
            return clipTicks > 0 ? this.getSittingDuration() : 0;
        }
        return clipTicks;
    }

    @Nullable
    private String sleepClipName(SleepPhase phase) {
        String base = phase.clipName();
        if (base == null) {
            return null;
        }
        return this.isSleepingInWater() ? base + "_water" : base;
    }

    // ───────────────────────────────────────────────────── SPAWN & BREEDING ─────


    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }


    public static boolean checkNirasSpawnRules(EntityType<NirasmosaurusEntity> type, LevelAccessor level,
                                               EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER);
        boolean onShore = level.getBlockState(pos).isAir()
                && (level.getBlockState(pos.below()).is(BlockTags.SAND)
                || level.getBlockState(pos.below()).is(Blocks.GRAVEL));
        return inWater || onShore;
    }


    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setMale(this.getRandom().nextBoolean());
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }


    @Override
    public void onEggBorn(@NotNull ServerLevel level, @NotNull BlockPos nestPos) {
        this.setMale(this.getRandom().nextBoolean());
    }


    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return FOOD_ITEMS.test(stack);
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.DOLPHIN_AMBIENT_WATER;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }
}
