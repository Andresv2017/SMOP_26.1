package net.darkblade.smop.entity;

import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.BaseAnimation;
import net.darkblade.deluxelib.anim.BlendLayer;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.smop.block.AbstractEggBlock;
import net.darkblade.smop.entity.sleep.ISleepingEntity;
import net.darkblade.smop.entity.sleep.SleepGoal;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.darkblade.smop.entity.sleep.SleepUrge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class SMOPAnimal extends TamableAnimal implements Animatable<SMOPAnimal>, ISleepingEntity {

    // ───────────────────────────────────────────────────── SYNCED STATE ─────

    private static final EntityDataAccessor<Integer> SLEEP_PHASE =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WANDERING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_EGG =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ROARING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ACTION =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> MOVING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);

    private MobAnimator<SMOPAnimal> animator;

    protected SMOPAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    public @NotNull MobAnimator<SMOPAnimal> animator() {
        if (this.animator == null) {
            this.animator = new MobAnimator<>(this);
        }
        return this.animator;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_PHASE, SleepPhase.NONE.ordinal());
        builder.define(WANDERING, false);
        builder.define(HAS_EGG, false);
        builder.define(ROARING, false);
        builder.define(ACTION, "");
        builder.define(MOVING, false);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.sleepUrge().tick();
            this.entityData.set(MOVING, this.moveHold.tick(this.isMovingNow()));
            if (this.actionTicksLeft > 0 && --this.actionTicksLeft <= 0) {
                this.stopAction();
            }
        }

        if (this.isOrderedToSit()) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void tickDeath() {
        this.animator().tickDeath();
    }

    @Override
    public void aiStep() {
        if (this.isInSleepCycle()) {
            this.setTarget(null);
        }
        super.aiStep();
    }

    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.isMovementLocked()) {
            this.getNavigation().stop();
            super.travel(new Vec3(0.0D, travelVector.y, 0.0D));
            return;
        }
        super.travel(travelVector);
    }

    public boolean isMovementLocked() {
        return this.isInSleepCycle() || this.isRoaring()
                || (this.isPerformingAction() && this.actionLocksMovement(this.currentAction()));
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !this.isRoaring() && this.getTarget() == null) {
            this.sleepUrge().requestWake();
        }
        return hurt;
    }

    // ───────────────────────────────────────────────────── MOVEMENT FLAG ─────

    private static final int MOVE_HOLD_TICKS = 6;

    private final MoveHold moveHold = new MoveHold();

    protected static final class MoveHold {
        private int holdTicks;

        public boolean tick(boolean movingNow) {
            if (movingNow) {
                this.holdTicks = MOVE_HOLD_TICKS;
                return true;
            }
            if (this.holdTicks > 0) {
                this.holdTicks--;
                return true;
            }
            return false;
        }
    }

    protected boolean isMovingNow() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
    }

    public boolean isMoving() {
        return this.entityData.get(MOVING);
    }

    // ───────────────────────────────────────────────────── SIT / FOLLOW / WANDER ─────

    public boolean isWandering() {
        return this.entityData.get(WANDERING);
    }

    public void setWandering(boolean wandering) {
        this.entityData.set(WANDERING, wandering);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.isOwnedBy(player) && player.isShiftKeyDown()) {
            if (!this.isOrderedToSit() && !this.isWandering()) {
                this.setWandering(true);
                this.setOrderedToSit(false);
                this.messageState("wandering", player);
            } else {
                this.setWandering(false);
                boolean willSit = !this.isOrderedToSit();
                this.setOrderedToSit(willSit);
                this.messageState(willSit ? "staying" : "following", player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    protected void messageState(String state, Player player) {
        player.sendOverlayMessage(this.getName().copy().append(" is now ").append(state));
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    @Override
    public SleepPhase sleepPhase() {
        return SleepPhase.byId(this.entityData.get(SLEEP_PHASE));
    }

    @Override
    public void setSleepPhase(SleepPhase phase) {
        this.entityData.set(SLEEP_PHASE, phase.ordinal());
    }

    @Override
    public boolean isSleeping() {
        return this.sleepPhase() == SleepPhase.SLEEPING;
    }

    @Override
    public int getSittingDuration() {
        return 60 + this.random.nextInt(100);
    }

    private SleepUrge sleepUrge;

    public SleepUrge sleepUrge() {
        if (this.sleepUrge == null) {
            this.sleepUrge = new SleepUrge(this);
        }
        return this.sleepUrge;
    }

    protected SleepGoal<SMOPAnimal> createSleepGoal() {
        return new SleepGoal<>(this, this.sleepUrge());
    }

    @Override
    public int sleepPhaseDuration(SleepPhase phase) {
        String clip = phase.clipName();
        if (clip == null) {
            return 0;
        }
        int clipTicks = this.clipDurationTicks(clip);
        if (phase == SleepPhase.SITTING) {
            return clipTicks > 0 ? this.getSittingDuration() : 0;
        }
        return clipTicks;
    }

    protected int clipDurationTicks(String name) {
        for (BlendLayer layer : this.animator().getLayers().values()) {
            if (layer.anims.get(name) instanceof BaseAnimation base) {
                return base.getDuration();
            }
        }
        return 0;
    }

    public static final String ANIM_SLEEP = SleepPhase.SLEEPING.clipName();
    public static final String ANIM_ROAR = "roar";

    @Override
    public void onSleepPhaseBegin(SleepPhase phase) {
        String clip = phase.clipName();
        if (clip != null) {
            this.playIfRegistered(clip);
        }
    }

    protected void playIfRegistered(String name) {
        MobAnimator<SMOPAnimal> animator = this.animator();
        for (BlendLayer layer : animator.getLayers().values()) {
            Animation anim = layer.anims.get(name);
            if (anim != null) {
                animator.play(anim);
                return;
            }
        }
    }

    // ───────────────────────────────────────────────────── REPRODUCTION ─────

    private boolean isMammal = false;

    public boolean isMammal() {
        return this.isMammal;
    }

    public void setMammal(boolean mammal) {
        this.isMammal = mammal;
    }

    public boolean hasEgg() {
        return this.entityData.get(HAS_EGG);
    }

    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(HAS_EGG, hasEgg);
    }

    @Nullable
    public BlockPos tryLayEgg(Block eggBlock) {
        if (!this.hasEgg() || this.isMammal() || !this.isSettledToLay()) {
            return null;
        }

        Level level = this.level();
        BlockPos pos = null;
        for (BlockPos candidate : this.eggPlacementPositions()) {
            if (this.canPlaceEggAt(level, candidate)) {
                pos = candidate;
                break;
            }
        }
        if (pos == null) {
            return null;
        }

        // defaultBlockState() would always be a clutch of one — the block decides how many eggs a
        // laying actually produces (see AbstractEggBlock#newClutchState).
        BlockState clutch = eggBlock instanceof AbstractEggBlock egg
                ? egg.newClutchState(this.random)
                : eggBlock.defaultBlockState();
        level.setBlock(pos, clutch, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.setHasEgg(false);
        return pos;
    }

    protected List<BlockPos> eggPlacementPositions() {
        return List.of(this.blockPosition());
    }

    protected boolean isSettledToLay() {
        return this.onGround();
    }

    protected boolean canPlaceEggAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid();
    }

    public boolean isNestSiteAt(BlockPos pos) {
        return this.canPlaceEggAt(this.level(), pos);
    }

    // ───────────────────────────────────────────────────── ROAR ─────

    private static final int ROAR_SOUND_MIN_GAP = 2;

    private int lastRoarSoundTick = -200;

    public int getRoarDuration() {
        int clip = this.clipDurationTicks(ANIM_ROAR);
        return clip > 0 ? clip : 70;
    }

    public boolean isRoaring() {
        return this.entityData.get(ROARING);
    }

    public void setRoaring(boolean roaring) {
        boolean was = this.isRoaring();
        this.entityData.set(ROARING, roaring);

        if (was || !roaring || this.level().isClientSide()) {
            return;
        }
        SoundEvent sound = this.getRoarSound();
        if (sound != null && this.tickCount - this.lastRoarSoundTick >= ROAR_SOUND_MIN_GAP) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE,
                    1.0F, 0.95F + this.getRandom().nextFloat() * 0.10F);
            this.lastRoarSoundTick = this.tickCount;
        }
    }

    @Nullable
    public SoundEvent getRoarSound() {
        return null;
    }

    // ───────────────────────────────────────────────────── SCRIPTED ACTION ─────

    private int actionTicksLeft;

    public void startAction(String name) {
        this.entityData.set(ACTION, name);
        int durationTicks = this.clipDurationTicks(name);
        this.actionTicksLeft = Math.max(1, durationTicks);
        if (!this.level().isClientSide()) {
            this.onActionStart(name);
        }
        // The synced ACTION string above is what setPlayCondition(a -> isPerforming(name)) reads,
        // but MobAnimator#tick's auto-start loop only ever starts REPEATING clips off a play
        // condition turning true — a one-shot action registered PLAY_ONCE (eating, tamed, ...) would
        // sit there with canPlay()==true and isPlaying()==false forever, never actually starting,
        // while actionTicksLeft still counts down and calls stopAction() as if it had played. The
        // caller then sees isPerforming(name) go false right on schedule with nothing ever shown.
        // play() is the direct trigger every other PLAY_ONCE clip in this mod uses (e.g. the
        // Tangoftero's bite from handleFeeding) — this just makes startAction do it too, once, here,
        // instead of every caller needing to remember it.
        //
        // Gated on durationTicks > 0 (not a bare getByName/play call) because getByName throws on an
        // unregistered name and durationTicks already tells us, for free, whether one was found —
        // same safety clipDurationTicks itself already promises ("or 0 if never registered").
        if (durationTicks > 0) {
            this.animator().play(this.animator().getByName(name));
        }
    }

    public void stopAction() {
        this.entityData.set(ACTION, "");
        this.actionTicksLeft = 0;
    }

    public String currentAction() {
        return this.entityData.get(ACTION);
    }

    public boolean isPerforming(String name) {
        return this.currentAction().equals(name);
    }

    public boolean isPerformingAction() {
        return !this.currentAction().isEmpty();
    }

    protected boolean actionLocksMovement(String name) {
        return true;
    }

    protected void onActionStart(String name) {
    }

    // ───────────────────────────────────────────────────── NBT ─────

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Wandering", this.isWandering());
        output.putBoolean("IsMammal", this.isMammal);
        output.putBoolean("HasEgg", this.hasEgg());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setWandering(input.getBooleanOr("Wandering", false));
        this.isMammal = input.getBooleanOr("IsMammal", false);
        this.setHasEgg(input.getBooleanOr("HasEgg", false));
    }
}
