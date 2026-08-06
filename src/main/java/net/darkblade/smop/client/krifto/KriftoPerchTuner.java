package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.client.PerchClient;
import net.darkblade.deluxelib.client.render.NumpadAxisTuner;
import net.darkblade.deluxelib.entity.perch.PerchPlacement;
import net.darkblade.smop.entity.krifto.KriftoPerchPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Live, in-game tuner for where the perched Kriftognathus sits — perch it with a right-click, run
 * {@code /smop debug kriftoperch}, nudge with the NUMPAD until it sits right over your head, then
 * paste the printed constants into {@link KriftoPerchPlacement}. Client-side only, local player only,
 * not persisted. Built on DeluxeLib's {@link NumpadAxisTuner}, the same skeleton {@code OwlPerchTuner}
 * uses.
 *
 * <p>Only two of the four modes the owl's tuner has are here: this creature's host pose comes from
 * {@code KriftognathusRenderer}'s {@code PerchPoseHandler} rather than the placement's arm angles, and
 * it opts out of first-person rendering entirely — so ARM, FP_POS and FP_ROT would all tune values
 * nothing reads. What is left is where it sits and how big it is.
 *
 * <p><b>Tuning moves the visual, not the hitbox.</b> This override is client-side while the server
 * places the real position from its own read of the compiled constants, so during a session the drawn
 * bird and its actual position drift apart until you paste the numbers back. Expected for a debug
 * tool — same caveat as every other tuner in the library.
 *
 * <p>Numpad controls while active (and perched):
 * <ul>
 *   <li>{@code 5} — switch between POS mode (where it sits) and SCALE mode</li>
 *   <li>POS: {@code 8}/{@code 2} forward/back, {@code 4}/{@code 6} right/left, {@code 9}/{@code 3}
 *       up/down</li>
 *   <li>SCALE: {@code 9}/{@code 3} bigger/smaller</li>
 *   <li>{@code 0} — print the current values as ready-to-paste constants</li>
 *   <li>{@code .} — reset back to the values compiled into {@link KriftoPerchPlacement}</li>
 * </ul>
 */
public final class KriftoPerchTuner extends NumpadAxisTuner {

    private static final KriftoPerchTuner INSTANCE = new KriftoPerchTuner();

    /** Blocks per nudge — fine enough to land a bird on a head without a dozen presses per block. */
    private static final float POS_STEP = 0.02F;
    private static final float SCALE_STEP = 0.05F;

    private enum Mode { POS, SCALE }

    private Mode mode = Mode.POS;

    // Null = "not overridden yet, seed from the compiled default on first read".
    private @Nullable Float side;
    private @Nullable Float height;
    private @Nullable Float forward;
    private @Nullable Float modelScale;

    private KriftoPerchTuner() {}

    /** Toggles the tuner, installing or clearing its live override on {@link KriftoPerchPlacement} so
     *  the render path picks the values up (or stops seeing them). */
    public static boolean toggle() {
        INSTANCE.active = !INSTANCE.active;
        KriftoPerchPlacement.setOverride(INSTANCE.active ? KriftoPerchTuner::placement : null);
        return INSTANCE.active;
    }

    public static boolean isActive() {
        return INSTANCE.active;
    }

    /** Drops every override, so the next read re-seeds from the compiled constants. */
    public static void reset() {
        INSTANCE.resetValues();
    }

    public static void onKey(int key, int action) {
        INSTANCE.handleInput(key, action);
    }

    /** The values as the library consumes them. A fresh record per call, since any of them can change
     *  between frames; the unused arm/first-person groups are passed straight through from the
     *  compiled placement. */
    public static PerchPlacement placement() {
        PerchPlacement base = KriftoPerchPlacement.compiled();
        return new PerchPlacement(
                side(), height(), forward(),
                base.armXRot(), base.armYRot(), base.armZRot(),
                base.fpX(), base.fpY(), base.fpZ(),
                base.fpXRot(), base.fpYRot(), base.fpZRot(),
                modelScale(), base.fpScale());
    }

    @Override
    protected boolean canTune(Minecraft mc) {
        return PerchClient.perchedEntityIdFor(mc.player.getId()) != -1;
    }

    @Override
    protected void resetValues() {
        this.side = null;
        this.height = null;
        this.forward = null;
        this.modelScale = null;
    }

    // Each read returns the compiled constant unless the tuner is active, in which case the (lazily
    // seeded from that same constant) override wins — seeding from the real constant is what keeps a
    // keypress that lands before the first read from snapping the value to a placeholder.
    public static float side() {
        if (!INSTANCE.active) return KriftoPerchPlacement.compiled().side();
        if (INSTANCE.side == null) INSTANCE.side = KriftoPerchPlacement.compiled().side();
        return INSTANCE.side;
    }

    public static float height() {
        if (!INSTANCE.active) return KriftoPerchPlacement.compiled().height();
        if (INSTANCE.height == null) INSTANCE.height = KriftoPerchPlacement.compiled().height();
        return INSTANCE.height;
    }

    public static float forward() {
        if (!INSTANCE.active) return KriftoPerchPlacement.compiled().forward();
        if (INSTANCE.forward == null) INSTANCE.forward = KriftoPerchPlacement.compiled().forward();
        return INSTANCE.forward;
    }

    public static float modelScale() {
        if (!INSTANCE.active) return KriftoPerchPlacement.compiled().modelScale();
        if (INSTANCE.modelScale == null) INSTANCE.modelScale = KriftoPerchPlacement.compiled().modelScale();
        return INSTANCE.modelScale;
    }

    @Override
    protected boolean handleKey(int key) {
        if (key == GLFW.GLFW_KEY_KP_5) {
            this.mode = this.mode == Mode.POS ? Mode.SCALE : Mode.POS;
            return true;
        }
        return this.mode == Mode.POS ? nudgePos(key) : nudgeScale(key);
    }

    /** Same axis layout and key order as {@code OwlPerchTuner}'s OWL mode, deliberately — these tools
     *  are used back to back and muscle memory should carry over. */
    private boolean nudgePos(int key) {
        return nudge(key,
                new KeyAxis(GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_8, POS_STEP, d -> this.forward = forward() + d),
                new KeyAxis(GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_6, POS_STEP, d -> this.side = side() + d),
                new KeyAxis(GLFW.GLFW_KEY_KP_3, GLFW.GLFW_KEY_KP_9, POS_STEP, d -> this.height = height() + d));
    }

    private boolean nudgeScale(int key) {
        return nudge(key,
                new KeyAxis(GLFW.GLFW_KEY_KP_3, GLFW.GLFW_KEY_KP_9, SCALE_STEP,
                        d -> this.modelScale = Math.max(0.05F, modelScale() + d)));
    }

    @Override
    protected Component status() {
        return switch (this.mode) {
            case POS -> Component.literal(String.format("[POS] side %.2f | height %.2f | forward %.2f",
                            side(), height(), forward()))
                    .withStyle(ChatFormatting.AQUA);
            case SCALE -> Component.literal(String.format("[SCALE] %.2f", modelScale()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        };
    }

    @Override
    protected void printValues(@NotNull LocalPlayer player) {
        player.sendSystemMessage(Component.literal("[kriftoperch] paste over the constants in KriftoPerchPlacement:")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(String.format(
                        "PERCH_SIDE = %.3fF;  PERCH_HEIGHT = %.3fF;  PERCH_FORWARD = %.3fF;",
                        side(), height(), forward()))
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal(String.format(
                        "MODEL_SCALE = %.3fF;", modelScale()))
                .withStyle(ChatFormatting.GREEN));
    }

    public static Component helpMessage(boolean enabled) {
        if (!enabled) {
            return Component.literal("[kriftoperch] tuner OFF").withStyle(ChatFormatting.GRAY);
        }
        return Component.literal("[kriftoperch] tuner ON — perch the Krifto, then NUMPAD: "
                        + "5 switches POS/SCALE. POS: 8/2 fwd/back, 4/6 left/right, 9/3 up/down. "
                        + "SCALE: 9/3 bigger/smaller. 0 prints, . resets")
                .withStyle(ChatFormatting.GOLD);
    }
}
