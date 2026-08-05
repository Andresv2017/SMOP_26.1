package net.darkblade.smop.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * A mount that reacts to SMOP's rider keybinds (attack, fear, open inventory, descend).
 *
 * <p><b>Design note.</b> On 1.20.1 the rider packet held a {@code switch} over the concrete mob
 * classes ({@code Hell_HippoEntity}, {@code NirasmosaurusEntity}), so the network layer had to
 * import every rideable mob and every new mount meant editing the packet. Routing through this
 * interface inverts that: the packet only knows "the vehicle handles its own actions", and a mount
 * opts in by implementing it. Unsupported actions are simply ignored by the implementer.
 */
public interface RiderControllable {

    /** Called server-side when the rider presses one of the mount keybinds. */
    void onRiderAction(ServerPlayer rider, RiderAction action);

    enum RiderAction {
        /** Hell Hippo: mounted charge attack (default {@code R}). */
        ATTACK,
        /** Hell Hippo: intimidation pulse applying {@code smop:fear} (default {@code G}). */
        FEAR,
        /** Hell Hippo: open the mount's chest inventory (default {@code V}). */
        OPEN_INVENTORY,
        /** Nirasmosaurus: begin diving while ridden (default {@code X} held). */
        DESCEND_START,
        /** Nirasmosaurus: stop diving. */
        DESCEND_STOP
    }
}
