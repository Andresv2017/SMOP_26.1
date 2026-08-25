package net.darkblade.smop.event;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.tag.SMOPTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class EggBreakHandler {

    private static final double NOTIFY_RADIUS = 10.0D;

    @SubscribeEvent
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(SMOPTags.Blocks.EGG_BLOCKS)) {
            return;
        }

        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(NOTIFY_RADIUS));
        for (Mob mob : nearby) {
            for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof ProtectEggBaseGoal guard) {
                    guard.notifyEggBroken(pos);
                }
            }
        }
    }

    private EggBreakHandler() {}
}
