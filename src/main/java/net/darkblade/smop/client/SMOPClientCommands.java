package net.darkblade.smop.client;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.client.krifto.KriftoPerchTuner;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class SMOPClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(@NotNull RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("smop")
                .then(Commands.literal("debug")
                        .then(Commands.literal("kriftoperch")
                                .executes(ctx -> {
                                    boolean enabled = KriftoPerchTuner.toggle();
                                    ctx.getSource().sendSuccess(() -> KriftoPerchTuner.helpMessage(enabled), false);
                                    return 1;
                                })
                                .then(Commands.literal("reset").executes(ctx -> {
                                    KriftoPerchTuner.reset();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[kriftoperch] values reset"), false);
                                    return 1;
                                })))));
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.@NotNull Key event) {
        KriftoPerchTuner.onKey(event.getKey(), event.getAction());
    }

    private SMOPClientCommands() {}
}
