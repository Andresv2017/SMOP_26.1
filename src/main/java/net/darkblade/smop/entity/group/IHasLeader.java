package net.darkblade.smop.entity.group;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** A {@link GroupType#PACK} member that follows a leader. See {@link GroupUtil}. */
public interface IHasLeader {

    @Nullable
    LivingEntity getGroupLeader();

    void setGroupLeader(@Nullable LivingEntity leader);
}
