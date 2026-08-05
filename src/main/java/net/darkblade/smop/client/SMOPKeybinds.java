package net.darkblade.smop.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.RiderControllable;
import net.darkblade.smop.network.SMOPNetwork;
import net.darkblade.smop.network.packet.RiderActionServerPacket;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The mount keybinds and the client tick that turns them into {@link RiderActionServerPacket}s.
 *
 * <p><b>Port note.</b> 26.1 replaced the keybind category string with a
 * {@link KeyMapping.Category} object built from an {@code Identifier}; its label key is derived as
 * {@code key.category.<namespace>.<path>}, hence {@code key.category.smop.main}. Registration of a
 * custom category goes through {@code RegisterKeyMappingsEvent#registerCategory} rather than the
 * now-deprecated static {@code Category.register}.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class SMOPKeybinds {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(SMOP.id("main"));

    public static final KeyMapping ATTACK = key("attack", GLFW.GLFW_KEY_R);
    public static final KeyMapping FEAR = key("fear", GLFW.GLFW_KEY_G);
    public static final KeyMapping OPEN_INVENTORY = key("open_inventory", GLFW.GLFW_KEY_V);
    public static final KeyMapping DESCEND = key("descend", GLFW.GLFW_KEY_X);

    /** Descend is held rather than tapped, so its edges are tracked instead of consumed as clicks. */
    private static boolean descendWasDown = false;

    private static KeyMapping key(String name, int keysym) {
        return new KeyMapping("key.smop." + name, InputConstants.Type.KEYSYM, keysym, CATEGORY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (ATTACK.consumeClick()) {
            send(RiderControllable.RiderAction.ATTACK);
        }
        while (FEAR.consumeClick()) {
            send(RiderControllable.RiderAction.FEAR);
        }
        while (OPEN_INVENTORY.consumeClick()) {
            send(RiderControllable.RiderAction.OPEN_INVENTORY);
        }

        boolean descendDown = DESCEND.isDown();
        if (descendDown != descendWasDown) {
            send(descendDown
                    ? RiderControllable.RiderAction.DESCEND_START
                    : RiderControllable.RiderAction.DESCEND_STOP);
            descendWasDown = descendDown;
        }
    }

    private static void send(RiderControllable.RiderAction action) {
        SMOPNetwork.INSTANCE.sendToServer(new RiderActionServerPacket(action));
    }

    /**
     * Mod-bus event. 26.1 infers which bus a handler belongs to from its event type, so this lives
     * alongside the game-bus tick handler above instead of needing its own
     * {@code bus = Bus.MOD} subscriber class — that attribute no longer exists.
     */
    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ATTACK);
        event.register(FEAR);
        event.register(OPEN_INVENTORY);
        event.register(DESCEND);
    }

    private SMOPKeybinds() {}
}
