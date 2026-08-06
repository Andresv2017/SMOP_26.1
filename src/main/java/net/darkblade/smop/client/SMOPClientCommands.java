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

/**
 * SMOP's client-only debug commands, and the numpad input they route.
 *
 * <p>Registered on the CLIENT dispatcher rather than the server one, and left unrestricted: each of
 * these only affects local rendering and is a no-op for anybody else, so there is nothing to gate on
 * operator level. Same split — and same reasoning — as DeluxeLib's own {@code DeluxeCommands.Client}.
 */
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

    /** Feeds raw key presses to the tuner; it ignores everything while inactive. */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.@NotNull Key event) {
        KriftoPerchTuner.onKey(event.getKey(), event.getAction());
    }

    private SMOPClientCommands() {}
}
