package net.darkblade.smop.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.SMOPEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /smop spawn baby <animal> [pos]} — one literal per SMOP animal, each spawning an adult of
 * that type aged down to freshly-hatched. Named and listed explicitly (no generic "spawn baby
 * &lt;entity&gt;" over the whole entity registry): the point is a fast, discoverable shortcut for the
 * animals this mod actually adds, in the same freshly-hatched state the manual test checklists have
 * been reaching by hand via {@code /summon smop:kriftognathus ~ ~ ~ {Age:-24000}}.
 *
 * <p>Registered on the SERVER dispatcher, gated to operators like vanilla {@code /summon} — this
 * spawns a real entity into the world, unlike {@code SMOPClientCommands}' purely-local tuners.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPCommands {

    @SubscribeEvent
    public static void onRegisterCommands(@NotNull RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("smop")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("baby")
                                .then(spawnBabyLiteral("tangoftero", SMOPEntities.TANGOFTERO.get()))
                                .then(spawnBabyLiteral("salmon", SMOPEntities.SALMON.get()))
                                .then(spawnBabyLiteral("kriftognathus", SMOPEntities.KRIFTOGNATHUS.get()))
                                .then(spawnBabyLiteral("hell_hippo", SMOPEntities.HELL_HIPPO.get()))
                                .then(spawnBabyLiteral("nirasmosaurus", SMOPEntities.NIRASMOSAURUS.get()))))
                .then(SMOPSwimDebug.build())
                .then(SMOPSpawnDebug.build())
                .then(SMOPNestDebug.build())
                .then(SMOPBiteDebug.build())
                .then(SMOPDeathDebug.build()));
    }

    /** One {@code <name> [pos]} literal, spawning {@code type} as a baby at the given or current position. */
    private static LiteralArgumentBuilder<CommandSourceStack> spawnBabyLiteral(String name, EntityType<?> type) {
        return Commands.literal(name)
                .executes(ctx -> spawnBaby(ctx.getSource(), type, ctx.getSource().getPosition()))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> spawnBaby(ctx.getSource(), type, Vec3Argument.getVec3(ctx, "pos"))));
    }

    private static int spawnBaby(CommandSourceStack source, EntityType<?> type, Vec3 pos) {
        ServerLevel level = source.getLevel();
        Entity entity = type.spawn(level, e -> {
            if (e instanceof AgeableMob ageable) {
                ageable.setBaby(true);
            }
        }, BlockPos.containing(pos), EntitySpawnReason.COMMAND, false, false);

        if (entity == null) {
            source.sendFailure(Component.translatable("commands.summon.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.summon.success", entity.getDisplayName()), true);
        return 1;
    }

    private SMOPCommands() {}
}
