package net.darkblade.smop.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * A mount that reacts to SMOP's rider keybinds (attack, fear, open inventory, descend).
 *
 * <p>Routing through an interface keeps the network layer from importing every rideable mob: the
 * packet only knows "the vehicle handles its own actions", and a mount opts in by implementing this.
 * Unsupported actions are simply ignored by the implementer.
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
