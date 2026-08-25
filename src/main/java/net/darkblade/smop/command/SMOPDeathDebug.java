package net.darkblade.smop.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.BaseAnimation;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPDeathDebug {

    private static final Logger LOGGER = LoggerFactory.getLogger("smop-death");

    @Nullable
    private static ServerPlayer watcher;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("death")
                .executes(ctx -> toggle(ctx.getSource())));
    }

    private static int toggle(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (watcher != null) {
            watcher = null;
            source.sendSuccess(() -> Component.literal("Death reporting off.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        watcher = player;
        source.sendSuccess(() -> Component.literal(
                        "Death reporting on. Kill a SMOP animal and the choice will be reported here and to the log.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent event) {
        if (watcher == null || !(event.getEntity() instanceof SMOPAnimal mob) || mob.level().isClientSide()) {
            return;
        }
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        BlockPos eyes = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());

        report(Component.literal("── " + name(mob) + " died @" + feet.getX() + "," + feet.getY() + "," + feet.getZ())
                .withStyle(ChatFormatting.AQUA));

        // The raw inputs every death condition on this species is built from.
        report(line("medium", "inWater=" + mob.isInWater()
                + " underWater=" + mob.isUnderWater()
                + " onGround=" + mob.onGround()
                + " fluidHeight=" + fmt(mob.getFluidHeight(net.minecraft.tags.FluidTags.WATER))));
        report(line("blocks", "feet=" + blockName(level, feet)
                + " eyes=" + blockName(level, eyes)
                + " below=" + blockName(level, feet.below())));

        MobAnimator<SMOPAnimal> animator = mob.animator();
        if (!animator.hasDeathAnimations()) {
            report(line("chosen", "NONE — this species registered no death variants at all"));
            return;
        }

        // Each registered variant and whether its condition held at this instant. This is exactly the
        // filter startDeathAnimation ran, re-read a moment later.
        List<String> states = new ArrayList<>();
        for (String variant : animator.deathAnimations) {
            Animation anim = animator.getByName(variant);
            String detail = variant + "[canPlay=" + anim.canPlay();
            if (anim instanceof BaseAnimation base) {
                detail += " playing=" + base.isPlaying() + " ticks=" + base.getDuration();
            }
            states.add(detail + "]");
        }
        report(line("variants", String.join("  ", states)));

        String chosen = animator.currentDeathAnimation;
        report(line("chosen", chosen == null
                ? "NONE — no variant qualified, so vanilla's own ~90° tip-over is what you are seeing"
                : chosen));
    }

    private static String blockName(Level level, BlockPos pos) {
        String id = level.getBlockState(pos).getBlock().getDescriptionId();
        return id.substring(id.lastIndexOf('.') + 1);
    }

    private static String name(SMOPAnimal mob) {
        String id = mob.getType().getDescriptionId();
        return id.substring(id.lastIndexOf('.') + 1);
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    private static Component line(String key, String value) {
        return Component.literal("   " + key + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static void report(Component message) {
        LOGGER.info(message.getString());
        if (watcher != null) {
            watcher.sendSystemMessage(message);
        }
    }

    private SMOPDeathDebug() {}
}
